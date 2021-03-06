package org.securegraph.accumulo.mapreduce;

import org.securegraph.*;
import org.securegraph.id.IdGenerator;
import org.securegraph.query.GraphQuery;

import java.util.EnumSet;

public class ElementMapperGraph extends GraphBase {
    private ElementMapper elementMapper;

    public ElementMapperGraph(ElementMapper elementMapper) {
        this.elementMapper = elementMapper;
    }

    @Override
    public VertexBuilder prepareVertex(String vertexId, Visibility visibility) {
        return this.elementMapper.prepareVertex(vertexId, visibility);
    }

    @Override
    public Iterable<Vertex> getVertices(EnumSet<FetchHint> fetchHints, Authorizations authorizations) {
        throw new SecureGraphException("Not supported");
    }

    @Override
    public void removeVertex(Vertex vertex, Authorizations authorizations) {
        throw new SecureGraphException("Not supported");
    }

    @Override
    public EdgeBuilder prepareEdge(String edgeId, Vertex outVertex, Vertex inVertex, String label, Visibility visibility) {
        return this.elementMapper.prepareEdge(edgeId, outVertex, inVertex, label, visibility);
    }

    @Override
    public Iterable<Edge> getEdges(EnumSet<FetchHint> fetchHints, Authorizations authorizations) {
        throw new SecureGraphException("Not supported");
    }

    @Override
    public void removeEdge(Edge edge, Authorizations authorizations) {
        throw new SecureGraphException("Not supported");
    }

    @Override
    public GraphQuery query(Authorizations authorizations) {
        throw new SecureGraphException("Not supported");
    }

    @Override
    public GraphQuery query(String queryString, Authorizations authorizations) {
        throw new SecureGraphException("Not supported");
    }

    @Override
    public void reindex(Authorizations authorizations) {
        throw new SecureGraphException("Not supported");
    }

    @Override
    public void flush() {
        throw new SecureGraphException("Not supported");
    }

    @Override
    public void shutdown() {
        throw new SecureGraphException("Not supported");
    }

    @Override
    public IdGenerator getIdGenerator() {
        return this.elementMapper.getIdGenerator();
    }

    @Override
    public boolean isVisibilityValid(Visibility visibility, Authorizations authorizations) {
        throw new SecureGraphException("Not supported");
    }

    @Override
    public DefinePropertyBuilder defineProperty(String propertyName) {
        throw new SecureGraphException("Not supported");
    }

    @Override
    public boolean isFieldBoostSupported() {
        throw new SecureGraphException("Not supported");
    }

    @Override
    public boolean isEdgeBoostSupported() {
        throw new SecureGraphException("Not supported");
    }

    @Override
    public void clearData() {
        throw new SecureGraphException("Not supported");
    }

    @Override
    public SearchIndexSecurityGranularity getSearchIndexSecurityGranularity() {
        throw new SecureGraphException("Not supported");
    }
}
