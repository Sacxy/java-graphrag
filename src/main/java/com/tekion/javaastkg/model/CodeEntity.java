package com.tekion.javaastkg.model;

import com.tekion.javaastkg.dto.CodeEntityDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeEntity {
    
    private String id;
    private String name;
    private CodeEntityDto.EntityType type;
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
    
    // Additional metadata
    private String fullName;
    private String signature;
    private LocalDateTime lastModified;
    private LocalDateTime lastAccessed;
    private Integer usageCount;
    private List<String> nameTokens;
    private String namePattern;
    private List<Float> embedding;
    
    public String getDisplayName() {
        if (type == CodeEntityDto.EntityType.METHOD && className != null) {
            return className + "." + name;
        }
        return name;
    }
    
    public String getFullName() {
        if (fullName != null) {
            return fullName;
        }
        
        StringBuilder builder = new StringBuilder();
        
        if (packageName != null && !packageName.isEmpty()) {
            builder.append(packageName).append(".");
        }
        
        if (className != null && !className.isEmpty() && type != CodeEntityDto.EntityType.CLASS) {
            builder.append(className).append(".");
        }
        
        builder.append(name);
        
        return builder.toString();
    }
    
    public String getSignature() {
        if (signature != null) {
            return signature;
        }
        
        if (type == CodeEntityDto.EntityType.METHOD && methodName != null) {
            StringBuilder sig = new StringBuilder();
            if (className != null) {
                sig.append(className).append(".");
            }
            sig.append(methodName);
            if (parameters != null && !parameters.isEmpty()) {
                sig.append("(");
                sig.append(String.join(", ", parameters));
                sig.append(")");
            }
            if (returnType != null) {
                sig.append(": ").append(returnType);
            }
            return sig.toString();
        }
        
        return getFullName();
    }
    
    public boolean isMethod() {
        return type == CodeEntityDto.EntityType.METHOD;
    }
    
    public boolean isClass() {
        return type == CodeEntityDto.EntityType.CLASS;
    }
    
    public boolean isPackage() {
        return type == CodeEntityDto.EntityType.PACKAGE;
    }
}