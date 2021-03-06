package org.securegraph;

import java.util.EnumSet;

public enum FetchHint {
    PROPERTIES,
    PROPERTY_METADATA,
    IN_EDGE_REFS,
    OUT_EDGE_REFS;

    public static final EnumSet<FetchHint> NONE = EnumSet.noneOf(FetchHint.class);
    public static final EnumSet<FetchHint> ALL = EnumSet.allOf(FetchHint.class);
    public static final EnumSet<FetchHint> EDGE_REFS = EnumSet.of(IN_EDGE_REFS, OUT_EDGE_REFS);
}
