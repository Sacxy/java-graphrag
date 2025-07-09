package com.tekion.javaastkg.agents.entity.extraction;

import com.tekion.javaastkg.agents.entity.models.EntityMatch;
import com.tekion.javaastkg.agents.entity.models.QueryContext;
import com.tekion.javaastkg.agents.entity.registry.CodebaseEntityRegistry;
import com.tekion.javaastkg.agents.entity.registry.SemanticEntityIndex;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Fuzzy Matching Agent - Handles typos, abbreviations, and similar-sounding terms.
 * Implements Factor 10 (Small Focused Agent) and Factor 12 (Stateless Reducer).
 * 
 * Applied 12-Factor Principles:
 * - Factor 1: Structured outputs (EntityMatch objects)
 * - Factor 4: Tools as structured outputs
 * - Factor 9: Compact errors into context (handles typos gracefully)
 * - Factor 10: Small focused agent (4-7 steps: fuzzy, phonetic, abbreviation, keyboard)
 * - Factor 12: Stateless pure function
 */
@Component
@Slf4j
public class FuzzyMatchingAgent implements ExtractionAgent {
    
    private final CodebaseEntityRegistry registry;
    private final SemanticEntityIndex semanticIndex;
    
    // Configuration
    private static final int MAX_EDIT_DISTANCE = 2;
    private static final double MIN_FUZZY_CONFIDENCE = 0.4;
    private static final int MAX_FUZZY_RESULTS = 20;
    
    // Keyboard layout for typo detection (QWERTY layout)
    private static final Map<Character, String> KEYBOARD_NEIGHBORS = createKeyboardNeighbors();

    /**
     * Creates a complete QWERTY keyboard neighbor mapping for typo detection
     */
    private static Map<Character, String> createKeyboardNeighbors() {
        Map<Character, String> neighbors = new HashMap<>();

        // Numbers row: 1 2 3 4 5 6 7 8 9 0
        neighbors.put('1', "2q");
        neighbors.put('2', "13qw");
        neighbors.put('3', "24we");
        neighbors.put('4', "35er");
        neighbors.put('5', "46rt");
        neighbors.put('6', "57ty");
        neighbors.put('7', "68yu");
        neighbors.put('8', "79ui");
        neighbors.put('9', "80io");
        neighbors.put('0', "9op");

        // Top row: Q W E R T Y U I O P
        neighbors.put('q', "12wa");
        neighbors.put('w', "23qeas");
        neighbors.put('e', "34wrds");
        neighbors.put('r', "45etdf");
        neighbors.put('t', "56ryfg");
        neighbors.put('y', "67tugh");
        neighbors.put('u', "78yihj");
        neighbors.put('i', "89uojk");
        neighbors.put('o', "90ipkl");
        neighbors.put('p', "0ol");

        // Middle row: A S D F G H J K L
        neighbors.put('a', "qwsz");
        neighbors.put('s', "awedxz");
        neighbors.put('d', "serfcx");
        neighbors.put('f', "drtgvc");
        neighbors.put('g', "ftyhbv");
        neighbors.put('h', "gyujnb");
        neighbors.put('j', "huikmn");
        neighbors.put('k', "jiolm");
        neighbors.put('l', "kop");

        // Bottom row: Z X C V B N M
        neighbors.put('z', "asx");
        neighbors.put('x', "zsdc");
        neighbors.put('c', "xdfv");
        neighbors.put('v', "cfgb");
        neighbors.put('b', "vghn");
        neighbors.put('n', "bhjm");
        neighbors.put('m', "njk");

        // Special characters commonly used in Java identifiers
        neighbors.put('_', "");
        neighbors.put('$', "");

        return neighbors;
    }

    public FuzzyMatchingAgent(CodebaseEntityRegistry registry, SemanticEntityIndex semanticIndex) {
        this.registry = registry;
        this.semanticIndex = semanticIndex;
    }
    
    /**
     * Factor 12: Pure function for fuzzy entity extraction
     * Factor 9: Gracefully handles input errors (typos, misspellings)
     */
    @Override
    public List<EntityMatch> extract(QueryContext context) {
        log.debug("Starting fuzzy matching for query: {}", context.getOriginalQuery());
        
        List<EntityMatch> allMatches = new ArrayList<>();
        
        // Step 1: Edit distance fuzzy matching
        allMatches.addAll(findEditDistanceMatches(context));
        
        // Step 2: Phonetic matching (sounds-like)
        allMatches.addAll(findPhoneticMatches(context));
        
        // Step 3: Abbreviation expansion
        allMatches.addAll(findAbbreviationMatches(context));
        
        // Step 4: Keyboard typo correction
        allMatches.addAll(findKeyboardTypoMatches(context));
        
        // Step 5: Partial substring matching
        allMatches.addAll(findPartialMatches(context));
        
        // Step 6: Common misspelling corrections
        allMatches.addAll(findMisspellingMatches(context));
        
        // Step 7: Filter and sort results
        List<EntityMatch> filteredMatches = filterAndSortMatches(allMatches);
        
        log.debug("Fuzzy matching completed. Found {} matches", filteredMatches.size());
        return filteredMatches;
    }
    
    /**
     * Step 1: Find matches using edit distance (BK-tree)
     */
    private List<EntityMatch> findEditDistanceMatches(QueryContext context) {
        List<EntityMatch> matches = new ArrayList<>();

        for (QueryContext.Token token : context.getIdentifierTokens()) {
            String term = token.getValue();

            // Input validation
            if (term == null || term.length() < 3) continue;

            try {
                List<EntityMatch> fuzzyMatches = registry.findSimilar(term, MAX_EDIT_DISTANCE);

                if (fuzzyMatches != null) {
                    for (EntityMatch match : fuzzyMatches) {
                        if (match != null && match.getEntityName() != null) {
                            // Calculate more precise confidence based on edit distance
                            double confidence = calculateEditDistanceConfidence(term, match.getEntityName());

                            if (confidence >= MIN_FUZZY_CONFIDENCE) {
                                match.setSource("FuzzyMatchingAgent");
                                match.setMatchType(EntityMatch.MatchType.FUZZY);
                                match.setMatchReason("edit distance fuzzy match for: " + term);
                                match.setConfidence(confidence);
                                matches.add(match);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error finding edit distance matches for term: {}", term, e);
            }
        }

        return matches;
    }
    
    /**
     * Step 2: Find phonetic matches (sounds-like matching)
     */
    private List<EntityMatch> findPhoneticMatches(QueryContext context) {
        List<EntityMatch> matches = new ArrayList<>();

        for (QueryContext.Token token : context.getIdentifierTokens()) {
            String term = token.getValue();

            if (term == null || term.length() < 3) continue;

            try {
                Set<String> phoneticMatches = semanticIndex.findPhoneticMatches(term);

                if (phoneticMatches != null) {
                    for (String phoneticMatch : phoneticMatches) {
                        if (phoneticMatch != null && !phoneticMatch.equals(term)) {
                            List<EntityMatch> entityMatches = registry.findExactMatches(phoneticMatch);

                            if (entityMatches != null) {
                                for (EntityMatch match : entityMatches) {
                                    if (match != null) {
                                        // Calculate phonetic confidence based on similarity
                                        double confidence = calculatePhoneticConfidence(term, phoneticMatch);

                                        match.setSource("FuzzyMatchingAgent");
                                        match.setMatchType(EntityMatch.MatchType.PHONETIC);
                                        match.setMatchReason("phonetic match for: " + term + " -> " + phoneticMatch);
                                        match.setConfidence(confidence);
                                        matches.add(match);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error finding phonetic matches for term: {}", term, e);
            }
        }

        return matches;
    }
    
    /**
     * Step 3: Find abbreviation expansion matches
     */
    private List<EntityMatch> findAbbreviationMatches(QueryContext context) {
        List<EntityMatch> matches = new ArrayList<>();

        for (QueryContext.Token token : context.getTokens()) {
            String term = token.getValue();

            // Input validation and length check for abbreviations
            if (term == null || term.length() < 2 || term.length() > 6) continue;

            try {
                Set<String> expansions = semanticIndex.expandAbbreviation(term);

                if (expansions != null) {
                    for (String expansion : expansions) {
                        if (expansion != null && !expansion.equals(term)) {
                            // Find entities that contain the expansion
                            List<EntityMatch> expansionMatches = findEntitiesContaining(expansion);

                            if (expansionMatches != null) {
                                for (EntityMatch match : expansionMatches) {
                                    if (match != null) {
                                        // Calculate abbreviation confidence based on expansion quality
                                        double confidence = calculateAbbreviationConfidence(term, expansion, match.getEntityName());

                                        match.setSource("FuzzyMatchingAgent");
                                        match.setMatchType(EntityMatch.MatchType.ABBREVIATION);
                                        match.setMatchReason("abbreviation expansion: " + term + " -> " + expansion);
                                        match.setConfidence(confidence);
                                        matches.add(match);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error finding abbreviation matches for term: {}", term, e);
            }
        }

        return matches;
    }
    
    /**
     * Step 4: Find keyboard typo corrections
     */
    private List<EntityMatch> findKeyboardTypoMatches(QueryContext context) {
        List<EntityMatch> matches = new ArrayList<>();

        for (QueryContext.Token token : context.getIdentifierTokens()) {
            String term = token.getValue();

            if (term == null || term.length() < 3) continue;

            try {
                // Generate potential typo corrections
                Set<String> typoCorrections = generateTypoCorrections(term);

                // Limit the number of corrections to avoid performance issues
                typoCorrections = typoCorrections.stream()
                        .limit(50) // Reasonable limit
                        .collect(Collectors.toSet());

                for (String correction : typoCorrections) {
                    if (correction != null && !correction.equals(term)) {
                        List<EntityMatch> correctionMatches = registry.findExactMatches(correction);

                        if (correctionMatches != null) {
                            for (EntityMatch match : correctionMatches) {
                                if (match != null) {
                                    // Calculate typo correction confidence
                                    double confidence = calculateTypoConfidence(term, correction);

                                    match.setSource("FuzzyMatchingAgent");
                                    match.setMatchType(EntityMatch.MatchType.FUZZY);
                                    match.setMatchReason("keyboard typo correction: " + term + " -> " + correction);
                                    match.setConfidence(confidence);
                                    matches.add(match);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error finding keyboard typo matches for term: {}", term, e);
            }
        }

        return matches;
    }
    
    /**
     * Step 5: Find partial substring matches
     */
    private List<EntityMatch> findPartialMatches(QueryContext context) {
        List<EntityMatch> matches = new ArrayList<>();

        for (QueryContext.Token token : context.getIdentifierTokens()) {
            String term = token.getValue();

            // Input validation - skip very short terms
            if (term == null || term.length() < 4) continue;

            try {
                // Find entities that contain this term as substring
                List<EntityMatch> partialMatches = findEntitiesContaining(term);

                if (partialMatches != null) {
                    for (EntityMatch match : partialMatches) {
                        if (match != null && match.getEntityName() != null) {
                            // Calculate confidence based on how much of the entity name is matched
                            double confidence = calculatePartialMatchConfidence(term, match.getEntityName());

                            if (confidence >= MIN_FUZZY_CONFIDENCE) {
                                match.setSource("FuzzyMatchingAgent");
                                match.setMatchType(EntityMatch.MatchType.FUZZY);
                                match.setMatchReason("partial substring match for: " + term);
                                match.setConfidence(confidence);
                                matches.add(match);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error finding partial matches for term: {}", term, e);
            }
        }

        return matches;
    }
    
    /**
     * Step 6: Find common misspelling corrections
     */
    private List<EntityMatch> findMisspellingMatches(QueryContext context) {
        List<EntityMatch> matches = new ArrayList<>();

        // Common programming misspellings - expanded list
        Map<String, String> commonMisspellings = Map.of(
            "recieve", "receive",
            "seperator", "separator",
            "occured", "occurred",
            "persistance", "persistence",
            "accessable", "accessible",
            "dependancy", "dependency",
            "existance", "existence",
            "referance", "reference",
            "perfomance", "performance",
            "managment", "management"
        );

        for (QueryContext.Token token : context.getTokens()) {
            if (token == null || token.getValue() == null) continue;

            String term = token.getValue().toLowerCase();
            String correction = commonMisspellings.get(term);

            if (correction != null) {
                try {
                    List<EntityMatch> correctionMatches = findEntitiesContaining(correction);

                    if (correctionMatches != null) {
                        for (EntityMatch match : correctionMatches) {
                            if (match != null) {
                                // High confidence for known misspelling corrections
                                double confidence = calculateMisspellingConfidence(term, correction, match.getEntityName());

                                match.setSource("FuzzyMatchingAgent");
                                match.setMatchType(EntityMatch.MatchType.FUZZY);
                                match.setMatchReason("misspelling correction: " + term + " -> " + correction);
                                match.setConfidence(confidence);
                                matches.add(match);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error finding misspelling matches for term: {}", term, e);
                }
            }
        }

        return matches;
    }
    
    /**
     * Step 7: Filter and sort matches by confidence
     */
    private List<EntityMatch> filterAndSortMatches(List<EntityMatch> matches) {
        if (matches == null || matches.isEmpty()) {
            return new ArrayList<>();
        }

        return matches.stream()
                .filter(match -> match != null &&
                               match.getConfidence() >= MIN_FUZZY_CONFIDENCE &&
                               match.getEntityId() != null)
                .collect(Collectors.groupingBy(
                    this::createMatchKey, // Use composite key for better deduplication
                    Collectors.reducing(null, this::selectBestMatch)
                ))
                .values()
                .stream()
                .filter(Objects::nonNull)
                .sorted((a, b) -> {
                    // Primary sort: confidence (descending)
                    int confidenceCompare = Double.compare(b.getConfidence(), a.getConfidence());
                    if (confidenceCompare != 0) return confidenceCompare;

                    // Secondary sort: match type priority
                    return Integer.compare(getMatchTypePriority(a.getMatchType()),
                                         getMatchTypePriority(b.getMatchType()));
                })
                .limit(MAX_FUZZY_RESULTS)
                .collect(Collectors.toList());
    }

    /**
     * Creates a composite key for match deduplication
     */
    private String createMatchKey(EntityMatch match) {
        return match.getEntityId() + "|" + match.getMatchType();
    }

    /**
     * Selects the best match between two candidates
     */
    private EntityMatch selectBestMatch(EntityMatch existing, EntityMatch candidate) {
        if (existing == null) return candidate;
        if (candidate == null) return existing;

        // Prefer higher confidence
        if (candidate.getConfidence() > existing.getConfidence()) {
            return candidate;
        } else if (existing.getConfidence() > candidate.getConfidence()) {
            return existing;
        }

        // If confidence is equal, prefer better match types
        int existingPriority = getMatchTypePriority(existing.getMatchType());
        int candidatePriority = getMatchTypePriority(candidate.getMatchType());

        return candidatePriority < existingPriority ? candidate : existing;
    }

    /**
     * Gets priority for match types (lower number = higher priority)
     */
    private int getMatchTypePriority(EntityMatch.MatchType matchType) {
        if (matchType == null) return 100;

        return switch (matchType) {
            case EXACT -> 1;
            case PREFIX, SUFFIX -> 2;
            case ABBREVIATION -> 3;
            case PHONETIC -> 4;
            case FUZZY -> 5;
            case SEMANTIC -> 6;
            default -> 10;
        };
    }
    
    // ========== UTILITY METHODS ==========

    /**
     * Calculates confidence for phonetic matches
     */
    private double calculatePhoneticConfidence(String original, String phoneticMatch) {
        if (original == null || phoneticMatch == null) return 0.0;
        if (original.equals(phoneticMatch)) return 1.0;

        // Base confidence for phonetic matches
        double confidence = 0.6;

        // Boost if lengths are similar
        int lengthDiff = Math.abs(original.length() - phoneticMatch.length());
        if (lengthDiff <= 1) {
            confidence += 0.1;
        } else if (lengthDiff > 3) {
            confidence -= 0.2;
        }

        // Boost if they share common prefixes or suffixes
        int commonPrefixLength = getCommonPrefixLength(original.toLowerCase(), phoneticMatch.toLowerCase());
        int commonSuffixLength = getCommonSuffixLength(original.toLowerCase(), phoneticMatch.toLowerCase());

        if (commonPrefixLength > 2) {
            confidence += 0.1;
        }
        if (commonSuffixLength > 2) {
            confidence += 0.1;
        }

        return Math.max(0.0, Math.min(1.0, confidence));
    }

    /**
     * Calculates confidence for abbreviation matches
     */
    private double calculateAbbreviationConfidence(String abbreviation, String expansion, String entityName) {
        if (abbreviation == null || expansion == null || entityName == null) return 0.0;

        // Base confidence for abbreviation expansion
        double confidence = 0.7;

        // Check how well the expansion matches the entity name
        String lowerEntity = entityName.toLowerCase();
        String lowerExpansion = expansion.toLowerCase();

        if (lowerEntity.contains(lowerExpansion)) {
            // Boost if expansion is found in entity name
            double matchRatio = (double) expansion.length() / entityName.length();
            confidence += matchRatio * 0.2;
        } else {
            // Penalty if expansion doesn't match well
            confidence -= 0.3;
        }

        // Boost for reasonable abbreviation ratios
        double abbreviationRatio = (double) abbreviation.length() / expansion.length();
        if (abbreviationRatio >= 0.2 && abbreviationRatio <= 0.6) {
            confidence += 0.1;
        }

        return Math.max(0.0, Math.min(1.0, confidence));
    }

    /**
     * Gets the length of common prefix between two strings
     */
    private int getCommonPrefixLength(String s1, String s2) {
        int minLength = Math.min(s1.length(), s2.length());
        int commonLength = 0;

        for (int i = 0; i < minLength; i++) {
            if (s1.charAt(i) == s2.charAt(i)) {
                commonLength++;
            } else {
                break;
            }
        }

        return commonLength;
    }

    /**
     * Gets the length of common suffix between two strings
     */
    private int getCommonSuffixLength(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();
        int minLength = Math.min(len1, len2);
        int commonLength = 0;

        for (int i = 1; i <= minLength; i++) {
            if (s1.charAt(len1 - i) == s2.charAt(len2 - i)) {
                commonLength++;
            } else {
                break;
            }
        }

        return commonLength;
    }
    
    /**
     * Calculates confidence based on edit distance
     */
    private double calculateEditDistanceConfidence(String query, String target) {
        if (query == null || target == null) return 0.0;
        if (query.equals(target)) return 1.0;

        int editDistance = calculateEditDistance(query, target);
        int maxLength = Math.max(query.length(), target.length());

        if (maxLength == 0) return 0.0;

        // Base confidence decreases with edit distance
        double confidence = 1.0 - (double) editDistance / maxLength;

        // Adjust confidence based on string characteristics
        int minLength = Math.min(query.length(), target.length());
        int lengthDiff = Math.abs(query.length() - target.length());

        // Boost confidence for longer strings (more reliable matches)
        if (minLength > 6) {
            confidence = Math.min(1.0, confidence + 0.05);
        }

        // Penalty for very different lengths (likely false positive)
        if (lengthDiff > maxLength / 2) {
            confidence *= 0.7;
        }

        // Penalty if edit distance is too high relative to string length
        if (editDistance > maxLength / 2) {
            confidence *= 0.5;
        }

        return Math.max(0.0, Math.min(1.0, confidence));
    }
    
    /**
     * Calculates confidence for partial matches
     */
    private double calculatePartialMatchConfidence(String query, String target) {
        if (query == null || target == null) return 0.0;
        if (query.equals(target)) return 1.0;

        String lowerQuery = query.toLowerCase();
        String lowerTarget = target.toLowerCase();

        if (!lowerTarget.contains(lowerQuery)) {
            return 0.0;
        }

        // Base confidence: how much of the query is matched (should be 1.0 for substring)
        double baseConfidence = 1.0;

        // Adjust based on the proportion of query to target
        double lengthRatio = (double) query.length() / target.length();

        // Higher confidence for larger portions of the target
        double confidence = baseConfidence * (0.3 + 0.7 * lengthRatio);

        // Boost for exact position matches
        if (lowerTarget.startsWith(lowerQuery)) {
            confidence = Math.min(1.0, confidence + 0.2); // Prefix match bonus
        } else if (lowerTarget.endsWith(lowerQuery)) {
            confidence = Math.min(1.0, confidence + 0.15); // Suffix match bonus
        }

        // Penalty for very small matches in large targets
        if (lengthRatio < 0.2) {
            confidence *= 0.8;
        }

        return Math.max(0.0, Math.min(1.0, confidence));
    }
    
    /**
     * Generates potential typo corrections based on keyboard layout
     */
    private Set<String> generateTypoCorrections(String term) {
        if (term == null || term.length() < 2) return new HashSet<>();

        Set<String> corrections = new HashSet<>();

        // 1. Substitution typos (replace character with keyboard neighbor)
        for (int i = 0; i < term.length(); i++) {
            char c = term.charAt(i);
            String neighbors = KEYBOARD_NEIGHBORS.get(Character.toLowerCase(c));

            if (neighbors != null) {
                for (char neighbor : neighbors.toCharArray()) {
                    String correction = term.substring(0, i) + neighbor + term.substring(i + 1);
                    corrections.add(correction);
                }
            }
        }

        // 2. Deletion typos (remove character)
        for (int i = 0; i < term.length(); i++) {
            String deletion = term.substring(0, i) + term.substring(i + 1);
            if (deletion.length() >= 2) { // Keep if still meaningful length
                corrections.add(deletion);
            }
        }

        // 3. Insertion typos (add character)
        for (int i = 0; i <= term.length(); i++) {
            // Try inserting common characters at each position
            for (char c : "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray()) {
                String insertion = term.substring(0, i) + c + term.substring(i);
                if (insertion.length() <= term.length() + 2) { // Reasonable length limit
                    corrections.add(insertion);
                }
            }
        }

        // 4. Transposition typos (swap adjacent characters)
        for (int i = 0; i < term.length() - 1; i++) {
            char[] chars = term.toCharArray();
            // Swap characters at positions i and i+1
            char temp = chars[i];
            chars[i] = chars[i + 1];
            chars[i + 1] = temp;
            corrections.add(new String(chars));
        }

        // Remove the original term and very short results
        corrections.remove(term);
        corrections.removeIf(s -> s.length() < 2);

        return corrections;
    }
    
    /**
     * Finds entities that contain the given term as substring
     */
    private List<EntityMatch> findEntitiesContaining(String term) {
        if (term == null || term.trim().isEmpty()) return new ArrayList<>();

        List<EntityMatch> matches = new ArrayList<>();
        String lowerTerm = term.toLowerCase().trim();

        try {
            // First, try exact matches (most efficient)
            List<EntityMatch> exactMatches = registry.findExactMatches(term);
            if (exactMatches != null) {
                matches.addAll(exactMatches);
            }

            // Then try prefix matches
            List<EntityMatch> prefixMatches = registry.findPrefixMatches(term);
            if (prefixMatches != null) {
                for (EntityMatch match : prefixMatches) {
                    if (match != null && !containsMatch(matches, match)) {
                        matches.add(match);
                    }
                }
            }

            // Try suffix matches
            List<EntityMatch> suffixMatches = registry.findSuffixMatches(term);
            if (suffixMatches != null) {
                for (EntityMatch match : suffixMatches) {
                    if (match != null && !containsMatch(matches, match)) {
                        matches.add(match);
                    }
                }
            }

            // For true substring matching, we would need to implement
            // a more sophisticated search or enhance the registry
            // This is a reasonable approximation for now

        } catch (Exception e) {
            log.warn("Error finding entities containing term: {}", term, e);
        }

        return matches;
    }

    /**
     * Checks if matches list already contains a match with the same entity ID
     */
    private boolean containsMatch(List<EntityMatch> matches, EntityMatch newMatch) {
        return matches.stream()
                .anyMatch(existing -> existing.getEntityId() != null &&
                         existing.getEntityId().equals(newMatch.getEntityId()));
    }
    
    /**
     * Calculates edit distance between two strings
     */
    private int calculateEditDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(
                        Math.min(dp[i - 1][j], dp[i][j - 1]), 
                        dp[i - 1][j - 1]
                    );
                }
            }
        }
        
        return dp[s1.length()][s2.length()];
    }
    
    /**
     * Checks if the agent can handle the given query context
     */
    @Override
    public boolean canHandle(QueryContext context) {
        // Can handle if there are identifiers that might have typos
        return !context.getIdentifierTokens().isEmpty() ||
               context.getTokens().stream().anyMatch(t -> t.getConfidence() < 0.8);
    }
    
    /**
     * Gets the confidence score for this agent handling the query
     */
    @Override
    public double getHandlingConfidence(QueryContext context) {
        // Higher confidence for queries with potential typos
        long lowConfidenceTokens = context.getTokens().stream()
                .filter(t -> t.getConfidence() < 0.7)
                .count();
        
        double confidence = 0.3 + (lowConfidenceTokens * 0.2);
        
        // Lower confidence if query seems very clear
        if (context.getConfidence() > 0.9) {
            confidence *= 0.5;
        }
        
        return Math.min(1.0, confidence);
    }

    /**
     * Calculates confidence for typo corrections
     */
    private double calculateTypoConfidence(String original, String correction) {
        if (original == null || correction == null) return 0.0;
        if (original.equals(correction)) return 1.0;

        // Base confidence for typo corrections
        double confidence = 0.5;

        // Calculate edit distance
        int editDistance = calculateEditDistance(original, correction);

        // Boost confidence for single-character changes
        if (editDistance == 1) {
            confidence = 0.7;
        } else if (editDistance == 2) {
            confidence = 0.6;
        }

        // Adjust based on string length
        int maxLength = Math.max(original.length(), correction.length());
        if (maxLength > 6) {
            confidence += 0.1;
        }

        // Penalty for very different lengths
        int lengthDiff = Math.abs(original.length() - correction.length());
        if (lengthDiff > 2) {
            confidence -= 0.2;
        }

        return Math.max(0.0, Math.min(1.0, confidence));
    }

    /**
     * Calculates confidence for misspelling corrections
     */
    private double calculateMisspellingConfidence(String misspelling, String correction, String entityName) {
        if (misspelling == null || correction == null || entityName == null) return 0.0;

        // High base confidence for known misspellings
        double confidence = 0.8;

        // Check how well the correction matches the entity name
        String lowerEntity = entityName.toLowerCase();
        String lowerCorrection = correction.toLowerCase();

        if (lowerEntity.contains(lowerCorrection)) {
            // Boost if correction is found in entity name
            double matchRatio = (double) correction.length() / entityName.length();
            confidence = Math.min(1.0, confidence + matchRatio * 0.1);
        } else {
            // Penalty if correction doesn't match well
            confidence -= 0.3;
        }

        return Math.max(0.0, Math.min(1.0, confidence));
    }
}