package org.securegraph.accumulo;

import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.iterators.user.RowDeletingIterator;
import org.apache.accumulo.core.security.ColumnVisibility;
import org.apache.hadoop.io.Text;
import org.securegraph.Authorizations;
import org.securegraph.Property;
import org.securegraph.SecureGraphException;
import org.securegraph.Visibility;

import java.util.*;

public abstract class ElementMaker<T> {
    private final Iterator<Map.Entry<Key, Value>> row;
    private final Map<String, String> propertyNames = new HashMap<String, String>();
    private final Map<String, String> propertyColumnQualifier = new HashMap<String, String>();
    private final Map<String, byte[]> propertyValues = new HashMap<String, byte[]>();
    private final Map<String, Visibility> propertyVisibilities = new HashMap<String, Visibility>();
    private final Map<String, byte[]> propertyMetadata = new HashMap<String, byte[]>();
    private final AccumuloGraph graph;
    private final Authorizations authorizations;
    private String id;
    private Visibility visibility;

    public ElementMaker(AccumuloGraph graph, Iterator<Map.Entry<Key, Value>> row, Authorizations authorizations) {
        this.graph = graph;
        this.row = row;
        this.authorizations = authorizations;
    }

    public T make() {
        while (row.hasNext()) {
            Map.Entry<Key, Value> col = row.next();

            if (this.id == null) {
                this.id = getIdFromRowKey(col.getKey().getRow().toString());
            }

            Text columnFamily = col.getKey().getColumnFamily();
            Text columnQualifier = col.getKey().getColumnQualifier();
            ColumnVisibility columnVisibility = getGraph().visibilityToAccumuloVisibility(col.getKey().getColumnVisibility().toString());
            Value value = col.getValue();

            if (columnFamily.equals(AccumuloGraph.DELETE_ROW_COLUMN_FAMILY)
                    && columnQualifier.equals(AccumuloGraph.DELETE_ROW_COLUMN_QUALIFIER)
                    && value.equals(RowDeletingIterator.DELETE_ROW_VALUE)) {
                return null;
            }

            if (AccumuloElement.CF_PROPERTY.compareTo(columnFamily) == 0) {
                extractPropertyData(columnQualifier, columnVisibility, value);
                continue;
            }

            if (AccumuloElement.CF_PROPERTY_METADATA.compareTo(columnFamily) == 0) {
                extractPropertyMetadata(columnQualifier, columnVisibility, value);
                continue;
            }

            if (getVisibilitySignal().equals(columnFamily.toString())) {
                this.visibility = accumuloVisibilityToVisibility(columnVisibility);
            }

            processColumn(col.getKey(), col.getValue());
        }

        // If the org.securegraph.accumulo.iterator.ElementVisibilityRowFilter isn't installed this will catch stray rows
        if (this.visibility == null) {
            return null;
        }

        return makeElement();
    }

    protected abstract void processColumn(Key key, Value value);

    protected abstract String getIdFromRowKey(String rowKey);

    protected abstract String getVisibilitySignal();

    protected abstract T makeElement();

    protected String getId() {
        return this.id;
    }

    protected Visibility getVisibility() {
        return this.visibility;
    }

    public AccumuloGraph getGraph() {
        return graph;
    }

    protected List<Property> getProperties() {
        List<Property> results = new ArrayList<Property>(propertyValues.size());
        for (Map.Entry<String, byte[]> propertyValueEntry : propertyValues.entrySet()) {
            String key = propertyValueEntry.getKey();
            String propertyKey = getPropertyKeyFromColumnQualifier(propertyColumnQualifier.get(key));
            String propertyName = propertyNames.get(key);
            byte[] propertyValue = propertyValueEntry.getValue();
            Visibility visibility = propertyVisibilities.get(key);
            byte[] metadata = propertyMetadata.get(key);
            results.add(new LazyMutableProperty(getGraph(), getGraph().getValueSerializer(), propertyKey, propertyName, propertyValue, metadata, visibility));
        }
        return results;
    }

    private void extractPropertyMetadata(Text columnQualifier, ColumnVisibility columnVisibility, Value value) {
        String key = toKey(columnQualifier, columnVisibility);
        propertyMetadata.put(key, value.get());
    }

    private void extractPropertyData(Text columnQualifier, ColumnVisibility columnVisibility, Value value) {
        String propertyName = getPropertyNameFromColumnQualifier(columnQualifier.toString());
        String key = toKey(columnQualifier, columnVisibility);
        propertyColumnQualifier.put(key, columnQualifier.toString());
        propertyNames.put(key, propertyName);
        propertyValues.put(key, value.get());
        propertyVisibilities.put(key, accumuloVisibilityToVisibility(columnVisibility));
    }

    private String toKey(Text columnQualifier, ColumnVisibility columnVisibility) {
        return columnQualifier.toString() + ":" + columnVisibility.toString();
    }

    private String getPropertyNameFromColumnQualifier(String columnQualifier) {
        int i = columnQualifier.indexOf(ElementMutationBuilder.VALUE_SEPARATOR);
        if (i < 0) {
            throw new SecureGraphException("Invalid property column qualifier");
        }
        return columnQualifier.substring(0, i);
    }

    private Visibility accumuloVisibilityToVisibility(ColumnVisibility columnVisibility) {
        String columnVisibilityString = columnVisibility.toString();
        if (columnVisibilityString.startsWith("[") && columnVisibilityString.endsWith("]")) {
            return new Visibility(columnVisibilityString.substring(1, columnVisibilityString.length() - 1));
        }
        return new Visibility(columnVisibilityString);
    }

    private String getPropertyKeyFromColumnQualifier(String columnQualifier) {
        int i = columnQualifier.indexOf(ElementMutationBuilder.VALUE_SEPARATOR);
        if (i < 0) {
            throw new SecureGraphException("Invalid property column qualifier");
        }
        return columnQualifier.substring(i + 1);
    }

    public Authorizations getAuthorizations() {
        return authorizations;
    }
}
