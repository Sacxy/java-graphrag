package com.tekion.javaastkg.model;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Root model representing the complete AST data from Spoon service.
 * Contains collections of classes, methods, and API endpoints.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpoonAST {
    private List<ClassInfo> classes = new ArrayList<>();
    private List<MethodInfo> methods = new ArrayList<>();
    private List<ApiEndpoint> apiEndpoints = new ArrayList<>();
    private Map<String, String> docs = new HashMap<>(); // filename -> documentation content

    /**
     * Class information extracted from the AST
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ClassInfo {
        private String name;
        private String fullName;
        private String filePath;
        private String type;
        private List<String> annotations = new ArrayList<>();
        private boolean isAbstract;
        private boolean isInterface;
        private String implementsClass;
        private String extendsClass;
        private String packageName;
        private int startLine;
        private int endLine;
        private List<String> dependencies = new ArrayList<>();
    }

    /**
     * Method information with detailed metadata
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MethodInfo {
        private String name;
        private String fullSignature;
        private String className;
        private String modifier;
        private boolean isStatic;
        private String returnType;
        private List<String> arguments = new ArrayList<>();
        private List<String> annotations = new ArrayList<>();
        private int startLine;
        private int endLine;
        private List<String> callsTo = new ArrayList<>();
        private List<String> calledFrom = new ArrayList<>();
        private List<String> exceptions = new ArrayList<>();
        private List<String> comments = new ArrayList<>();
    }

    /**
     * API endpoint information for REST controllers
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApiEndpoint {
        private String path;
        private String method;
        private String controllerClass;
        private String handlerMethod;
    }
}
