package com.tekion.javaastkg.model;

public enum EdgeType {
    // Structural hierarchy
    CONTAINS,
    CONTAINS_INNER_CLASS,
    CONTAINS_LAMBDA,
    
    // Inheritance relationships
    EXTENDS,
    IMPLEMENTS,
    
    // Method relationships
    CALLS,
    OVERRIDES,
    METHOD_REFERENCE,
    
    // Field and parameter relationships
    HAS_PARAMETER,
    HAS_FIELD,
    USES_FIELD,
    RETURNS,
    THROWS,
    
    // Annotation relationships
    ANNOTATED_BY,
    
    // Type relationships
    DEPENDS_ON,
    PARAMETERIZES,
    HAS_TYPE_ARGUMENT,
    
    // Variable relationships
    CAPTURES,
    DECLARES,
    ACCESSES,
    
    // Advanced relationships
    CIRCULAR_DEPENDENCY,
    INSTANTIATES
}