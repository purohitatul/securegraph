package org.securegraph.accumulo.mapreduce;

import org.apache.accumulo.core.client.AccumuloSecurityException;
import org.apache.accumulo.core.client.security.tokens.AuthenticationToken;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.util.PeekingIterator;
import org.apache.hadoop.mapreduce.Job;
import org.securegraph.Authorizations;
import org.securegraph.Vertex;
import org.securegraph.accumulo.AccumuloGraph;
import org.securegraph.accumulo.VertexMaker;

import java.util.Map;

public class AccumuloVertexInputFormat extends AccumuloElementInputFormatBase<Vertex> {
    public static void setInputInfo(Job job, AccumuloGraph graph, String instanceName, String zooKeepers, String principal, AuthenticationToken token, String[] authorizations) throws AccumuloSecurityException {
        String tableName = graph.getVerticesTableName();
        setInputInfo(job, instanceName, zooKeepers, principal, token, authorizations, tableName);
    }

    @Override
    protected Vertex createElementFromRow(AccumuloGraph graph, PeekingIterator<Map.Entry<Key, Value>> row, Authorizations authorizations) {
        VertexMaker maker = new VertexMaker(graph, row, authorizations);
        return maker.make();
    }
}

