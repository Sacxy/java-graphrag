package com.tekion.javaastkg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JavaAstkgApplication {

    public static void main(String[] args) {
        // Google API key is now set dynamically in the controller before LLM execution
        // This avoids static initialization timing issues with ADK
        SpringApplication.run(JavaAstkgApplication.class, args);
    }

}
