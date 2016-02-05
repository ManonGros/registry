package org.gbif.registry.search;

import org.gbif.api.model.common.paging.Pageable;
import org.gbif.api.model.common.paging.PagingRequest;
import org.gbif.api.model.common.paging.PagingResponse;
import org.gbif.api.model.registry.Comment;
import org.gbif.api.model.registry.Contact;
import org.gbif.api.model.registry.Dataset;
import org.gbif.api.model.registry.Endpoint;
import org.gbif.api.model.registry.Identifier;
import org.gbif.api.model.registry.Installation;
import org.gbif.api.model.registry.MachineTag;
import org.gbif.api.model.registry.Organization;
import org.gbif.api.model.registry.Tag;
import org.gbif.api.service.registry.DatasetService;
import org.gbif.api.service.registry.InstallationService;
import org.gbif.api.service.registry.NetworkEntityService;
import org.gbif.api.service.registry.OrganizationService;
import org.gbif.api.vocabulary.IdentifierType;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import com.google.common.base.Stopwatch;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A builder that will clear and build a new dataset index by paging over the given service.
 */
@Singleton
public class DatasetIndexBuilder {

  // controls how many results we request while paging over the WS
  private static final int WS_PAGE_SIZE = 100;
  private static final Logger LOG = LoggerFactory.getLogger(DatasetIndexBuilder.class);
  private final SolrClient solrClient;
  private final DatasetService datasetService;
  private final DatasetDocConverter docConverter;

  @Inject
  public DatasetIndexBuilder(@Named("Dataset") SolrClient solrClient, DatasetService datasetService,
    InstallationService installationService, OrganizationService organizationService) {
    this.solrClient = solrClient;
    this.datasetService = datasetService;
    // We can use a cache at startup
    docConverter =
      new DatasetDocConverter(new CachingNetworkEntityService<Organization>(organizationService),
        new CachingNetworkEntityService<Installation>(installationService), datasetService);
  }

  public void build() throws SolrServerException, IOException {
    LOG.info("Building a new Dataset index");
    Stopwatch stopwatch = Stopwatch.createStarted();
    pageAndIndex();
    solrClient.commit();
    solrClient.optimize();
    LOG.info("Finished building Dataset index in {} secs", stopwatch.elapsed(TimeUnit.SECONDS));
  }

  /**
   * Pages over all datasets and adds to SOLR.
   */
  private void pageAndIndex() throws IOException, SolrServerException {
    PagingRequest page = new PagingRequest(0, WS_PAGE_SIZE);
    PagingResponse<Dataset> response = null;
    do {
      LOG.debug("Requesting {} datasets starting at offset {}", page.getLimit(), page.getOffset());
      response = datasetService.list(page);
      // Batching updates to SOLR proves quicker with batches of 100 - 1000 showing similar performance
      List<SolrInputDocument> batch = Lists.newArrayList();
      for (Dataset ds : response.getResults()) {
        batch.add(docConverter.build(ds));
      }
      if (!batch.isEmpty()) {
        solrClient.add(batch);
        solrClient.commit(); // to allow eager users (or impatient developers) to see search data on startup quickly
      }
      page.nextPage();

    } while (!response.isEndOfRecords());
  }

  /**
   * A lightweight cache to help improve performance of the builder.
   *
   * @param <T> The type of entity being wrapped
   */
  private static class CachingNetworkEntityService<T> implements NetworkEntityService<T> {

    private final NetworkEntityService<T> service;
    private final LoadingCache<UUID, T> cache = CacheBuilder.newBuilder()
      .maximumSize(1000)
      .expireAfterWrite(10, TimeUnit.MINUTES)
      .build(
        new CacheLoader<UUID, T>() {

          @Override
          public T load(UUID key) throws Exception {
            return service.get(key);
          }
        });

    public CachingNetworkEntityService(NetworkEntityService<T> service) {
      this.service = service;
    }

    @Override
    public UUID create(T entity) {
      throw new IllegalStateException("Method not supported in caching service");
    }

    @Override
    public void delete(UUID key) {
      throw new IllegalStateException("Method not supported in caching service");
    }

    @Override
    public T get(UUID key) {
      try {
        return cache.get(key);
      } catch (ExecutionException e) {
        return null;
      }
    }

    @Override
    public Map<UUID, String> getTitles(Collection<UUID> collection) {
      throw new IllegalStateException("Method not supported in caching service");
    }

    @Override
    public PagingResponse<T> list(Pageable page) {
      throw new IllegalStateException("Method not supported in caching service");
    }

    @Override
    public void update(T entity) {
      throw new IllegalStateException("Method not supported in caching service");
    }

    @Override
    public PagingResponse<T> search(String query, Pageable page) {
      throw new IllegalStateException("Method not supported in caching service");
    }

    @Override
    public int addComment(@NotNull UUID targetEntityKey, @NotNull Comment comment) {
      throw new IllegalStateException("Method not supported in caching service");
    }

    @Override
    public void deleteComment(@NotNull UUID targetEntityKey, int commentKey) {
      throw new IllegalStateException("Method not supported in caching service");
    }

    @Override
    public List<Comment> listComments(@NotNull UUID targetEntityKey) {
      throw new IllegalStateException("Method not supported in caching service");
    }

    @Override
    public int addContact(@NotNull UUID targetEntityKey, @NotNull Contact contact) {
      throw new IllegalStateException("Method not supported in caching service");
    }

    @Override
    public void updateContact(@NotNull UUID targetEntityKey, @NotNull Contact contact) {
      throw new IllegalStateException("Method not supported in caching service");
    }

    @Override
    public void deleteContact(@NotNull UUID targetEntityKey, int contactKey) {
      throw new IllegalStateException("Method not supported in caching service");
    }

    @Override
    public List<Contact> listContacts(@NotNull UUID targetEntityKey) {
      throw new IllegalStateException("Method not supported in caching service");
    }

    @Override
    public int addEndpoint(@NotNull UUID targetEntityKey, @NotNull Endpoint endpoint) {
      throw new IllegalStateException("Method not supported in caching service");
    }

    @Override
    public void deleteEndpoint(@NotNull UUID targetEntityKey, int endpointKey) {
      throw new IllegalStateException("Method not supported in caching service");
    }

    @Override
    public List<Endpoint> listEndpoints(@NotNull UUID targetEntityKey) {
      throw new IllegalStateException("Method not supported in caching service");
    }

    @Override
    public int addIdentifier(@NotNull UUID targetEntityKey, @NotNull Identifier identifier) {
      throw new IllegalStateException("Method not supported in caching service");
    }

    @Override
    public void deleteIdentifier(@NotNull UUID targetEntityKey, int identifierKey) {
      throw new IllegalStateException("Method not supported in caching service");
    }

    @Override
    public List<Identifier> listIdentifiers(@NotNull UUID targetEntityKey) {
      throw new IllegalStateException("Method not supported in caching service");
    }

    @Override
    public int addMachineTag(@NotNull UUID targetEntityKey, @NotNull MachineTag machineTag) {
      throw new IllegalStateException("Method not supported in caching service");
    }

    @Override
    public int addMachineTag(
      @NotNull UUID targetEntityKey, @NotNull String namespace, @NotNull String name, @NotNull String value) {
      throw new IllegalStateException("Method not supported in caching service");
    }

    @Override
    public void deleteMachineTag(@NotNull UUID targetEntityKey, int machineTagKey) {
      throw new IllegalStateException("Method not supported in caching service");
    }

    @Override
    public void deleteMachineTags(@NotNull UUID targetEntityKey, @NotNull String namespace) {
      throw new IllegalStateException("Method not supported in caching service");
    }

    @Override
    public void deleteMachineTags(
      @NotNull UUID targetEntityKey, @NotNull String namespace, @NotNull String name) {
      throw new IllegalStateException("Method not supported in caching service");
    }

    @Override
    public List<MachineTag> listMachineTags(@NotNull UUID targetEntityKey) {
      throw new IllegalStateException("Method not supported in caching service");
    }

    @Override
    public int addTag(@NotNull UUID targetEntityKey, @NotNull String value) {
      throw new IllegalStateException("Method not supported in caching service");
    }

    @Override
    public int addTag(@NotNull UUID targetEntityKey, @NotNull Tag tag) {
      throw new IllegalStateException("Method not supported in caching service");
    }

    @Override
    public void deleteTag(@NotNull UUID taggedEntityKey, int tagKey) {
      throw new IllegalStateException("Method not supported in caching service");
    }

    @Override
    public List<Tag> listTags(@NotNull UUID taggedEntityKey, @Nullable String owner) {
      throw new IllegalStateException("Method not supported in caching service");
    }

    @Override
    public PagingResponse<T> listByIdentifier(IdentifierType type, String identifier, Pageable page) {
      throw new IllegalStateException("Method not supported in caching service");
    }

    @Override
    public PagingResponse<T> listByIdentifier(String identifier, Pageable page) {
      throw new IllegalStateException("Method not supported in caching service");
    }
  }
}
