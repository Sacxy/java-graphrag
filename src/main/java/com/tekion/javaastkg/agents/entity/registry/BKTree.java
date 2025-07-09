package com.tekion.javaastkg.agents.entity.registry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BK-Tree (Burkhard-Keller Tree) implementation for fast fuzzy string matching.
 * Enables efficient search for strings within a given edit distance.
 * 
 * Time Complexity: O(log n) average for search operations
 * Space Complexity: O(n) where n is the number of strings
 */
public class BKTree {
    
    private BKNode root;
    
    public BKTree() {
        this.root = null;
    }
    
    /**
     * Adds a string to the BK-Tree
     */
    public void add(String word) {
        if (root == null) {
            root = new BKNode(word);
        } else {
            add(root, word);
        }
    }
    
    private void add(BKNode node, String word) {
        int distance = editDistance(node.word, word);
        
        if (distance == 0) {
            return; // Word already exists
        }
        
        BKNode child = node.children.get(distance);
        if (child == null) {
            node.children.put(distance, new BKNode(word));
        } else {
            add(child, word);
        }
    }
    
    /**
     * Searches for all strings within maxDistance edit distance from the query
     */
    public List<String> search(String query, int maxDistance) {
        List<String> results = new ArrayList<>();
        if (root != null) {
            search(root, query, maxDistance, results);
        }
        return results;
    }
    
    private void search(BKNode node, String query, int maxDistance, List<String> results) {
        int distance = editDistance(node.word, query);
        
        if (distance <= maxDistance) {
            results.add(node.word);
        }
        
        // Search children within the distance range
        int minChildDistance = Math.max(1, distance - maxDistance);
        int maxChildDistance = distance + maxDistance;
        
        for (Map.Entry<Integer, BKNode> entry : node.children.entrySet()) {
            int childDistance = entry.getKey();
            if (childDistance >= minChildDistance && childDistance <= maxChildDistance) {
                search(entry.getValue(), query, maxDistance, results);
            }
        }
    }
    
    /**
     * Calculates the edit distance (Levenshtein distance) between two strings
     */
    private int editDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        // Initialize base cases
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
        // Fill the DP table
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
     * Node in the BK-Tree
     */
    private static class BKNode {
        String word;
        Map<Integer, BKNode> children;
        
        BKNode(String word) {
            this.word = word;
            this.children = new HashMap<>();
        }
    }
    
    /**
     * Returns the number of words in the tree
     */
    public int size() {
        return size(root);
    }
    
    private int size(BKNode node) {
        if (node == null) {
            return 0;
        }
        
        int count = 1; // Count this node
        for (BKNode child : node.children.values()) {
            count += size(child);
        }
        return count;
    }
    
    /**
     * Checks if the tree is empty
     */
    public boolean isEmpty() {
        return root == null;
    }
    
    /**
     * Clears all entries from the tree
     */
    public void clear() {
        root = null;
    }
}