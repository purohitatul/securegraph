package org.securegraph.accumulo;

import org.apache.hadoop.io.Text;
import org.securegraph.*;
import org.securegraph.mutation.ExistingElementMutation;
import org.securegraph.mutation.ExistingElementMutationImpl;

import java.util.EnumSet;

public class AccumuloEdge extends AccumuloElement<Edge> implements Edge {
    public static final Text CF_SIGNAL = new Text("E");
    public static final Text CF_OUT_VERTEX = new Text("EOUT");
    public static final Text CF_IN_VERTEX = new Text("EIN");
    private final String outVertexId;
    private final String inVertexId;
    private final String label;

    public AccumuloEdge(Graph graph, String id, String outVertexId, String inVertexId, String label, Visibility visibility, Iterable<Property> properties, Authorizations authorizations) {
        super(graph, id, visibility, properties, authorizations);
        this.outVertexId = outVertexId;
        this.inVertexId = inVertexId;
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public String getVertexId(Direction direction) {
        switch (direction) {
            case OUT:
                return outVertexId;
            case IN:
                return inVertexId;
            default:
                throw new IllegalArgumentException("Unexpected direction: " + direction);
        }
    }

    @Override
    public Vertex getVertex(Direction direction, Authorizations authorizations) {
        return getVertex(direction, FetchHint.ALL, authorizations);
    }

    @Override
    public String getOtherVertexId(String myVertexId) {
        if (inVertexId.equals(myVertexId)) {
            return outVertexId;
        } else if (outVertexId.equals(myVertexId)) {
            return inVertexId;
        }
        throw new SecureGraphException("myVertexId does not appear on either the in or the out.");
    }

    @Override
    public Vertex getOtherVertex(String myVertexId, Authorizations authorizations) {
        return getOtherVertex(myVertexId, FetchHint.ALL, authorizations);
    }

    @Override
    public Vertex getOtherVertex(String myVertexId, EnumSet<FetchHint> fetchHints, Authorizations authorizations) {
        return getGraph().getVertex(getOtherVertexId(myVertexId), fetchHints, authorizations);
    }

    @Override
    public Vertex getVertex(Direction direction, EnumSet<FetchHint> fetchHints, Authorizations authorizations) {
        return getGraph().getVertex(getVertexId(direction), fetchHints, authorizations);
    }

    @Override
    public ExistingElementMutation<Edge> prepareMutation() {
        return new ExistingElementMutationImpl<Edge>(this) {
            @Override
            public Edge save(Authorizations authorizations) {
                saveExistingElementMutation(this, authorizations);
                return getElement();
            }
        };
    }
}
