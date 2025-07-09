package com.tekion.javaastkg.config;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class LuceneConfig {
    
    @Bean
    public Directory luceneDirectory() {
        return new ByteBuffersDirectory();
    }
    
    @Bean
    public Analyzer luceneAnalyzer() {
        Map<String, Analyzer> fieldAnalyzers = new HashMap<>();
        fieldAnalyzers.put("className", new KeywordAnalyzer());
        fieldAnalyzers.put("methodName", new KeywordAnalyzer());
        fieldAnalyzers.put("variableName", new KeywordAnalyzer());
        fieldAnalyzers.put("packageName", new KeywordAnalyzer());
        
        return new PerFieldAnalyzerWrapper(new StandardAnalyzer(), fieldAnalyzers);
    }
    
    @Bean
    public IndexWriter luceneIndexWriter(Directory directory, Analyzer analyzer) throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        config.setCommitOnClose(true);
        
        IndexWriter writer = new IndexWriter(directory, config);
        log.info("Lucene IndexWriter initialized with in-memory directory");
        return writer;
    }
}