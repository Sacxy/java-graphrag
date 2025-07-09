package com.tekion.javaastkg.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeEntityDto {
    
    private String id;
    private String name;
    private EntityType type;
    private String packageName;
    private String className;
    private String methodName;
    private String visibility;
    private Set<String> modifiers;
    private Set<String> annotations;
    private String description;
    private Integer lineNumber;
    private String filePath;
    private List<String> parameters;
    private String returnType;
    private Integer complexity;
    
    // Search-specific fields
    private Float score;
    
    public enum EntityType {
        CLASS,
        METHOD,
        PACKAGE,
        FIELD,
        ANNOTATION,
        INTERFACE,
        ENUM,
        VARIABLE
    }
    
    public String getDisplayName() {
        if (type == EntityType.METHOD && className != null) {
            return className + "." + name;
        }
        return name;
    }
    
    public String getFullName() {
        StringBuilder fullName = new StringBuilder();
        
        if (packageName != null && !packageName.isEmpty()) {
            fullName.append(packageName).append(".");
        }
        
        if (className != null && !className.isEmpty() && type != EntityType.CLASS) {
            fullName.append(className).append(".");
        }
        
        fullName.append(name);
        
        return fullName.toString();
    }
    
    public boolean isMethod() {
        return type == EntityType.METHOD;
    }
    
    public boolean isClass() {
        return type == EntityType.CLASS;
    }
    
    public boolean isPackage() {
        return type == EntityType.PACKAGE;
    }
}