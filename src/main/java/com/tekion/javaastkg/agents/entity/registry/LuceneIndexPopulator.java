package com.tekion.javaastkg.agents.entity.registry;

import com.tekion.javaastkg.dto.CodeEntityDto;
import com.tekion.javaastkg.agents.entity.models.ClassEntity;
import com.tekion.javaastkg.agents.entity.models.MethodEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LuceneIndexPopulator {
    
    private final CodebaseEntityRegistry registry;
    private final LuceneEntityIndex luceneIndex;
    
    @EventListener(ApplicationReadyEvent.class)
    public void populateLuceneIndex() {
        log.info("LUCENE_POPULATION: Starting Lucene index population from CodebaseEntityRegistry");
        
        try {
            // Check registry state first
            log.info("LUCENE_POPULATION: Checking CodebaseEntityRegistry state - initialized: {}", registry.isInitialized());
            log.info("LUCENE_POPULATION: Registry counts - classes: {}, methods: {}", 
                registry.getClassCount(), registry.getMethodCount());
            
            List<CodeEntityDto> allEntities = new ArrayList<>();
            
            // Get and process classes
            var allClasses = registry.getAllClasses();
            log.info("LUCENE_POPULATION: Retrieved {} class entities from registry", allClasses.size());
            
            int classCount = 0;
            for (var classEntity : allClasses) {
                try {
                    CodeEntityDto dto = convertClassToDto(classEntity);
                    allEntities.add(dto);
                    classCount++;
                    
                    if (classCount <= 5) { // Log first 5 classes for debugging
                        log.info("LUCENE_POPULATION: Class[{}] - id: {}, name: {}, package: {}, modifiers: {}", 
                            classCount, dto.getId(), dto.getName(), dto.getPackageName(), dto.getModifiers());
                    }
                } catch (Exception e) {
                    log.info("LUCENE_POPULATION: Failed to convert class entity: {} - error: {}", 
                        classEntity.getName(), e.getMessage());
                }
            }
            log.info("LUCENE_POPULATION: Successfully processed {} class entities", classCount);
            
            // Get and process methods
            var allMethods = registry.getAllMethods();
            log.info("LUCENE_POPULATION: Retrieved {} method entities from registry", allMethods.size());
            
            int methodCount = 0;
            for (var methodEntity : allMethods) {
                try {
                    CodeEntityDto dto = convertMethodToDto(methodEntity);
                    allEntities.add(dto);
                    methodCount++;
                    
                    if (methodCount <= 5) { // Log first 5 methods for debugging
                        log.info("LUCENE_POPULATION: Method[{}] - id: {}, name: {}, class: {}, params: {}, return: {}", 
                            methodCount, dto.getId(), dto.getName(), dto.getClassName(), 
                            dto.getParameters(), dto.getReturnType());
                    }
                } catch (Exception e) {
                    log.info("LUCENE_POPULATION: Failed to convert method entity: {} - error: {}", 
                        methodEntity.getName(), e.getMessage());
                }
            }
            log.info("LUCENE_POPULATION: Successfully processed {} method entities", methodCount);
            
            log.info("LUCENE_POPULATION: Total entities to index: {}", allEntities.size());
            
            if (!allEntities.isEmpty()) {
                luceneIndex.indexEntities(allEntities);
                log.info("LUCENE_POPULATION: Successfully populated Lucene index with {} entities (classes: {}, methods: {})", 
                    allEntities.size(), classCount, methodCount);
            } else {
                log.info("LUCENE_POPULATION: WARNING - No entities found to index in Lucene! Registry may be empty or not initialized");
                log.info("LUCENE_POPULATION: Registry debug - isInitialized: {}, classCount: {}, methodCount: {}", 
                    registry.isInitialized(), registry.getClassCount(), registry.getMethodCount());
            }
            
        } catch (Exception e) {
            log.info("LUCENE_POPULATION: FAILED to populate Lucene index - error: {} - message: {}", 
                e.getClass().getSimpleName(), e.getMessage());
            log.error("LUCENE_POPULATION: Full stack trace:", e);
        }
    }
    
    private CodeEntityDto convertClassToDto(ClassEntity entity) {
        return CodeEntityDto.builder()
            .id(entity.getId())
            .name(entity.getName())
            .type(CodeEntityDto.EntityType.CLASS)
            .packageName(entity.getPackageName())
            .className(entity.getName())
            .modifiers(entity.getModifiers())
            .annotations(entity.getAnnotations())
            .filePath(entity.getFilePath())
            .description(entity.getDescription())
            .build();
    }
    
    private CodeEntityDto convertMethodToDto(MethodEntity entity) {
        return CodeEntityDto.builder()
            .id(entity.getId())
            .name(entity.getName())
            .type(CodeEntityDto.EntityType.METHOD)
            .packageName(entity.getPackageName())
            .className(entity.getClassName())
            .methodName(entity.getName())
            .modifiers(entity.getModifiers())
            .annotations(entity.getAnnotations())
            .filePath(entity.getFilePath())
            .parameters(entity.getParameterNames())
            .returnType(entity.getReturnType())
            .description(entity.getDescription())
            .build();
    }
}