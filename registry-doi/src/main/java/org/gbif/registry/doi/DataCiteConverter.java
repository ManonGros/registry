package org.gbif.registry.doi;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.gbif.api.model.common.DOI;
import org.gbif.api.model.common.GbifUser;
import org.gbif.api.model.occurrence.Download;
import org.gbif.api.model.occurrence.DownloadFormat;
import org.gbif.api.model.occurrence.PredicateDownloadRequest;
import org.gbif.api.model.occurrence.SqlDownloadRequest;
import org.gbif.api.model.registry.Contact;
import org.gbif.api.model.registry.Dataset;
import org.gbif.api.model.registry.DatasetOccurrenceDownloadUsage;
import org.gbif.api.model.registry.Organization;
import org.gbif.api.model.registry.eml.KeywordCollection;
import org.gbif.api.model.registry.eml.geospatial.GeospatialCoverage;
import org.gbif.api.vocabulary.ContactType;
import org.gbif.api.vocabulary.IdentifierType;
import org.gbif.api.vocabulary.Language;
import org.gbif.api.vocabulary.License;
import org.gbif.doi.metadata.datacite.Affiliation;
import org.gbif.doi.metadata.datacite.Box;
import org.gbif.doi.metadata.datacite.ContributorType;
import org.gbif.doi.metadata.datacite.DataCiteMetadata;
import org.gbif.doi.metadata.datacite.DataCiteMetadata.AlternateIdentifiers;
import org.gbif.doi.metadata.datacite.DataCiteMetadata.AlternateIdentifiers.AlternateIdentifier;
import org.gbif.doi.metadata.datacite.DataCiteMetadata.Contributors;
import org.gbif.doi.metadata.datacite.DataCiteMetadata.Contributors.Contributor;
import org.gbif.doi.metadata.datacite.DataCiteMetadata.Creators;
import org.gbif.doi.metadata.datacite.DataCiteMetadata.Creators.Creator;
import org.gbif.doi.metadata.datacite.DataCiteMetadata.Creators.Creator.CreatorName;
import org.gbif.doi.metadata.datacite.DataCiteMetadata.Dates;
import org.gbif.doi.metadata.datacite.DataCiteMetadata.Descriptions;
import org.gbif.doi.metadata.datacite.DataCiteMetadata.Descriptions.Description;
import org.gbif.doi.metadata.datacite.DataCiteMetadata.GeoLocations;
import org.gbif.doi.metadata.datacite.DataCiteMetadata.GeoLocations.GeoLocation;
import org.gbif.doi.metadata.datacite.DataCiteMetadata.Identifier;
import org.gbif.doi.metadata.datacite.DataCiteMetadata.Publisher;
import org.gbif.doi.metadata.datacite.DataCiteMetadata.RelatedIdentifiers;
import org.gbif.doi.metadata.datacite.DataCiteMetadata.RelatedIdentifiers.RelatedIdentifier;
import org.gbif.doi.metadata.datacite.DataCiteMetadata.RightsList;
import org.gbif.doi.metadata.datacite.DataCiteMetadata.RightsList.Rights;
import org.gbif.doi.metadata.datacite.DataCiteMetadata.Subjects;
import org.gbif.doi.metadata.datacite.DataCiteMetadata.Subjects.Subject;
import org.gbif.doi.metadata.datacite.DataCiteMetadata.Titles;
import org.gbif.doi.metadata.datacite.DataCiteMetadata.Titles.Title;
import org.gbif.doi.metadata.datacite.DateType;
import org.gbif.doi.metadata.datacite.DescriptionType;
import org.gbif.doi.metadata.datacite.NameIdentifier;
import org.gbif.doi.metadata.datacite.NameType;
import org.gbif.doi.metadata.datacite.RelatedIdentifierType;
import org.gbif.doi.metadata.datacite.RelationType;
import org.gbif.doi.metadata.datacite.ResourceType;
import org.gbif.doi.service.InvalidMetadataException;
import org.gbif.doi.service.datacite.DataCiteValidator;
import org.gbif.occurrence.query.HumanPredicateBuilder;
import org.gbif.occurrence.query.TitleLookup;
import org.gbif.registry.metadata.contact.ContactAdapter;

import javax.xml.bind.JAXBException;
import java.net.URI;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DataCiteConverter {

  public static final String ORCID_NAME_IDENTIFIER_SCHEME = "ORCID";
  public static final String RESEARCHERID_NAME_IDENTIFIER_SCHEME = "ResearcherID";

  private static Map<ContactType, ContributorType> REGISTRY_DATACITE_ROLE_MAPPING =
    ImmutableMap.<ContactType, ContributorType>builder().
      put(ContactType.EDITOR, ContributorType.EDITOR).
      put(ContactType.PUBLISHER, ContributorType.EDITOR).
      put(ContactType.CONTENT_PROVIDER, ContributorType.DATA_COLLECTOR).
      put(ContactType.CUSTODIAN_STEWARD, ContributorType.DATA_MANAGER).
      put(ContactType.CURATOR, ContributorType.DATA_CURATOR).
      put(ContactType.METADATA_AUTHOR, ContributorType.DATA_CURATOR).
      put(ContactType.DISTRIBUTOR, ContributorType.DISTRIBUTOR).
      put(ContactType.OWNER, ContributorType.RIGHTS_HOLDER).
      put(ContactType.POINT_OF_CONTACT, ContributorType.CONTACT_PERSON).
      put(ContactType.PRINCIPAL_INVESTIGATOR, ContributorType.PROJECT_LEADER).
      put(ContactType.ORIGINATOR, ContributorType.DATA_COLLECTOR).
      put(ContactType.PROCESSOR, ContributorType.PRODUCER).
      put(ContactType.PROGRAMMER, ContributorType.PRODUCER).
      build();

  // Patterns must return 2 groups: scheme and id
  public static final Map<Pattern, String> SUPPORTED_SCHEMES = ImmutableMap.of(
    Pattern.compile("^(http[s]?:\\/\\/orcid.org\\/)([\\d\\-]+$)"), ORCID_NAME_IDENTIFIER_SCHEME);

  private static final String DOWNLOAD_TITLE = "Occurrence Download";
  protected static final String GBIF_PUBLISHER = "The Global Biodiversity Information Facility";
  protected static final License DEFAULT_DOWNLOAD_LICENSE = License.CC_BY_NC_4_0;
  private static final String LICENSE_INFO = "Data from some individual datasets included in this download may be licensed under less restrictive terms.";
  protected static final String ENGLISH = Language.ENGLISH.getIso3LetterCode();
  private static final String DWCA_FORMAT = "Darwin Core Archive";

  private DataCiteConverter() {
    //DO nothing
  }

  /**
   * Convert a dataset and publisher object into a datacite metadata instance.
   * DataCite requires at least the following properties:
   * <ul>
   * <li>Identifier</li>
   * <li>Creator</li>
   * <li>Title</li>
   * <li>Publisher</li>
   * <li>PublicationYear</li>
   * <li>ResourceType</li>
   * </ul>
   * As the publicationYear property is often not available from newly created datasets, this converter uses the
   * current year as the default in case no created timestamp or pubdate exists.
   */
  // TODO: 01/10/2019 method is too complex
  public static DataCiteMetadata convert(Dataset d, Organization publisher) {
    // always add required metadata
    DataCiteMetadata.Builder<Void> b = DataCiteMetadata.builder()
      // build titles
      .withTitles(
        Titles.builder()
          .addTitle(
            Title.builder()
              .withValue(d.getTitle())
              .build())
          .build());

    // build publisher
    b.withPublisher(
      Publisher.builder()
        .withValue(publisher.getTitle())
        .build());

    // build publication year
    // default to this year, e.g. when creating new datasets. This field is required!
    b.withPublicationYear(getYear(new Date()));

    // build resource type
    b.withResourceType(
      DataCiteMetadata.ResourceType.builder()
        .withResourceTypeGeneral(ResourceType.DATASET)
        .withValue(d.getType().name())
        .build());

    // build related identifiers
    // empty list of RelatedIdentifiers is expected but callers
    b.withRelatedIdentifiers().end();

    final Dates.Builder<Void> datesBuilder = Dates.builder();
    if (d.getCreated() != null) {
      // build dates
      b.withDates(
        datesBuilder
          .addDate(
            Dates.Date.builder()
              .withDateType(DateType.CREATED)
              .withValue(fdate(d.getCreated()))
              .build())
          .build());
    }

    if (d.getModified() != null) {
      // build publication year
      b.withPublicationYear(getYear(d.getModified()));

      // build dates
      b.withDates(
        datesBuilder
          .addDate(
            Dates.Date.builder()
              .withDateType(DateType.UPDATED)
              .withValue(fdate(d.getModified()))
              .build())
          .build());
    }

    // handle contacts
    boolean creatorFound = false;
    if (d.getContacts() != null && !d.getContacts().isEmpty()) {
      ContactAdapter contactAdapter = new ContactAdapter(d.getContacts());

      // handle Creators
      List<Contact> resourceCreators = contactAdapter.getCreators();
      final Creators.Builder<Void> creatorsBuilder = Creators.builder();
      for (Contact resourceCreator : resourceCreators) {
        final Optional<Creator> creatorWrapper = toDataCiteCreator(resourceCreator);
        creatorFound = creatorFound ? creatorFound : creatorWrapper.isPresent();
        creatorWrapper
          // build creators
          .ifPresent(creator -> b.withCreators(
            creatorsBuilder
              .addCreator(creator)
              .build()
            )
          );
      }

      // handle Contributors
      List<Contact> contributors = Lists.newArrayList(d.getContacts());
      contributors.removeAll(resourceCreators);

      final Contributors.Builder<Void> contributorsBuilder = Contributors.builder();
      if (!contributors.isEmpty()) {
        for (Contact contact : contributors) {
          toDataCiteContributor(contact)
            // build contributors
            .ifPresent(contributor -> b.withContributors(
              contributorsBuilder
                .addContributor(contributor)
                .build()
              )
            );
        }
      }
    }

    if (!creatorFound) {
      // creator is mandatory, build a default one
      b.withCreators(
        Creators.builder()
          .addCreator(getDefaultGBIFDataCiteCreator(d.getCreatedBy()))
          .build());
    }

    if (d.getPubDate() != null) {
      // build publication year
      // use pub date for publication year if it exists
      b.withPublicationYear(getYear(d.getPubDate()));
    }

    if (d.getDoi() != null) {
      // build identifiers
      b.withIdentifier(
        Identifier.builder()
          .withIdentifierType(IdentifierType.DOI.name())
          .withValue(d.getDoi().getDoiName())
          .build());

      if (d.getKey() != null) {
        // build alternate identifiers
        b.withAlternateIdentifiers(
          AlternateIdentifiers.builder()
            .addAlternateIdentifier(
              AlternateIdentifier.builder()
                .withAlternateIdentifierType("UUID")
                .withValue(d.getKey().toString())
                .build())
            .build());
      }
    } else if (d.getKey() != null) {
      // build identifier
      b.withIdentifier(
        Identifier.builder()
          .withIdentifierType(IdentifierType.UUID.name())
          .withValue(d.getKey().toString())
          .build());
    }

    if (!Strings.isNullOrEmpty(d.getDescription())) {
      // build description
      b.withDescriptions(
        Descriptions.builder()
          .addDescription(
            Description.builder()
              .withContent(d.getDescription())
              .withDescriptionType(DescriptionType.ABSTRACT)
              .build())
          .build());
    }

    if (d.getDataLanguage() != null) {
      // build language
      b.withLanguage(d.getDataLanguage().getIso3LetterCode());
    }

    // build rights list
    if (d.getLicense() != null && d.getLicense().isConcrete()) {
      b.withRightsList(
        RightsList.builder()
          .addRights(
            Rights.builder()
              .withRightsURI(d.getLicense().getLicenseUrl())
              .withValue(d.getLicense().getLicenseTitle())
              .build())
          .build());
    } else {
      // this is still require for metadata only resource
      if (!Strings.isNullOrEmpty(d.getRights())) {
        b.withRightsList(
          RightsList.builder()
            .addRights(
              Rights.builder()
                .withValue(d.getRights())
                .build())
            .build());
      }
    }

    // build subjects
    final Subjects.Builder<Void> subjectsBuilder = Subjects.builder();
    for (KeywordCollection kCol : d.getKeywordCollections()) {
      for (String k : kCol.getKeywords()) {
        if (!Strings.isNullOrEmpty(k)) {
          Subject s = Subject.builder().withValue(k).build();
          if (!Strings.isNullOrEmpty(kCol.getThesaurus())) {
            s.setSubjectScheme(kCol.getThesaurus());
          }
          b.withSubjects(subjectsBuilder
            .addSubject(s)
            .build());
        }
      }
    }

    final GeoLocations.Builder<Void> geoLocationsBuilder = GeoLocations.builder();
    for (GeospatialCoverage gc : d.getGeographicCoverages()) {
      if (gc.getBoundingBox() != null) {
        // build geo locations
        b.withGeoLocations(geoLocationsBuilder
          .addGeoLocation(GeoLocation.builder()
            .addGeoLocationPlace(gc.getDescription())
            .addGeoLocationBox(Box.builder()
              .withEastBoundLongitude((float) gc.getBoundingBox().getMaxLongitude())
              .withNorthBoundLatitude((float) gc.getBoundingBox().getMaxLatitude())
              .withSouthBoundLatitude((float) gc.getBoundingBox().getMinLatitude())
              .withWestBoundLongitude((float) gc.getBoundingBox().getMinLongitude())
              .build())
            .build())
          .build());
      }
    }
    return b.build();
  }

  private static DataCiteMetadata truncateDescriptionDCM(DOI doi, String xml, URI target) throws InvalidMetadataException {
    try {
      DataCiteMetadata dm = DataCiteValidator.fromXml(xml);
      String description = Joiner.on("\n").join(dm.getDescriptions().getDescription().get(0).getContent());

      Descriptions.builder()
        .addDescription(Descriptions.Description.builder()
          .withDescriptionType(DescriptionType.ABSTRACT)
          .withLang(ENGLISH)
          .addContent(
            StringUtils.substringBefore(description, "constituent datasets:") +
              String.format("constituent datasets:\nPlease see %s for full list of all constituents.", target))
          .build())
        .build();
      return dm;
    } catch (JAXBException e) {
      throw new InvalidMetadataException("Failed to deserialize datacite xml for DOI " + doi, e);
    }
  }

  public static String truncateDescription(DOI doi, String xml, URI target) throws InvalidMetadataException {
    DataCiteMetadata dm = truncateDescriptionDCM(doi, xml, target);
    return DataCiteValidator.toXml(doi, dm);
  }

  /**
   * Removes all constituent relations and description entries from the metadata.
   */
  public static String truncateConstituents(DOI doi, String xml, URI target) throws InvalidMetadataException {
    DataCiteMetadata dm = truncateDescriptionDCM(doi, xml, target);
    // also remove constituent relations
    dm.setRelatedIdentifiers(null);
    return DataCiteValidator.toXml(doi, dm);
  }

  /**
   * Convert a download and its dataset usages into a datacite metadata instance.
   */
  public static DataCiteMetadata convert(Download d,
                                         GbifUser creator,
                                         List<DatasetOccurrenceDownloadUsage> usedDatasets,
                                         TitleLookup titleLookup) {
    Preconditions.checkNotNull(d.getDoi(), "Download DOI required to build valid DOI metadata");
    Preconditions.checkNotNull(d.getCreated(), "Download created date required to build valid DOI metadata");
    Preconditions.checkNotNull(creator, "Download creator required to build valid DOI metadata");
    Preconditions.checkNotNull(d.getRequest(), "Download request required to build valid DOI metadata");

    // always add required metadata
    DataCiteMetadata.Builder<Void> b = DataCiteMetadata.builder();

    // build identifier
    b.withIdentifier(Identifier.builder()
      .withIdentifierType(IdentifierType.DOI.name())
      .withValue(d.getDoi().getDoiName())
      .build());

    // build titles
    b.withTitles(Titles.builder()
      .withTitle(Title.builder()
        .withValue(DOWNLOAD_TITLE)
        .build())
      .build());

    // build subjects
    b.withSubjects(Subjects.builder()
      .addSubject(Subject.builder()
        .withValue("GBIF")
        .withLang(ENGLISH)
        .build())
      .addSubject(Subject.builder()
        .withValue("biodiversity")
        .withLang(ENGLISH)
        .build())
      .addSubject(Subject.builder()
        .withValue("species occurrences")
        .withLang(ENGLISH)
        .build())
      .build());

    // build creators
    b.withCreators(Creators.builder()
      .addCreator(Creator.builder()
        .withCreatorName(CreatorName.builder()
          .withNameType(NameType.ORGANIZATIONAL)
          .withValue(creator.getName())
          .build())
        .build())
      .build());

    // build publisher
    b.withPublisher(Publisher.builder()
      .withValue(GBIF_PUBLISHER)
      .build());

    // build publication year
    b.withPublicationYear(getYear(d.getCreated()));

    // build resource type
    b.withResourceType(DataCiteMetadata.ResourceType.builder()
      .withResourceTypeGeneral(ResourceType.DATASET)
      .build());

    // build alternate identifiers
    b.withAlternateIdentifiers(AlternateIdentifiers.builder()
      .addAlternateIdentifier(AlternateIdentifier.builder()
        .withAlternateIdentifierType("GBIF")
        .withValue(d.getKey())
        .build())
      .build());

    // build dates
    b.withDates(Dates.builder()
      .addDate(Dates.Date.builder()
        .withDateType(DateType.CREATED)
        .withValue(fdate(d.getCreated()))
        .build())
      .addDate(Dates.Date.builder()
        .withDateType(DateType.UPDATED)
        .withValue(fdate(d.getModified()))
        .build())
      .build());

    // build formats & sizes
    b.withFormats(
      DataCiteMetadata.Formats.builder()
        .withFormat(DWCA_FORMAT)
        .build());
    b.withSizes(
      DataCiteMetadata.Sizes.builder()
        .addSize(Long.toString(d.getSize()))
        .build());

    License downloadLicense = d.getLicense() != null && d.getLicense().isConcrete() ?
      d.getLicense() : DEFAULT_DOWNLOAD_LICENSE;

    // build rights list
    b.withRightsList(
      RightsList.builder()
        .addRights(
          Rights.builder()
            .withRightsURI(downloadLicense.getLicenseUrl())
            .withValue(downloadLicense.getLicenseTitle())
            .build())
        .build());

    // build descriptions
    b.withDescriptions(
      Descriptions.builder()
        .addDescription(
          Description.builder()
            .withDescriptionType(DescriptionType.ABSTRACT)
            .withLang(ENGLISH)
            .addContent(String.format("A dataset containing %s species occurrences available in GBIF matching the query:\n%s\n\n",
              d.getTotalRecords(), getFilterQuery(d, titleLookup)))
            .addContent(String.format("The dataset includes %s records from %s constituent datasets:\n",
              d.getTotalRecords(), d.getNumberDatasets()))
            .addContent(getDescriptionDatasetOccurrenceDownloadUsage(usedDatasets))
            .build())
        .build());

    // build related identifiers
    b.withRelatedIdentifiers(getRelatedIdentifiersDatasetOccurrenceDownloadUsage(usedDatasets));

    return b.build();
  }

  private static String getDescriptionDatasetOccurrenceDownloadUsage(
    List<DatasetOccurrenceDownloadUsage> usedDatasets) {
    final StringBuilder result = new StringBuilder();

    if (!usedDatasets.isEmpty()) {
      for (DatasetOccurrenceDownloadUsage du : usedDatasets) {
        if (!Strings.isNullOrEmpty(du.getDatasetTitle())) {
          result.append(" ")
            .append(du.getNumberRecords())
            .append(" records from ")
            .append(du.getDatasetTitle())
            .append(".\n");
        }
      }
      result.append("\n");
      result.append(LICENSE_INFO);
    }

    return result.toString();
  }

  private static RelatedIdentifiers getRelatedIdentifiersDatasetOccurrenceDownloadUsage(
    List<DatasetOccurrenceDownloadUsage> usedDatasets) {
    final RelatedIdentifiers.Builder relatedIdentifiersBuilder = RelatedIdentifiers.builder();
    if (!usedDatasets.isEmpty()) {
      for (DatasetOccurrenceDownloadUsage du : usedDatasets) {
        if (du.getDatasetDOI() != null) {
          relatedIdentifiersBuilder.addRelatedIdentifier(
            RelatedIdentifier.builder()
              .withRelationType(RelationType.REFERENCES)
              .withValue(du.getDatasetDOI().getDoiName())
              .withRelatedIdentifierType(RelatedIdentifierType.DOI)
              .build()
          );
        }
      }
    }

    return relatedIdentifiersBuilder.build();
  }

  /**
   * Transforms a Contact into a Datacite Creator.
   *
   * @return Creator instance or null if it is not possible to build one
   */
  private static Optional<Creator> toDataCiteCreator(Contact contact) {
    final String creatorNameValue = ContactAdapter.formatContactName(contact);

    // CreatorName is mandatory
    if (Strings.isNullOrEmpty(creatorNameValue)) {
      return Optional.empty();
    }

    final Creator.Builder<Void> creatorBuilder = Creator.builder();

    creatorBuilder
      .withCreatorName(
        CreatorName.builder()
          .withValue(creatorNameValue)
          .build());

    // affiliation is optional
    if (!Strings.isNullOrEmpty(contact.getOrganization())) {
      creatorBuilder
        .withAffiliation(
          Affiliation.builder()
            .withValue(contact.getOrganization())
            .build());
    }

    NameIdentifier nId = userIdToNameIdentifier(contact.getUserId());
    if (nId != null) {
      creatorBuilder.withNameIdentifier(nId);
    }

    return Optional.of(creatorBuilder.build());
  }

  /**
   * Transforms a Contact into a DataCite Creator.
   */
  private static Creator getDefaultGBIFDataCiteCreator(String fullname) {
    return Creator.builder()
      .withCreatorName(
        CreatorName.builder()
          .withValue(fullname)
          .build())
      .withNameIdentifier(
        NameIdentifier.builder()
          .withValue(fullname)
          .withSchemeURI("gbif.org")
          .withNameIdentifierScheme("GBIF")
          .build())
      .build();
  }

  /**
   * Transforms a Contact into a Datacite Contributor.
   *
   * @return Contributor instance or null if it is not possible to build one
   */
  private static Optional<Contributor> toDataCiteContributor(Contact contact) {
    final String nameValue = ContactAdapter.formatContactName(contact);

    // CreatorName is mandatory
    if (Strings.isNullOrEmpty(nameValue)) {
      return Optional.empty();
    }

    final Contributor.Builder<Void> contributorBuilder = Contributor.builder();
    contributorBuilder
      .withContributorName(
        Contributor.ContributorName.builder()
          .withValue(nameValue)
          .build())
      .withContributorType(
        REGISTRY_DATACITE_ROLE_MAPPING.getOrDefault(contact.getType(), ContributorType.RELATED_PERSON)
      );

    // affiliation is optional
    if (!Strings.isNullOrEmpty(contact.getOrganization())) {
      contributorBuilder
        .withAffiliation(
          Affiliation.builder()
            .withValue(contact.getOrganization())
            .build());
    }

    NameIdentifier nId = userIdToNameIdentifier(contact.getUserId());
    if (nId != null) {
      contributorBuilder.withNameIdentifier(nId);
    }

    return Optional.of(contributorBuilder.build());
  }

  /**
   * Get the first userId from the list and
   * transforms an userId in the form of https://orcid.org/0000-0000-0000-00001 into a DataCite NameIdentifier object.
   *
   * @return a NameIdentifier instance or null if the object can not be built (e.g. unsupported scheme)
   */
  public static NameIdentifier userIdToNameIdentifier(List<String> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return null;
    }

    for (String userId : userIds) {
      if (Strings.isNullOrEmpty(userId)) {
        // try the next one
        continue;
      }

      for (Map.Entry<Pattern, String> scheme : SUPPORTED_SCHEMES.entrySet()) {
        Matcher matcher = scheme.getKey().matcher(userId);
        if (matcher.matches()) {
          // group 0 = the entire string
          // we take the first we can support
          return NameIdentifier.builder()
            .withSchemeURI(matcher.group(1))
            .withValue(matcher.group(2))
            .withNameIdentifierScheme(scheme.getValue())
            .build();
        }
      }
    }
    return null;
  }

  private static String fdate(Date date) {
    return DateFormatUtils.ISO_DATE_FORMAT.format(date);
  }

  @VisibleForTesting
  protected static String getYear(Date date) {
    if (date == null) {
      return null;
    }
    Calendar cal = new GregorianCalendar();
    cal.setTime(date);
    return String.valueOf(cal.get(Calendar.YEAR));
  }

  /**
   * Tries to get the human readable version of the download query, if fails returns the raw query.
   */
  private static String getFilterQuery(Download d, TitleLookup titleLookup) {
    try {
      return d.getRequest().getFormat().equals(DownloadFormat.SQL) ? ((SqlDownloadRequest) d.getRequest()).getSql()
        : new HumanPredicateBuilder(titleLookup).humanFilterString(((PredicateDownloadRequest) d.getRequest()).getPredicate());
    } catch (Exception e) {
      return "(Query is too complex. Can be viewed on the landing page)";
    }
  }
}
