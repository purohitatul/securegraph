package org.securegraph.elasticsearch;

import org.elasticsearch.action.admin.indices.mapping.put.PutMappingResponse;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.securegraph.*;
import org.securegraph.property.StreamingPropertyValue;
import org.securegraph.query.GraphQuery;
import org.securegraph.type.GeoPoint;
import org.securegraph.util.StreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ElasticSearchSearchIndex extends ElasticSearchSearchIndexBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchSearchIndexBase.class);

    public ElasticSearchSearchIndex(Map config) {
        super(config);
    }

    @Override
    public void addElement(Graph graph, Element element, Authorizations authorizations) {
        IndexInfo indexInfo = addPropertiesToIndex(element, element.getProperties());

        try {
            boolean mergeExisting = true;
            XContentBuilder jsonBuilder = buildJsonContentFromElement(graph, indexInfo, element, mergeExisting, authorizations);

            IndexResponse response = getClient()
                    .prepareIndex(indexInfo.getIndexName(), ElasticSearchSearchIndexBase.ELEMENT_TYPE, element.getId())
                    .setSource(jsonBuilder.endObject())
                    .execute()
                    .actionGet();
            if (response.getId() == null) {
                throw new SecureGraphException("Could not index document " + element.getId());
            }

            if (isAutoflush()) {
                flush();
            }
        } catch (Exception e) {
            throw new SecureGraphException("Could not add element", e);
        }

        if (isUseEdgeBoost() && element instanceof Edge) {
            Element vOut = ((Edge) element).getVertex(Direction.OUT, authorizations);
            if (vOut != null) {
                addElement(graph, vOut, authorizations);
            }
            Element vIn = ((Edge) element).getVertex(Direction.IN, authorizations);
            if (vIn != null) {
                addElement(graph, vIn, authorizations);
            }
        }
    }

    @Override
    public void removeElement(Graph graph, Element element, Authorizations authorizations) {
        String indexName = getIndexName(element);
        String id = element.getId();
        LOGGER.debug("deleting document " + id);
        DeleteResponse deleteResponse = getClient().delete(
                getClient()
                        .prepareDelete(indexName, ELEMENT_TYPE, id)
                        .request()
        ).actionGet();
        if (!deleteResponse.isFound()) {
            throw new SecureGraphException("Could not remove element " + element.getId());
        }
    }

    public String createJsonForElement(Graph graph, Element element, boolean mergeExisting, Authorizations authorizations) {
        try {
            String indexName = getIndexName(element);
            IndexInfo indexInfo = ensureIndexCreatedAndInitialized(indexName, isStoreSourceData());
            return buildJsonContentFromElement(graph, indexInfo, element, mergeExisting, authorizations).string();
        } catch (Exception e) {
            throw new SecureGraphException("Could not create JSON for element", e);
        }
    }

    private XContentBuilder buildJsonContentFromElement(Graph graph, IndexInfo indexInfo, Element element, boolean mergeExisting, Authorizations authorizations) throws IOException {
        XContentBuilder jsonBuilder;
        jsonBuilder = XContentFactory.jsonBuilder()
                .startObject();

        if (mergeExisting) {
            element = requeryWithAuthsAndMergedElement(graph, element, authorizations);
        }

        if (element instanceof Vertex) {
            jsonBuilder.field(ElasticSearchSearchIndexBase.ELEMENT_TYPE_FIELD_NAME, ElasticSearchSearchIndexBase.ELEMENT_TYPE_VERTEX);
            if (isUseEdgeBoost()) {
                int inEdgeCount = ((Vertex) element).getEdgeCount(Direction.IN, authorizations);
                jsonBuilder.field(ElasticSearchSearchIndexBase.IN_EDGE_COUNT_FIELD_NAME, inEdgeCount);
                int outEdgeCount = ((Vertex) element).getEdgeCount(Direction.OUT, authorizations);
                jsonBuilder.field(ElasticSearchSearchIndexBase.OUT_EDGE_COUNT_FIELD_NAME, outEdgeCount);
            }
        } else if (element instanceof Edge) {
            jsonBuilder.field(ElasticSearchSearchIndexBase.ELEMENT_TYPE_FIELD_NAME, ElasticSearchSearchIndexBase.ELEMENT_TYPE_EDGE);
        } else {
            throw new SecureGraphException("Unexpected element type " + element.getClass().getName());
        }

        Set<String> visibilityStrings = new HashSet<String>();
        visibilityStrings.add(element.getVisibility().getVisibilityString());

        for (Property property : element.getProperties()) {
            visibilityStrings.add(property.getVisibility().getVisibilityString());

            Object propertyValue = property.getValue();
            if (propertyValue != null && shouldIgnoreType(propertyValue.getClass())) {
                continue;
            } else if (propertyValue instanceof GeoPoint) {
                GeoPoint geoPoint = (GeoPoint) propertyValue;
                Map<String, Object> propertyValueMap = new HashMap<String, Object>();
                propertyValueMap.put("lat", geoPoint.getLatitude());
                propertyValueMap.put("lon", geoPoint.getLongitude());
                propertyValue = propertyValueMap;
            } else if (propertyValue instanceof StreamingPropertyValue) {
                StreamingPropertyValue streamingPropertyValue = (StreamingPropertyValue) propertyValue;
                if (!streamingPropertyValue.isSearchIndex()) {
                    continue;
                }
                Class valueType = streamingPropertyValue.getValueType();
                if (valueType == String.class) {
                    InputStream in = streamingPropertyValue.getInputStream();
                    propertyValue = StreamUtils.toString(in);
                } else {
                    throw new SecureGraphException("Unhandled StreamingPropertyValue type: " + valueType.getName());
                }
            } else if (propertyValue instanceof String) {
                PropertyDefinition propertyDefinition = indexInfo.getPropertyDefinitions().get(property.getName());
                if (propertyDefinition == null || propertyDefinition.getTextIndexHints().contains(TextIndexHint.EXACT_MATCH)) {
                    jsonBuilder.field(property.getName() + ElasticSearchSearchIndexBase.EXACT_MATCH_PROPERTY_NAME_SUFFIX, propertyValue);
                }
                if (propertyDefinition == null || propertyDefinition.getTextIndexHints().contains(TextIndexHint.FULL_TEXT)) {
                    jsonBuilder.field(property.getName(), propertyValue);
                }
                continue;
            }

            if (propertyValue instanceof DateOnly) {
                propertyValue = ((DateOnly) propertyValue).getDate();
            }

            jsonBuilder.field(property.getName(), propertyValue);
        }

        String visibilityString = Visibility.and(visibilityStrings).getVisibilityString();
        jsonBuilder.field(VISIBILITY_FIELD_NAME, visibilityString);

        return jsonBuilder;
    }

    private Element requeryWithAuthsAndMergedElement(Graph graph, Element element, Authorizations authorizations) {
        Element existingElement;
        if (element instanceof Vertex) {
            existingElement = graph.getVertex(element.getId(), authorizations);
        } else if (element instanceof Edge) {
            existingElement = graph.getEdge(element.getId(), authorizations);
        } else {
            throw new SecureGraphException("Unexpected element type " + element.getClass().getName());
        }
        if (existingElement == null) {
            return element;
        }

        LOGGER.debug("Reindexing element " + element.getId());
        existingElement.mergeProperties(element);

        return existingElement;
    }

    @Override
    protected void addPropertyToIndex(IndexInfo indexInfo, String propertyName, Class dataType, boolean analyzed, Double boost) throws IOException {
        if (indexInfo.isPropertyDefined(propertyName)) {
            return;
        }

        if (shouldIgnoreType(dataType)) {
            return;
        }

        XContentBuilder mapping = XContentFactory.jsonBuilder()
                .startObject()
                .startObject(ElasticSearchSearchIndexBase.ELEMENT_TYPE)
                .startObject("properties")
                .startObject(propertyName);

        addTypeToMapping(mapping, propertyName, dataType, analyzed, boost);

        mapping
                .endObject()
                .endObject()
                .endObject()
                .endObject();

        PutMappingResponse response = getClient()
                .admin()
                .indices()
                .preparePutMapping(indexInfo.getIndexName())
                .setIgnoreConflicts(false)
                .setType(ElasticSearchSearchIndexBase.ELEMENT_TYPE)
                .setSource(mapping)
                .execute()
                .actionGet();
        LOGGER.debug(response.toString());

        indexInfo.addPropertyDefinition(propertyName, new PropertyDefinition(propertyName, dataType, TextIndexHint.ALL));
    }

    @Override
    public GraphQuery queryGraph(Graph graph, String queryString, Authorizations authorizations) {
        return new ElasticSearchGraphQuery(getClient(), getIndicesToQuery(), graph, queryString, getAllPropertyDefinitions(), getInEdgeBoost(), getOutEdgeBoost(), authorizations);
    }

    @Override
    public SearchIndexSecurityGranularity getSearchIndexSecurityGranularity() {
        return SearchIndexSecurityGranularity.DOCUMENT;
    }
}
