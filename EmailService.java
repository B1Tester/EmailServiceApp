package com.farmers.ecom.email.service;

import com.farmers.ecom.email.config.GmailServiceConfig;
import com.farmers.ecom.email.controller.WebhookController;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.History;
import com.google.api.services.gmail.model.HistoryMessageAdded;
import com.google.api.services.gmail.model.ListHistoryResponse;
import com.google.api.services.gmail.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.*;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);


    @Autowired
    private GmailServiceConfig gmailServiceConfig;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private InMemoryHistoryService historyService;

    public void processUpdates(String emailAddress, String currentHistoryId) {
        try {
            String lastHistoryId = historyService.getLastHistoryId(emailAddress);

            if (lastHistoryId == null) {
                historyService.saveHistoryId(emailAddress, currentHistoryId);
                return; // Skip first notification as it gives no updates
            }

            Gmail userGmailService = gmailServiceConfig.createGmailService(emailAddress);

            ListHistoryResponse historyResponse = userGmailService.users().history().list(emailAddress)
                    .setStartHistoryId(BigInteger.valueOf(Long.parseLong(lastHistoryId)))
                    .setHistoryTypes(Arrays.asList("messageAdded"))
                    .execute();

            List<String> newMessageIds = new ArrayList<>();
            if (historyResponse.getHistory() != null) {
                for (History history : historyResponse.getHistory()) {
                    if (history.getMessagesAdded() != null) {
                        for (HistoryMessageAdded messageAdded : history.getMessagesAdded()) {
                            String messageId = messageAdded.getMessage().getId();

                            // Skip if already processed
                            if (!historyService.isMessageProcessed(emailAddress, messageId)) {
                                newMessageIds.add(messageId);
                                historyService.saveProcessedMessageId(emailAddress, messageId);
                            }
                        }
                    }
                }
            }

            for (String messageId : newMessageIds) {
                Message message = userGmailService.users().messages().get(emailAddress, messageId).execute();
                Map<String, Object> formattedMessage = formatMessage(message);

                // Broadcast to frontend
                messagingTemplate.convertAndSend("/topic/updates/" + emailAddress, formattedMessage);
            }

            historyService.saveHistoryId(emailAddress, currentHistoryId);
        } catch (Exception e) {
            logger.error("Error processing updates for email: {}", emailAddress, e);
        }
    }

    private Map<String, Object> formatMessage(Message message) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", message.getId());
        result.put("threadId", message.getThreadId());
        result.put("snippet", message.getSnippet());
        return result;
    }
}

