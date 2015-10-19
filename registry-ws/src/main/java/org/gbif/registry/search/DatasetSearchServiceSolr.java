package org.gbif.registry.search;

import org.gbif.api.model.registry.search.DatasetSearchParameter;
import org.gbif.api.model.registry.search.DatasetSearchRequest;
import org.gbif.api.model.registry.search.DatasetSearchResult;
import org.gbif.api.model.registry.search.DatasetSuggestRequest;
import org.gbif.api.model.registry.search.DatasetSuggestResult;
import org.gbif.api.service.registry.DatasetSearchService;
import org.gbif.common.search.service.SolrSearchSuggestService;

import java.util.Map;

import com.google.common.collect.ImmutableSortedMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;

/**
 * Dataset search implementation using the provided SOLR instance.
 */
public class DatasetSearchServiceSolr
  extends
  SolrSearchSuggestService<DatasetSearchResult, DatasetSearchParameter, SolrAnnotatedDataset, DatasetSearchRequest, DatasetSuggestResult, SolrAnnotatedSuggestDataset, DatasetSuggestRequest>
  implements DatasetSearchService {

  // Order by best score, then alphabetically by title
  private static final Map<String, SolrQuery.ORDER> PRIMARY_SORT_ORDER = ImmutableSortedMap.of(
    "score", SolrQuery.ORDER.desc,
    "dataset_title", SolrQuery.ORDER.asc);

  @Inject
  public DatasetSearchServiceSolr(@Named("Dataset") SolrClient solrClient) {
    super(solrClient, DatasetSearchResult.class, SolrAnnotatedDataset.class, DatasetSearchParameter.class,
      PRIMARY_SORT_ORDER, SolrAnnotatedSuggestDataset.class);
  }

}
