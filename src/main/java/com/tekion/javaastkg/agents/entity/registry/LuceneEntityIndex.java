package com.tekion.javaastkg.agents.entity.registry;

import com.tekion.javaastkg.dto.CodeEntityDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.store.Directory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LuceneEntityIndex {
    
    private final Directory directory;
    private final Analyzer analyzer;
    private final IndexWriter indexWriter;
    private final Map<String, CodeEntityDto> entityStore = new ConcurrentHashMap<>();
    
    private static final String[] SEARCH_FIELDS = {
        "name", "className", "methodName", "packageName", 
        "description", "annotations", "modifiers", "content"
    };
    
    public void indexEntity(CodeEntityDto entity) {
        try {
            Document doc = createDocument(entity);
            indexWriter.addDocument(doc);
            entityStore.put(entity.getId(), entity);
            
            if (indexWriter.hasUncommittedChanges()) {
                indexWriter.commit();
            }
        } catch (IOException e) {
            log.error("Failed to index entity: {}", entity.getName(), e);
        }
    }
    
    public void indexEntities(Collection<CodeEntityDto> entities) {
        log.info("LUCENE_INDEX: Starting to index {} entities with Lucene", entities.size());
        
        int indexed = 0;
        int failed = 0;
        
        for (CodeEntityDto entity : entities) {
            try {
                indexEntity(entity);
                indexed++;
                
                if (indexed <= 5) { // Log first 5 for debugging
                    log.info("LUCENE_INDEX: Indexed entity[{}] - type: {}, name: {}, id: {}", 
                        indexed, entity.getType(), entity.getName(), entity.getId());
                }
            } catch (Exception e) {
                failed++;
                log.info("LUCENE_INDEX: Failed to index entity: {} - error: {}", 
                    entity.getName(), e.getMessage());
            }
        }
        
        try {
            indexWriter.commit();
            log.info("LUCENE_INDEX: Successfully committed {} entities to index (indexed: {}, failed: {})", 
                entities.size(), indexed, failed);
        } catch (IOException e) {
            log.info("LUCENE_INDEX: FAILED to commit Lucene index - error: {}", e.getMessage());
            log.error("LUCENE_INDEX: Commit error stack trace:", e);
        }
    }
    
    public List<CodeEntityDto> search(String queryString, SearchType searchType, int maxResults) {
        log.info("LUCENE_SEARCH: Starting search - query: '{}', type: {}, maxResults: {}", 
            queryString, searchType, maxResults);
        
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            Query query = buildQuery(queryString, searchType);
            
            log.info("LUCENE_SEARCH: Built query: {} for searchType: {}", query.toString(), searchType);
            log.info("LUCENE_SEARCH: Index has {} documents", reader.numDocs());
            
            TopDocs topDocs = searcher.search(query, maxResults);
            log.info("LUCENE_SEARCH: Found {} hits out of {} total documents", 
                topDocs.scoreDocs.length, topDocs.totalHits.value);
            
            List<CodeEntityDto> results = Arrays.stream(topDocs.scoreDocs)
                .map(scoreDoc -> {
                    try {
                        Document doc = searcher.doc(scoreDoc.doc);
                        String entityId = doc.get("id");
                        CodeEntityDto entity = entityStore.get(entityId);
                        if (entity != null) {
                            entity.setScore(scoreDoc.score);
                        } else {
                            log.info("LUCENE_SEARCH: Entity not found in store for id: {}", entityId);
                        }
                        return entity;
                    } catch (IOException e) {
                        log.info("LUCENE_SEARCH: Failed to retrieve document - error: {}", e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
                
            log.info("LUCENE_SEARCH: Returning {} valid results for query: '{}'", results.size(), queryString);
            
            // Log first few results for debugging
            for (int i = 0; i < Math.min(3, results.size()); i++) {
                CodeEntityDto result = results.get(i);
                log.info("LUCENE_SEARCH: Result[{}] - name: {}, type: {}, score: {}", 
                    i+1, result.getName(), result.getType(), result.getScore());
            }
            
            return results;
                
        } catch (IOException | ParseException e) {
            log.info("LUCENE_SEARCH: FAILED search for query: '{}' - error: {} - message: {}", 
                queryString, e.getClass().getSimpleName(), e.getMessage());
            log.error("LUCENE_SEARCH: Full error stack trace:", e);
            return Collections.emptyList();
        }
    }
    
    public List<CodeEntityDto> fuzzySearch(String queryString, int maxResults) {
        log.info("LUCENE_FUZZY: Starting fuzzy search - query: '{}', maxResults: {}", queryString, maxResults);
        
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            
            BooleanQuery.Builder booleanQuery = new BooleanQuery.Builder();
            
            String[] tokens = queryString.toLowerCase().split("\\s+");
            log.info("LUCENE_FUZZY: Split query into {} tokens: {}", tokens.length, Arrays.toString(tokens));
            
            for (String token : tokens) {
                for (String field : SEARCH_FIELDS) {
                    Term term = new Term(field, token);
                    FuzzyQuery fuzzyQuery = new FuzzyQuery(term, 2);
                    booleanQuery.add(fuzzyQuery, BooleanClause.Occur.SHOULD);
                }
            }
            
            Query finalQuery = booleanQuery.build();
            log.info("LUCENE_FUZZY: Built fuzzy query with {} clauses", finalQuery.toString().split("SHOULD").length - 1);
            
            TopDocs topDocs = searcher.search(finalQuery, maxResults);
            log.info("LUCENE_FUZZY: Found {} fuzzy hits", topDocs.scoreDocs.length);
            
            List<CodeEntityDto> results = Arrays.stream(topDocs.scoreDocs)
                .map(scoreDoc -> {
                    try {
                        Document doc = searcher.doc(scoreDoc.doc);
                        String entityId = doc.get("id");
                        CodeEntityDto entity = entityStore.get(entityId);
                        if (entity != null) {
                            entity.setScore(scoreDoc.score);
                        }
                        return entity;
                    } catch (IOException e) {
                        log.info("LUCENE_FUZZY: Failed to retrieve document - error: {}", e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
                
            log.info("LUCENE_FUZZY: Returning {} fuzzy results", results.size());
            return results;
                
        } catch (IOException e) {
            log.info("LUCENE_FUZZY: FAILED fuzzy search for query: '{}' - error: {}", queryString, e.getMessage());
            log.error("LUCENE_FUZZY: Full error stack trace:", e);
            return Collections.emptyList();
        }
    }
    
    public Map<String, String> getHighlightedSnippets(String queryString, CodeEntityDto entity) {
        Map<String, String> highlights = new HashMap<>();
        
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            Query query = new MultiFieldQueryParser(SEARCH_FIELDS, analyzer).parse(queryString);
            
            QueryScorer scorer = new QueryScorer(query);
            Highlighter highlighter = new Highlighter(
                new SimpleHTMLFormatter("<mark>", "</mark>"),
                scorer
            );
            highlighter.setTextFragmenter(new SimpleFragmenter(150));
            
            Term idTerm = new Term("id", entity.getId());
            Query idQuery = new TermQuery(idTerm);
            TopDocs topDocs = searcher.search(idQuery, 1);
            
            if (topDocs.totalHits.value > 0) {
                Document doc = searcher.doc(topDocs.scoreDocs[0].doc);
                
                for (String field : SEARCH_FIELDS) {
                    String text = doc.get(field);
                    if (text != null && !text.isEmpty()) {
                        String highlighted = highlighter.getBestFragment(analyzer, field, text);
                        if (highlighted != null) {
                            highlights.put(field, highlighted);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to generate highlights", e);
        }
        
        return highlights;
    }
    
    private Query buildQuery(String queryString, SearchType searchType) throws ParseException {
        switch (searchType) {
            case EXACT:
                return new TermQuery(new Term("name", queryString.toLowerCase()));
                
            case PREFIX:
                return new PrefixQuery(new Term("name", queryString.toLowerCase()));
                
            case WILDCARD:
                return new WildcardQuery(new Term("name", queryString.toLowerCase()));
                
            case PHRASE:
                MultiFieldQueryParser parser = new MultiFieldQueryParser(SEARCH_FIELDS, analyzer);
                parser.setDefaultOperator(QueryParser.Operator.AND);
                return parser.parse("\"" + queryString + "\"");
                
            case FUZZY:
                return new FuzzyQuery(new Term("name", queryString.toLowerCase()), 2);
                
            default:
                MultiFieldQueryParser defaultParser = new MultiFieldQueryParser(SEARCH_FIELDS, analyzer);
                return defaultParser.parse(queryString);
        }
    }
    
    private Document createDocument(CodeEntityDto entity) {
        Document doc = new Document();
        
        doc.add(new StringField("id", entity.getId(), Field.Store.YES));
        doc.add(new TextField("name", entity.getName().toLowerCase(), Field.Store.YES));
        doc.add(new TextField("type", entity.getType().toString(), Field.Store.YES));
        
        if (entity.getClassName() != null) {
            doc.add(new TextField("className", entity.getClassName(), Field.Store.YES));
            doc.add(new StringField("classNameExact", entity.getClassName(), Field.Store.NO));
        }
        
        if (entity.getMethodName() != null) {
            doc.add(new TextField("methodName", entity.getMethodName(), Field.Store.YES));
        }
        
        if (entity.getPackageName() != null) {
            doc.add(new TextField("packageName", entity.getPackageName(), Field.Store.YES));
        }
        
        if (entity.getDescription() != null) {
            doc.add(new TextField("description", entity.getDescription(), Field.Store.YES));
        }
        
        if (entity.getModifiers() != null && !entity.getModifiers().isEmpty()) {
            doc.add(new TextField("modifiers", String.join(" ", entity.getModifiers()), Field.Store.YES));
        }
        
        if (entity.getAnnotations() != null && !entity.getAnnotations().isEmpty()) {
            doc.add(new TextField("annotations", String.join(" ", entity.getAnnotations()), Field.Store.YES));
        }
        
        StringBuilder content = new StringBuilder();
        content.append(entity.getName()).append(" ");
        if (entity.getClassName() != null) content.append(entity.getClassName()).append(" ");
        if (entity.getMethodName() != null) content.append(entity.getMethodName()).append(" ");
        if (entity.getDescription() != null) content.append(entity.getDescription());
        doc.add(new TextField("content", content.toString(), Field.Store.NO));
        
        return doc;
    }
    
    public enum SearchType {
        EXACT, PREFIX, WILDCARD, PHRASE, FUZZY, STANDARD
    }
}