package org.securegraph.accumulo;

import org.apache.hadoop.io.Text;
import org.securegraph.*;
import org.securegraph.mutation.ExistingElementMutationImpl;

import java.io.Serializable;

public abstract class AccumuloElement<T extends Element> extends ElementBase<T> implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final Text CF_PROPERTY = new Text("PROP");
    public static final Text CF_PROPERTY_METADATA = new Text("PROPMETA");

    protected AccumuloElement(Graph graph, String id, Visibility visibility, Iterable<Property> properties, Authorizations authorizations) {
        super(graph, id, visibility, properties, authorizations);
    }

    @Override
    public void removeProperty(String key, String name, Authorizations authorizations) {
        Property property = super.removePropertyInternal(key, name);
        if (property != null) {
            getGraph().removeProperty(this, property, authorizations);
        }
    }

    @Override
    public void removeProperty(String name, Authorizations authorizations) {
        Iterable<Property> properties = super.removePropertyInternal(name);
        for (Property property : properties) {
            getGraph().removeProperty(this, property, authorizations);
        }
    }

    @Override
    public AccumuloGraph getGraph() {
        return (AccumuloGraph) super.getGraph();
    }

    protected <TElement extends Element> void saveExistingElementMutation(ExistingElementMutationImpl<TElement> mutation, Authorizations authorizations) {
        // Order matters a lot here

        // metadata must be altered first because the lookup of a property can include visibility which will be altered by alterElementPropertyVisibilities
        getGraph().alterPropertyMetadatas((AccumuloElement) mutation.getElement(), mutation.getAlterPropertyMetadatas());

        // altering properties comes next because alterElementVisibility may alter the vertex and we won't find it
        getGraph().alterElementPropertyVisibilities((AccumuloElement) mutation.getElement(), mutation.getAlterPropertyVisibilities());

        Iterable<Property> properties = mutation.getProperties();
        updatePropertiesInternal(properties);
        getGraph().saveProperties((AccumuloElement) mutation.getElement(), properties, authorizations);

        if (mutation.getNewElementVisibility() != null) {
            getGraph().alterElementVisibility((AccumuloElement) mutation.getElement(), mutation.getNewElementVisibility());
        }
    }
}
