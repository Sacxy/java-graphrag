package com.tekion.javaastkg.agents.entity.registry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Trie (Prefix Tree) implementation for fast prefix-based string matching.
 * Enables efficient search for strings that start with a given prefix.
 * 
 * Time Complexity: O(k) for search where k is the length of the prefix
 * Space Complexity: O(ALPHABET_SIZE * N * M) where N is number of strings, M is average length
 */
public class TrieNode {
    
    private Map<Character, TrieNode> children;
    private boolean isEndOfWord;
    private String word; // Store the complete word at leaf nodes
    private int frequency; // How many times this word was inserted
    
    public TrieNode() {
        this.children = new HashMap<>();
        this.isEndOfWord = false;
        this.word = null;
        this.frequency = 0;
    }
    
    /**
     * Inserts a word into the Trie
     */
    public void insert(String word) {
        if (word == null || word.isEmpty()) {
            return;
        }
        
        TrieNode current = this;
        for (char c : word.toCharArray()) {
            current.children.putIfAbsent(c, new TrieNode());
            current = current.children.get(c);
        }
        
        current.isEndOfWord = true;
        current.word = word;
        current.frequency++;
    }
    
    /**
     * Searches for an exact word in the Trie
     */
    public boolean search(String word) {
        if (word == null || word.isEmpty()) {
            return false;
        }
        
        TrieNode current = this;
        for (char c : word.toCharArray()) {
            current = current.children.get(c);
            if (current == null) {
                return false;
            }
        }
        
        return current.isEndOfWord;
    }
    
    /**
     * Finds all words that start with the given prefix
     */
    public List<String> findWordsWithPrefix(String prefix) {
        List<String> results = new ArrayList<>();
        
        if (prefix == null) {
            return results;
        }
        
        // Navigate to the prefix node
        TrieNode prefixNode = this;
        for (char c : prefix.toCharArray()) {
            prefixNode = prefixNode.children.get(c);
            if (prefixNode == null) {
                return results; // Prefix not found
            }
        }
        
        // Collect all words from the prefix node
        collectAllWords(prefixNode, results);
        return results;
    }
    
    /**
     * Collects all words from a given node (DFS traversal)
     */
    private void collectAllWords(TrieNode node, List<String> results) {
        if (node == null) {
            return;
        }
        
        if (node.isEndOfWord && node.word != null) {
            results.add(node.word);
        }
        
        for (TrieNode child : node.children.values()) {
            collectAllWords(child, results);
        }
    }
    
    /**
     * Finds all words that start with the prefix, sorted by frequency
     */
    public List<String> findWordsWithPrefixSorted(String prefix, int maxResults) {
        List<String> allMatches = findWordsWithPrefix(prefix);
        
        // Sort by frequency (higher frequency first)
        allMatches.sort((w1, w2) -> {
            int freq1 = getWordFrequency(w1);
            int freq2 = getWordFrequency(w2);
            if (freq1 != freq2) {
                return Integer.compare(freq2, freq1); // Descending order
            }
            return w1.compareTo(w2); // Alphabetical if same frequency
        });
        
        // Limit results
        if (maxResults > 0 && allMatches.size() > maxResults) {
            return allMatches.subList(0, maxResults);
        }
        
        return allMatches;
    }
    
    /**
     * Gets the frequency of a word
     */
    public int getWordFrequency(String word) {
        TrieNode current = this;
        for (char c : word.toCharArray()) {
            current = current.children.get(c);
            if (current == null) {
                return 0;
            }
        }
        
        return current.isEndOfWord ? current.frequency : 0;
    }
    
    /**
     * Checks if any word in the trie starts with the given prefix
     */
    public boolean startsWith(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return true;
        }
        
        TrieNode current = this;
        for (char c : prefix.toCharArray()) {
            current = current.children.get(c);
            if (current == null) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Removes a word from the Trie
     */
    public boolean delete(String word) {
        return delete(word, 0);
    }
    
    private boolean delete(String word, int index) {
        if (index == word.length()) {
            // End of word reached
            if (!isEndOfWord) {
                return false; // Word doesn't exist
            }
            
            isEndOfWord = false;
            this.word = null;
            this.frequency = 0;
            
            // Return true if current node has no children (can be deleted)
            return children.isEmpty();
        }
        
        char c = word.charAt(index);
        TrieNode node = children.get(c);
        
        if (node == null) {
            return false; // Word doesn't exist
        }
        
        boolean shouldDeleteChild = delete(word, index + 1);
        
        if (shouldDeleteChild) {
            children.remove(c);
            
            // Return true if current node has no children and is not end of another word
            return !isEndOfWord && children.isEmpty();
        }
        
        return false;
    }
    
    /**
     * Gets the total number of words in the Trie
     */
    public int getWordCount() {
        return getWordCount(this);
    }
    
    private int getWordCount(TrieNode node) {
        if (node == null) {
            return 0;
        }
        
        int count = node.isEndOfWord ? 1 : 0;
        for (TrieNode child : node.children.values()) {
            count += getWordCount(child);
        }
        
        return count;
    }
    
    /**
     * Gets all words in the Trie
     */
    public List<String> getAllWords() {
        List<String> allWords = new ArrayList<>();
        collectAllWords(this, allWords);
        return allWords;
    }
    
    /**
     * Clears all entries from the Trie
     */
    public void clear() {
        children.clear();
        isEndOfWord = false;
        word = null;
        frequency = 0;
    }
    
    /**
     * Checks if the Trie is empty
     */
    public boolean isEmpty() {
        return children.isEmpty() && !isEndOfWord;
    }
}