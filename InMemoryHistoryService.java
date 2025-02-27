package com.farmers.ecom.email.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryHistoryService {
    private final Map<String, String> userHistoryIds = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> processedMessageIds = new ConcurrentHashMap<>();

    // Save the latest historyId for a user
    public void saveHistoryId(String userEmail, String historyId) {
        userHistoryIds.put(userEmail, historyId);
    }

    // Retrieve the latest historyId for a user
    public String getLastHistoryId(String userEmail) {
        return userHistoryIds.get(userEmail);
    }

    // Mark a message as processed
    public void saveProcessedMessageId(String userEmail, String messageId) {
        processedMessageIds.computeIfAbsent(userEmail, k -> new HashSet<>()).add(messageId);
    }

    // Check if a message has already been processed
    public boolean isMessageProcessed(String userEmail, String messageId) {
        return processedMessageIds.getOrDefault(userEmail, Collections.emptySet()).contains(messageId);
    }
}

