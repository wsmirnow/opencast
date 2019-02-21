/**
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */


package org.opencastproject.matterhorn.search.impl;

import static org.opencastproject.matterhorn.search.impl.IndexSchema.VERSION;

import org.opencastproject.matterhorn.search.SearchIndex;
import org.opencastproject.matterhorn.search.SearchIndexException;
import org.opencastproject.matterhorn.search.SearchQuery;
import org.opencastproject.matterhorn.search.SearchQuery.Order;
import org.opencastproject.util.PathSupport;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingResponse;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequestBuilder;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.indices.IndexAlreadyExistsException;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * A search index implementation based on ElasticSearch.
 */
public abstract class AbstractElasticsearchIndex implements SearchIndex {

  /** Logging facility */
  private static final Logger logger = LoggerFactory.getLogger(AbstractElasticsearchIndex.class);

  /** The Elasticsearch maximum results window size */
  private static final int ELASTICSEARCH_INDEX_MAX_RESULT_WINDOW = Integer.MAX_VALUE;

  /** The Elasticsearch config directory key */
  public static final String ELASTICSEARCH_CONFIG_DIR_KEY = "org.opencastproject.elasticsearch.config.dir";

  /** Configuration key defining the address of an external Elasticsearch server */
  public static final String ELASTICSEARCH_SERVER_ADDRESS_KEY = "org.opencastproject.elasticsearch.server.address";

  /** Configuration key defining the port of an external Elasticsearch server */
  public static final String ELASTICSEARCH_SERVER_PORT_KEY = "org.opencastproject.elasticsearch.server.port";

  /** Identifier of the root entry */
  private static final String ROOT_ID = "root";

  /** Type of the document containing the index version information */
  private static final String VERSION_TYPE = "version";

  /** The index identifier */
  private String index = null;

  /** The local elastic search node */
  private static Node elasticSearch = null;

  /** List of clients to the local node */
  private static List<Client> elasticSearchClients = new ArrayList<>();

  /** Client for talking to elastic search */
  private Client nodeClient = null;

  /** List of sites with prepared index */
  private final List<String> preparedIndices = new ArrayList<>();

  /** The version number */
  private int indexVersion = -1;

  /** The path to the index settings */
  protected String indexSettingsPath;

  /**
   * Address of an external Elasticsearch server to connect to.
   * Opencast will not try to launch an internal server if this is defined.
   **/
  private String externalServerAddress = null;

  /** Port of an external Elasticsearch server to connect to */
  private int externalServerPort = 9300;

  /**
   * Returns an array of document types for the index. For every one of these, the corresponding document type
   * definition will be loaded.
   *
   * @return the document types
   */
  public abstract String[] getDocumentTypes();

  /**
   * OSGi callback to activate this component instance.
   *
   * @param ctx
   *          the component context
   * @throws ComponentException
   *           if the search index cannot be initialized
   */
  public void activate(ComponentContext ctx) throws ComponentException {
    indexSettingsPath = StringUtils.trimToNull(ctx.getBundleContext().getProperty(ELASTICSEARCH_CONFIG_DIR_KEY));
    if (indexSettingsPath == null) {
      final String etc = StringUtils.trimToNull(ctx.getBundleContext().getProperty("karaf.etc"));
      if (etc == null) {
        throw new ComponentException("Configuration for key '" + ELASTICSEARCH_CONFIG_DIR_KEY + "' missing");
      }
      indexSettingsPath = etc + "/index";
    }

    // Address of an external Elasticsearch node.
    // It's fine if this is not set. Opencast will then launch its own node.
    externalServerAddress = StringUtils.trimToNull(ctx.getBundleContext().getProperty(ELASTICSEARCH_SERVER_ADDRESS_KEY));

    // Silently fall back to port 9300
    externalServerPort = Integer.parseInt(StringUtils.defaultIfBlank(
            ctx.getBundleContext().getProperty(ELASTICSEARCH_SERVER_PORT_KEY), "9300"));
  }

  /**
   * Returns the client used to query the index.
   *
   * @return the Elasticsearch node client
   */
  protected Client getSearchClient() {
    return nodeClient;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int getIndexVersion() {
    return indexVersion;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void clear() throws IOException {
    try {
      IndicesExistsResponse indicesExistsResponse = nodeClient.admin().indices()
              .exists(new IndicesExistsRequest(getIndexName())).actionGet();
      if (indicesExistsResponse.isExists()) {
        DeleteIndexResponse delete = nodeClient.admin().indices().delete(new DeleteIndexRequest(getIndexName()))
                .actionGet();
        if (!delete.isAcknowledged())
          logger.error("Index '{}' could not be deleted", getIndexName());
      } else {
        logger.error("Cannot clear non-existing index '{}'", getIndexName());
      }
    } catch (Throwable t) {
      throw new IOException("Cannot clear index", t);
    }

    preparedIndices.remove(getIndexName());
    // Create the index
    try {
      createIndex(index);
    } catch (SearchIndexException e) {
      logger.error("Unable to re-create the index after a clear", e);
    }
  }

  /**
   * Removes the given document from the specified index.
   *
   * @param type
   *          the document type
   * @param uid
   *          the identifier
   * @return <code>true</code> if the element was found and deleted
   * @throws SearchIndexException
   *           if deletion fails
   */
  protected boolean delete(String type, String uid) throws SearchIndexException {

    if (!preparedIndices.contains(index)) {
      try {
        createIndex(index);
      } catch (IOException e) {
        throw new SearchIndexException(e);
      }
    }

    logger.debug("Removing element with id '{}' from searching index", uid);

    DeleteRequestBuilder deleteRequest = nodeClient.prepareDelete(index, type, uid);
    deleteRequest.setRefresh(true);
    DeleteResponse delete = deleteRequest.execute().actionGet();
    if (!delete.isFound()) {
      logger.trace("Document {} to delete was not found", uid);
      return false;
    }

    return true;
  }

  /**
   * Posts the input document to the search index.
   *
   * @param documents
   *          the input documents
   * @return the query response
   * @throws SearchIndexException
   *           if posting to the index fails
   */
  protected BulkResponse update(ElasticsearchDocument... documents) throws SearchIndexException {

    BulkRequestBuilder bulkRequest = nodeClient.prepareBulk();
    for (ElasticsearchDocument doc : documents) {
      String type = doc.getType();
      String uid = doc.getUID();
      bulkRequest.add(nodeClient.prepareIndex(index, type, uid).setSource(doc));
    }

    // Make sure the operations are searchable immediately
    bulkRequest.setRefresh(true);

    try {
      BulkResponse bulkResponse = bulkRequest.execute().actionGet();

      // Check for errors
      if (bulkResponse.hasFailures()) {
        for (BulkItemResponse item : bulkResponse.getItems()) {
          if (item.isFailed()) {
            logger.warn("Error updating {}: {}", item, item.getFailureMessage());
            throw new SearchIndexException(item.getFailureMessage());
          }
        }
      }

      return bulkResponse;
    } catch (Throwable t) {
      throw new SearchIndexException("Cannot update documents in index " + index, t);
    }
  }

  /**
   * Initializes an Elasticsearch node for the given index.
   *
   * @param index
   *          the index identifier
   * @param version
   *          the index version
   * @throws SearchIndexException
   *           if the index configuration cannot be loaded
   * @throws IOException
   *           if loading of settings fails
   * @throws IllegalArgumentException
   *           if the index identifier is blank.
   */
  protected void init(String index, int version) throws IOException, IllegalArgumentException, SearchIndexException {
    if (StringUtils.isBlank(index)) {
      throw new IllegalArgumentException("Search index identifier must be set");
    }

    this.index = index;
    this.indexVersion = version;

    // Configure and start Elasticsearch
    synchronized (AbstractElasticsearchIndex.class) {

      // Prepare the configuration of the elastic search node
      Settings settings = loadSettings(index, indexSettingsPath);
      if (elasticSearch == null && externalServerAddress == null) {
        logger.info("Starting local Elasticsearch node");

        // Configure and start the elastic search node. In a testing scenario,
        // the node is being created locally.
        NodeBuilder nodeBuilder = NodeBuilder.nodeBuilder().settings(settings);
        elasticSearch = nodeBuilder.local(TestUtils.isTest()).build();
        elasticSearch.start();
        logger.info("Elasticsearch node is up and running");
      }

      // Create the client
      if (nodeClient == null) {
        if (elasticSearch == null) {
          // configure external Elasticsearch
          nodeClient = TransportClient.builder()
                  .settings(settings).build()
                  .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(externalServerAddress),
                          externalServerPort));
        } else {
          // configure internal Elasticsearch
          nodeClient = elasticSearch.client();
        }
        elasticSearchClients.add(nodeClient);
      }
    }

    // Create the index
    createIndex(index);
  }

  /**
   * Closes the client and stops and closes the Elasticsearch node.
   *
   * @throws IOException
   *           if stopping the Elasticsearch node fails
   */
  protected void close() throws IOException {
    try {
      if (nodeClient != null) {
        nodeClient.close();
        synchronized (AbstractElasticsearchIndex.class) {
          elasticSearchClients.remove(nodeClient);
          if (elasticSearchClients.isEmpty() && elasticSearch != null) {
            logger.info("Stopping local Elasticsearch node");
            elasticSearch.close();
            elasticSearch = null;
          }
        }
      }
    } catch (Throwable t) {
      throw new IOException("Error stopping the Elasticsearch node", t);
    }
  }

  /**
   * Prepares Elasticsearch index to store data for the types (or mappings) as returned by {@link #getDocumentTypes()}.
   *
   * @param idx
   *          the index name
   *
   * @throws SearchIndexException
   *           if index and type creation fails
   * @throws IOException
   *           if loading of the type definitions fails
   */
  private void createIndex(String idx) throws SearchIndexException, IOException {

    // Make sure the site index exists
    try {
      IndicesExistsResponse indicesExistsResponse = nodeClient.admin().indices()
              .exists(new IndicesExistsRequest(idx)).actionGet();
      if (!indicesExistsResponse.isExists()) {
        logger.debug("Trying to create index for '{}'", idx);
        CreateIndexRequest indexCreateRequest = new CreateIndexRequest(idx);
        String settings = getIndexSettings(idx);
        if (settings != null)
          indexCreateRequest.settings(settings);
        CreateIndexResponse siteidxResponse = nodeClient.admin().indices().create(indexCreateRequest).actionGet();
        if (!siteidxResponse.isAcknowledged()) {
          throw new SearchIndexException("Unable to create index for '" + idx + "'");
        }
      }
    } catch (IndexAlreadyExistsException e) {
      logger.info("Detected existing index '{}'", idx);
    }

    // Store the correct mapping
    for (String type : getDocumentTypes()) {
      PutMappingRequest siteMappingRequest = new PutMappingRequest(idx);
      siteMappingRequest.source(getIndexTypeDefinition(idx, type));
      siteMappingRequest.type(type);
      PutMappingResponse siteMappingResponse = nodeClient.admin().indices().putMapping(siteMappingRequest).actionGet();
      if (!siteMappingResponse.isAcknowledged()) {
        throw new SearchIndexException("Unable to install '" + type + "' mapping for index '" + idx + "'");
      }
    }

    // See if the index version exists and check if it matches. The request will
    // fail if there is no version index
    boolean versionIndexExists = false;
    GetRequestBuilder getRequestBuilder = nodeClient.prepareGet(idx, VERSION_TYPE, ROOT_ID);
    try {
      GetResponse response = getRequestBuilder.execute().actionGet();
      if (response.isExists() && response.getField(VERSION) != null) {
        int actualIndexVersion = Integer.parseInt((String) response.getField(VERSION).getValue());
        if (indexVersion != actualIndexVersion)
          throw new SearchIndexException("Search index is at version " + actualIndexVersion + ", but codebase expects "
                  + indexVersion);
        versionIndexExists = true;
        logger.debug("Search index version is {}", indexVersion);
      }
    } catch (ElasticsearchException e) {
      logger.debug("Version index has not been created");
    }

    // The index does not exist, let's create it
    if (!versionIndexExists) {
      logger.debug("Creating version index for site '{}'", idx);
      IndexRequestBuilder requestBuilder = nodeClient.prepareIndex(idx, VERSION_TYPE, ROOT_ID);
      logger.debug("Index version of site '{}' is {}", idx, indexVersion);
      requestBuilder = requestBuilder.setSource(VERSION, Integer.toString(indexVersion));
      requestBuilder.execute().actionGet();
    }

    preparedIndices.add(idx);
  }

  /**
   * Loads the settings for the elastic search configuration. An initial attempt is made to get the configuration from
   * <code>${opencast.home}/etc/index/&lt; index &gt;/settings.yml</code>.
   *
   * @param index
   *          the index name
   * @return the elastic search settings
   * @throws IOException
   *           if the index cannot be created in case it is not there already
   * @throws SearchIndexException
   *           if the index configuration cannot be found
   */
  protected Settings loadSettings(String index, String indexSettingsPath) throws IOException, SearchIndexException {
    // Check if a local configuration file is present
    File configFile = new File(PathSupport.concat(new String[] { indexSettingsPath, index, "settings.yml" }));
    if (!configFile.isFile()) {
      throw new SearchIndexException("Settings for search index '" + index + "' not found at " + configFile);
    }

    // Finally, try and load the index settings
    try (FileInputStream fis = new FileInputStream(configFile)) {
      return Settings.settingsBuilder().loadFromStream(configFile.getName(), fis).build();
    } catch (FileNotFoundException e) {
      throw new IOException("Unable to load elasticsearch settings from " + configFile.getAbsolutePath());
    }
  }

  /**
   * Loads the index settings. An initial attempt is made to get the configuration from
   * <code>${opencast.home}/etc/index/&lt;index&gt;/settings.json</code>. If this file can't be found, the
   * default mapping loaded from the classpath.
   *
   * @param index
   *          the index identifier
   * @return the string containing the configuration
   * @throws IOException
   *           if reading the index mapping fails
   */
  protected String getIndexSettings(String index) throws IOException {
    String settings = null;

    File configFile = new File(PathSupport.concat(new String[] { indexSettingsPath, index, "settings.json" }));
    if (configFile.isFile()) {
      try (FileInputStream fis = new FileInputStream(configFile)) {
        settings = IOUtils.toString(fis, StandardCharsets.UTF_8);
      } catch (IOException e) {
        logger.warn("Unable to load index settings from {}", configFile.getAbsolutePath(), e);
      }
    }

    // If no local settings were found, read them from the bundle resources
    if (settings == null) {
      String resourcePath = PathSupport
              .concat(new String[] { "/elasticsearch/", index, "settings.json" });
      try (InputStream is = this.getClass().getResourceAsStream(resourcePath)) {
        if (is != null) {
          logger.debug("Reading elastic search index settings '{}' from the bundle resource", index);
          settings = IOUtils.toString(is, StandardCharsets.UTF_8);
        }
      }
    }

    return settings;
  }

  /**
   * Loads the mapping configuration. An initial attempt is made to get the configuration from
   * <code>${opencast.home}/etc/index/&lt;index&gt;/&lt;type&gt;-mapping.json</code>. If this file can't be found, the
   * default mapping loaded from the classpath.
   *
   * @param index
   *          the index identifier
   * @param documentType
   *          the document type
   * @return the string containing the configuration
   * @throws SearchIndexException
   *           if the index cannot be created
   * @throws IOException
   *           if reading the index mapping fails
   */
  protected String getIndexTypeDefinition(String index, String documentType) throws SearchIndexException, IOException {
    String mapping = null;

    File configFile = new File(PathSupport.concat(new String[] { indexSettingsPath, index,
            documentType + "-mapping.json" }));
    if (configFile.isFile()) {
      try (FileInputStream fis = new FileInputStream(configFile)) {
        mapping = IOUtils.toString(fis, StandardCharsets.UTF_8);
      } catch (IOException e) {
        logger.warn("Unable to load index mapping from {}", configFile.getAbsolutePath(), e);
      }
    }

    // If no local settings were found, read them from the bundle resources
    if (mapping == null) {
      String resourcePath = PathSupport
              .concat(new String[] { "/elasticsearch/", index, documentType + "-mapping.json" });
      try (InputStream is = this.getClass().getResourceAsStream(resourcePath)) {
        if (is != null) {
          logger.debug("Reading elastic search index mapping '{}' from the bundle resource", documentType);
          mapping = IOUtils.toString(is, StandardCharsets.UTF_8);
        }
      }
    }

    return mapping;
  }

  /**
   * Creates a request builder for a search query based on the properties known by the search query.
   * <p>
   * Once this query builder has been created, support for ordering needs to be configured as needed.
   *
   * @param query
   *          the search query
   * @return the request builder
   */
  protected SearchRequestBuilder getSearchRequestBuilder(SearchQuery query, QueryBuilder queryBuilder) {

    SearchRequestBuilder requestBuilder = getSearchClient().prepareSearch(getIndexName());
    requestBuilder.setSearchType(SearchType.QUERY_THEN_FETCH);
    requestBuilder.setPreference("_local");

    // Create the actual search query
    requestBuilder.setQuery(queryBuilder);

    // Make sure all fields are being returned
    if (query.getFields().length > 0) {
      requestBuilder.addFields(query.getFields());
    } else {
      requestBuilder.setFetchSource(true);
    }

    // Types
    requestBuilder.setTypes(query.getTypes());

    // Pagination
    if (query.getOffset() >= 0)
      requestBuilder.setFrom(query.getOffset());

    int limit = ELASTICSEARCH_INDEX_MAX_RESULT_WINDOW;
    if (query.getLimit() > 0) {
      // limit + offset may not exceed some limit
      // this limit seems to be Integer.MAX_VALUE in elasticsearch v1.3 (as we currently use)
      // elasticsearch version 2.1 onwards documented this behaviour by index.max_result_window
      // see https://www.elastic.co/guide/en/elasticsearch/reference/2.1/index-modules.html
      if (query.getOffset() > 0
              && (long)query.getOffset() + (long)query.getLimit() > ELASTICSEARCH_INDEX_MAX_RESULT_WINDOW)
        limit = ELASTICSEARCH_INDEX_MAX_RESULT_WINDOW - query.getOffset();
      else
        limit = query.getLimit();
    }
    requestBuilder.setSize(limit);

    // Sort orders
    Map<String, Order> sortCriteria = query.getSortOrders();
    for (Entry<String, Order> sortCriterion : sortCriteria.entrySet()) {
      switch (sortCriterion.getValue()) {
        case Ascending:
          requestBuilder.addSort(sortCriterion.getKey(), SortOrder.ASC);
          break;
        case Descending:
          requestBuilder.addSort(sortCriterion.getKey(), SortOrder.DESC);
          break;
        default:
          break;
      }
    }
    logger.debug("Search query: {}", requestBuilder);
    return requestBuilder;
  }

  /**
   * Returns the name of this index.
   *
   * @return the index name
   */
  public String getIndexName() {
    return index;
  }

}
