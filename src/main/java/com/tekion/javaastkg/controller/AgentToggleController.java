package com.tekion.javaastkg.controller;

import com.tekion.javaastkg.config.AgentToggleConfig;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
@Slf4j
public class AgentToggleController {
    
    private final AgentToggleConfig agentToggleConfig;
    
    @GetMapping("/status")
    public ResponseEntity<AgentStatusResponse> getAgentStatus() {
        AgentStatusResponse response = AgentStatusResponse.builder()
            .patternMatching(agentToggleConfig.isPatternMatching())
            .fuzzyMatching(agentToggleConfig.isFuzzyMatching())
            .semanticMatching(agentToggleConfig.isSemanticMatching())
            .luceneMatching(agentToggleConfig.isLuceneMatching())
            .patternConfig(agentToggleConfig.getPattern())
            .fuzzyConfig(agentToggleConfig.getFuzzy())
            .semanticConfig(agentToggleConfig.getSemantic())
            .luceneConfig(agentToggleConfig.getLucene())
            .enabledAgentsStatus(agentToggleConfig.getEnabledAgentsStatus())
            .anyAgentEnabled(agentToggleConfig.isAnyAgentEnabled())
            .build();
            
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/toggle/{agentType}")
    public ResponseEntity<String> toggleAgent(@PathVariable String agentType, @RequestParam boolean enabled) {
        log.info("Toggling agent {} to {}", agentType, enabled);
        
        switch (agentType.toLowerCase()) {
            case "pattern":
                agentToggleConfig.setPatternMatching(enabled);
                agentToggleConfig.getPattern().setEnabled(enabled);
                break;
            case "fuzzy":
                agentToggleConfig.setFuzzyMatching(enabled);
                agentToggleConfig.getFuzzy().setEnabled(enabled);
                break;
            case "semantic":
                agentToggleConfig.setSemanticMatching(enabled);
                agentToggleConfig.getSemantic().setEnabled(enabled);
                break;
            case "lucene":
                agentToggleConfig.setLuceneMatching(enabled);
                agentToggleConfig.getLucene().setEnabled(enabled);
                break;
            default:
                return ResponseEntity.badRequest()
                    .body("Unknown agent type: " + agentType + ". Available: pattern, fuzzy, semantic, lucene");
        }
        
        String status = enabled ? "enabled" : "disabled";
        return ResponseEntity.ok(agentType + " agent " + status + " successfully");
    }
    
    @PostMapping("/enable-only/{agentType}")
    public ResponseEntity<String> enableOnlyAgent(@PathVariable String agentType) {
        log.info("Enabling only {} agent", agentType);
        
        agentToggleConfig.enableOnly(agentType);
        return ResponseEntity.ok("Only " + agentType + " agent is now enabled");
    }
    
    @PostMapping("/enable-all")
    public ResponseEntity<String> enableAllAgents() {
        log.info("Enabling all agents");
        
        agentToggleConfig.enableAll();
        return ResponseEntity.ok("All agents enabled successfully");
    }
    
    @PostMapping("/disable-all")
    public ResponseEntity<String> disableAllAgents() {
        log.info("Disabling all agents");
        
        agentToggleConfig.disableAll();
        return ResponseEntity.ok("All agents disabled successfully");
    }
    
    @PostMapping("/priority/{agentType}")
    public ResponseEntity<String> setAgentPriority(@PathVariable String agentType, @RequestParam int priority) {
        log.info("Setting {} agent priority to {}", agentType, priority);
        
        switch (agentType.toLowerCase()) {
            case "pattern":
                agentToggleConfig.getPattern().setPriority(priority);
                break;
            case "fuzzy":
                agentToggleConfig.getFuzzy().setPriority(priority);
                break;
            case "semantic":
                agentToggleConfig.getSemantic().setPriority(priority);
                break;
            case "lucene":
                agentToggleConfig.getLucene().setPriority(priority);
                break;
            default:
                return ResponseEntity.badRequest()
                    .body("Unknown agent type: " + agentType);
        }
        
        return ResponseEntity.ok(agentType + " agent priority set to " + priority);
    }
    
    @PostMapping("/configuration")
    public ResponseEntity<String> updateConfiguration(@RequestBody Map<String, Object> config) {
        log.info("Updating agent configuration: {}", config);
        
        try {
            // Update master toggles
            if (config.containsKey("patternMatching")) {
                agentToggleConfig.setPatternMatching((Boolean) config.get("patternMatching"));
            }
            if (config.containsKey("fuzzyMatching")) {
                agentToggleConfig.setFuzzyMatching((Boolean) config.get("fuzzyMatching"));
            }
            if (config.containsKey("semanticMatching")) {
                agentToggleConfig.setSemanticMatching((Boolean) config.get("semanticMatching"));
            }
            if (config.containsKey("luceneMatching")) {
                agentToggleConfig.setLuceneMatching((Boolean) config.get("luceneMatching"));
            }
            
            return ResponseEntity.ok("Configuration updated successfully");
        } catch (Exception e) {
            log.error("Failed to update configuration", e);
            return ResponseEntity.badRequest().body("Failed to update configuration: " + e.getMessage());
        }
    }
    
    @GetMapping("/test-scenarios")
    public ResponseEntity<TestScenariosResponse> getTestScenarios() {
        TestScenariosResponse scenarios = TestScenariosResponse.builder()
            .scenario1("Only Pattern Matching")
            .scenario1Description("Test exact matches, wildcards, and pattern-based searches")
            .scenario1Endpoint("POST /api/agents/enable-only/pattern")
            
            .scenario2("Only Fuzzy Matching")
            .scenario2Description("Test typo tolerance, edit distance, and phonetic matching")
            .scenario2Endpoint("POST /api/agents/enable-only/fuzzy")
            
            .scenario3("Only Semantic Matching")
            .scenario3Description("Test embedding similarity and domain-aware searches")
            .scenario3Endpoint("POST /api/agents/enable-only/semantic")
            
            .scenario4("Only Lucene Matching")
            .scenario4Description("Test boolean queries, phrase searches, and advanced syntax")
            .scenario4Endpoint("POST /api/agents/enable-only/lucene")
            
            .scenario5("All Agents")
            .scenario5Description("Test combined results from all agents with priority-based ranking")
            .scenario5Endpoint("POST /api/agents/enable-all")
            
            .exampleQueries(new String[]{
                "UserService",           // Test exact pattern matching
                "user service",          // Test compound matching
                "userservic",           // Test fuzzy matching
                "get user information", // Test semantic matching
                "User* AND Service",    // Test Lucene boolean queries
                "\"create user\"",      // Test phrase matching
                "*Manager",             // Test wildcard matching
                "getUserInfo"           // Test CamelCase matching
            })
            .build();
            
        return ResponseEntity.ok(scenarios);
    }
    
    @Data
    @lombok.Builder
    public static class AgentStatusResponse {
        private boolean patternMatching;
        private boolean fuzzyMatching;
        private boolean semanticMatching;
        private boolean luceneMatching;
        private AgentToggleConfig.PatternMatchingConfig patternConfig;
        private AgentToggleConfig.FuzzyMatchingConfig fuzzyConfig;
        private AgentToggleConfig.SemanticMatchingConfig semanticConfig;
        private AgentToggleConfig.LuceneMatchingConfig luceneConfig;
        private String enabledAgentsStatus;
        private boolean anyAgentEnabled;
    }
    
    @Data
    @lombok.Builder
    public static class TestScenariosResponse {
        private String scenario1;
        private String scenario1Description;
        private String scenario1Endpoint;
        
        private String scenario2;
        private String scenario2Description;
        private String scenario2Endpoint;
        
        private String scenario3;
        private String scenario3Description;
        private String scenario3Endpoint;
        
        private String scenario4;
        private String scenario4Description;
        private String scenario4Endpoint;
        
        private String scenario5;
        private String scenario5Description;
        private String scenario5Endpoint;
        
        private String[] exampleQueries;
    }
}