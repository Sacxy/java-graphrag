package com.tekion.javaastkg.adk.config;

import com.google.adk.agents.BaseAgent;
import com.tekion.javaastkg.adk.agents.IntelligentOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 🔧 ADK Configuration
 * 
 * Spring configuration for Google ADK agents and tools.
 * Provides beans for agent integration with the Spring application.
 */
@Configuration("newAdkConfiguration")
@Slf4j
public class AdkConfiguration {
    
    @Autowired
    @Qualifier("adkIntelligentOrchestrator")
    private IntelligentOrchestrator intelligentOrchestrator;
    
    /**
     * Configure the main orchestrator agent
     */
    @Bean("newIntelligentOrchestratorAgent")
    public BaseAgent newIntelligentOrchestratorAgent() {
        log.info("🚀 Configuring ADK Intelligent Orchestrator Agent");
        return intelligentOrchestrator.getAgent();
    }
}