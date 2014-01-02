package com.altamiracorp.securegraph.accumulo.serializer;

import org.apache.accumulo.core.data.Value;

public interface ValueSerializer {
    Value objectToValue(Object value);

    Object valueToObject(Value value);
}
