package org.gbif.registry.collections.sync.notification;

import org.gbif.api.model.collections.Collection;
import org.gbif.api.model.collections.CollectionEntity;
import org.gbif.api.model.collections.Institution;
import org.gbif.api.model.collections.Person;
import org.gbif.registry.collections.sync.ih.IHInstitution;
import org.gbif.registry.collections.sync.ih.IHStaff;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static org.gbif.registry.collections.sync.SyncConfig.NotificationConfig;

/** Factory to create {@link Issue}. */
public class IssueFactory {

  private static final String IH_OUTDATED_TITLE = "The IH %s with IRN %s is outdated";
  private static final String ENTITY_CONFLICT_TITLE =
      "IH institution with IRN %s matches with multiple GrSciColl entities";
  private static final String STAFF_CONFLICT_TITLE =
      "IH staff with IRN %s matches with multiple GrSciColl person";
  private static final String NEW_LINE = "\n";
  private static final String CODE_SEPARATOR = "```";
  private static final List<String> DEFAULT_LABELS =
      Collections.singletonList("GrSciColl-IH conflict");

  private static final UnaryOperator<String> PORTAL_URL_NORMALIZER =
      url -> {
        if (url != null && url.endsWith("/")) {
          return url.substring(0, url.length() - 1);
        }
        return url;
      };

  private final NotificationConfig config;
  private final String ihInstitutionLink;
  private final String ihStaffLink;
  private final String registryInstitutionLink;
  private final String registryCollectionLink;
  private final String registryPersonLink;

  private IssueFactory(NotificationConfig config) {
    this.config = config;
    this.ihInstitutionLink =
        PORTAL_URL_NORMALIZER.apply(config.getIhPortalUrl()) + "/ih/herbarium-details/?irn=%s";
    this.ihStaffLink =
        PORTAL_URL_NORMALIZER.apply(config.getIhPortalUrl()) + "/ih/person-details/?irn=%s";
    this.registryInstitutionLink =
        PORTAL_URL_NORMALIZER.apply(config.getRegistryPortalUrl()) + "/institution/%s";
    this.registryCollectionLink =
        PORTAL_URL_NORMALIZER.apply(config.getRegistryPortalUrl()) + "/collection/%s";
    this.registryPersonLink =
        PORTAL_URL_NORMALIZER.apply(config.getRegistryPortalUrl()) + "/person/%s";
  }

  public static IssueFactory fromConfig(NotificationConfig config) {
    return new IssueFactory(config);
  }

  public static IssueFactory fromDefaults() {
    NotificationConfig config = new NotificationConfig();
    config.setGhIssuesAssignees(Collections.emptyList());
    return new IssueFactory(config);
  }

  public Issue createOutdatedIHStaffIssue(Person grSciCollPerson, IHStaff ihStaff) {
    return createOutdatedIHEntityIssue(
        grSciCollPerson, ihStaff.getIrn(), ihStaff.toString(), EntityType.IH_STAFF);
  }

  public Issue createOutdatedIHInstitutionIssue(
      CollectionEntity grSciCollEntity, IHInstitution ihInstitution) {
    return createOutdatedIHEntityIssue(
        grSciCollEntity,
        ihInstitution.getIrn(),
        ihInstitution.toString(),
        EntityType.IH_INSTITUTION);
  }

  private Issue createOutdatedIHEntityIssue(
      CollectionEntity grSciCollEntity,
      String irn,
      String ihEntityAsString,
      EntityType entityType) {

    // create body
    StringBuilder body = new StringBuilder();
    body.append("The IH ")
        .append(createLink(irn, entityType))
        .append(":")
        .append(formatEntity(ihEntityAsString))
        .append("has a more up-to-date ")
        .append(
            createLink(grSciCollEntity.getKey().toString(), getRegistryEntityType(grSciCollEntity)))
        .append(" in GrSciColl:")
        .append(formatEntity(grSciCollEntity.toString()))
        .append(
            "Please check which one should remain and sync them in both systems. Remember to sync the associated staff too.");

    return Issue.builder()
        .title(String.format(IH_OUTDATED_TITLE, entityType, irn))
        .body(body.toString())
        .assignees(config.getGhIssuesAssignees())
        .labels(DEFAULT_LABELS)
        .build();
  }

  public Issue createConflict(List<CollectionEntity> entities, IHInstitution ihInstitution) {
    // create body
    StringBuilder body = new StringBuilder();
    body.append("The IH ")
        .append(createLink(ihInstitution.getIrn(), EntityType.IH_INSTITUTION))
        .append(":")
        .append(formatEntity(ihInstitution.toString()))
        .append("have multiple candidates in GrSciColl: " + NEW_LINE);
    entities.forEach(
        e -> {
          body.append(createLink(e.getKey().toString(), getRegistryEntityType(e)));
          body.append(formatEntity(e.toString()));
        });
    body.append(
        "A IH institution should be associated to only one GrSciColl entity. Please resolve the conflict.");

    return Issue.builder()
        .title(String.format(ENTITY_CONFLICT_TITLE, ihInstitution.getIrn()))
        .body(body.toString())
        .assignees(config.getGhIssuesAssignees())
        .labels(DEFAULT_LABELS)
        .build();
  }

  public Issue createMultipleStaffIssue(Set<Person> persons, IHStaff ihStaff) {
    // create body
    StringBuilder body = new StringBuilder();
    body.append("The IH staff: ")
        .append(formatEntity(ihStaff.toString()))
        .append("is associated to all the following GrSciColl persons: ")
        .append(NEW_LINE);
    persons.forEach(p -> body.append(formatEntity(p.toString())));
    body.append(
        "A IH staff should be associated to only one GrSciColl person. Please resolve the conflict.");

    return Issue.builder()
        .title(String.format(STAFF_CONFLICT_TITLE, ihStaff.getIrn()))
        .body(body.toString())
        .assignees(config.getGhIssuesAssignees())
        .labels(DEFAULT_LABELS)
        .build();
  }

  private String formatEntity(String entity) {
    return NEW_LINE + CODE_SEPARATOR + NEW_LINE + entity + NEW_LINE + CODE_SEPARATOR + NEW_LINE;
  }

  private String createLink(String id, EntityType entityType) {
    String linkTemplate = null;
    if (entityType == EntityType.IH_INSTITUTION) {
      linkTemplate = ihInstitutionLink;
    } else if (entityType == EntityType.IH_STAFF) {
      linkTemplate = ihStaffLink;
    } else if (entityType == EntityType.INSTITUTION) {
      linkTemplate = registryInstitutionLink;
    } else if (entityType == EntityType.COLLECTION) {
      linkTemplate = registryCollectionLink;
    } else {
      linkTemplate = registryPersonLink;
    }

    URI uri = URI.create(String.format(linkTemplate, id));

    return "[" + entityType.name().toLowerCase() + "](" + uri.toString() + ")";
  }

  private EntityType getRegistryEntityType(CollectionEntity entity) {
    if (entity instanceof Institution) {
      return EntityType.INSTITUTION;
    } else if (entity instanceof Collection) {
      return EntityType.COLLECTION;
    } else {
      return EntityType.PERSON;
    }
  }

  private enum EntityType {
    IH_INSTITUTION,
    IH_STAFF,
    INSTITUTION,
    COLLECTION,
    PERSON;
  }
}