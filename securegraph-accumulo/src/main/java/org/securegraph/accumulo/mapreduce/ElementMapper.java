package org.securegraph.accumulo.mapreduce;

import org.apache.accumulo.core.data.Mutation;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.securegraph.*;
import org.securegraph.accumulo.*;
import org.securegraph.accumulo.serializer.ValueSerializer;
import org.securegraph.id.IdGenerator;

import java.io.IOException;
import java.net.URISyntaxException;

public abstract class ElementMapper<KEYIN, VALUEIN, KEYOUT, VALUEOUT> extends Mapper<KEYIN, VALUEIN, KEYOUT, VALUEOUT> {
    public static final String GRAPH_CONFIG_PREFIX = "graphConfigPrefix";
    private ElementMutationBuilder elementMutationBuilder;
    private ElementMapperGraph graph;

    @Override
    protected void setup(final Context context) throws IOException, InterruptedException {
        super.setup(context);
        String configPrefix = context.getConfiguration().get(GRAPH_CONFIG_PREFIX, "");
        AccumuloGraphConfiguration accumuloGraphConfiguration = new AccumuloGraphConfiguration(context.getConfiguration(), configPrefix);
        String tableNamePrefix = accumuloGraphConfiguration.getTableNamePrefix();
        final Text edgesTableName = new Text(AccumuloGraph.getEdgesTableName(tableNamePrefix));
        final Text dataTableName = new Text(AccumuloGraph.getDataTableName(tableNamePrefix));
        final Text verticesTableName = new Text(AccumuloGraph.getVerticesTableName(tableNamePrefix));
        ValueSerializer valueSerializer = accumuloGraphConfiguration.createValueSerializer();
        long maxStreamingPropertyValueTableDataSize = accumuloGraphConfiguration.getMaxStreamingPropertyValueTableDataSize();
        String dataDir = accumuloGraphConfiguration.getDataDir();
        FileSystem fileSystem;
        try {
            fileSystem = accumuloGraphConfiguration.createFileSystem();
        } catch (URISyntaxException e) {
            throw new IOException("Could not initialize", e);
        }

        this.elementMutationBuilder = new ElementMutationBuilder(fileSystem, valueSerializer, maxStreamingPropertyValueTableDataSize, dataDir) {
            @Override
            protected void saveVertexMutation(Mutation m) {
                try {
                    ElementMapper.this.saveVertexMutation(context, verticesTableName, m);
                } catch (Exception e) {
                    throw new RuntimeException("Could not save vertex", e);
                }
            }

            @Override
            protected void saveEdgeMutation(Mutation m) {
                try {
                    ElementMapper.this.saveEdgeMutation(context, edgesTableName, m);
                } catch (Exception e) {
                    throw new RuntimeException("Could not save edge", e);
                }
            }

            @Override
            protected void saveDataMutation(Mutation m) {
                try {
                    ElementMapper.this.saveDataMutation(context, dataTableName, m);
                } catch (Exception e) {
                    throw new RuntimeException("Could not save data", e);
                }
            }
        };

        this.graph = new ElementMapperGraph(this);
    }

    protected abstract void saveDataMutation(Context context, Text dataTableName, Mutation m) throws IOException, InterruptedException;

    protected abstract void saveEdgeMutation(Context context, Text edgesTableName, Mutation m) throws IOException, InterruptedException;

    protected abstract void saveVertexMutation(Context context, Text verticesTableName, Mutation m) throws IOException, InterruptedException;

    public VertexBuilder prepareVertex(String vertexId, Visibility visibility) {
        if (vertexId == null) {
            vertexId = getIdGenerator().nextId();
        }

        return new VertexBuilder(vertexId, visibility) {
            @Override
            public Vertex save(Authorizations authorizations) {
                AccumuloVertex vertex = new AccumuloVertex(null, getVertexId(), getVisibility(), getProperties(), authorizations);
                elementMutationBuilder.saveVertex(vertex);
                return vertex;
            }
        };
    }

    public Edge addEdge(String edgeId, Vertex outVertex, Vertex inVertex, String label, Visibility visibility, Authorizations authorizations) {
        return prepareEdge(edgeId, outVertex, inVertex, label, visibility).save(authorizations);
    }

    public EdgeBuilder prepareEdge(String edgeId, Vertex outVertex, Vertex inVertex, String label, Visibility visibility) {
        if (edgeId == null) {
            edgeId = getIdGenerator().nextId();
        }

        return new EdgeBuilder(edgeId, outVertex, inVertex, label, visibility) {
            @Override
            public Edge save(Authorizations authorizations) {
                AccumuloEdge edge = new AccumuloEdge(null, getEdgeId(), getOutVertex().getId(), getInVertex().getId(), getLabel(), getVisibility(), getProperties(), authorizations);
                elementMutationBuilder.saveEdge(edge);
                return edge;
            }
        };
    }

    public abstract IdGenerator getIdGenerator();

    public Graph getGraph() {
        return graph;
    }
}
