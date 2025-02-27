package com.farmers.ecom.email.service;

import com.farmers.ecom.email.config.GmailServiceConfig;
import com.farmers.ecom.email.util.EmailStorageUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.cloud.pubsub.v1.MessageReceiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.lang.Thread;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import jakarta.annotation.PostConstruct;


@Service
public class PubSubPullService {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmailStorageUtil emailStorageUtil;

    private static final Logger logger = LoggerFactory.getLogger(PubSubPullService.class);

    private static final String PROJECT_ID = "claimsitdevpoc"; // Replace with your GCP project ID
    private static final String SUBSCRIPTION_NAME = "gmail-watch-topic-sub"; // Replace with your subscription name

    private static final Set<String> TEXT_MIME_TYPES = Set.of("text/plain", "text/html");

    @Autowired
    private SimpMessagingTemplate messagingTemplate; // WebSocket template for frontend notifications

    @Autowired
    GmailServiceConfig gmailServiceConfig;

    private final Map<String, BigInteger> userHistoryMap = new ConcurrentHashMap<>();

    public void startPullingMessages() {
        try {
            // Load service account credentials from the resources folder
            InputStream credentialsStream = new ClassPathResource("service-account-key.json").getInputStream();
            GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream);

            ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(PROJECT_ID, SUBSCRIPTION_NAME);

            // Create a Pub/Sub subscriber with the loaded credentials
            Subscriber subscriber = Subscriber.newBuilder(subscriptionName, (MessageReceiver) (message, consumer) -> {
                try {
                    logger.info("Received message: {}", message.getData().toStringUtf8());
                    // Acknowledge the message after processing
                    processMessage(message);
                    consumer.ack();
                } catch (Exception e) {
                    logger.error("Error processing message: {}", e.getMessage(), e);
                    // Nack (negative acknowledgment) the message if processing fails
                    consumer.nack();
                }
            }).setCredentialsProvider(() -> credentials).build();

            // Start the subscriber
            subscriber.startAsync().awaitRunning();
            logger.info("Pull subscription started for {}", SUBSCRIPTION_NAME);

            // Add a shutdown hook to stop the subscriber when the application terminates
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down subscriber...");
                subscriber.stopAsync();
            }));

        } catch (IOException e) {
            logger.error("Error initializing Pub/Sub subscriber: {}", e.getMessage(), e);
        }
    }

    @PostConstruct
    public void init() {
        logger.info("Fetching latest history IDs for all users on startup...");
        fetchLatestHistoryIdsForAllUsers();
    }

    private void fetchLatestHistoryIdsForAllUsers() {
        try {
            List<String> userEmails = List.of("utkarshs@claimsitdev.farmers.com"); // Replace with actual user list retrieval
            for (String userEmail : userEmails) {
                fetchAndStoreLatestHistoryId(userEmail);
            }
        } catch (Exception e) {
            logger.error("Error fetching initial history IDs", e);
        }
    }

    private void fetchAndStoreLatestHistoryId(String userEmail) {
        try {
            Gmail gmailService = gmailServiceConfig.createGmailService(userEmail);
            BigInteger latestHistoryId = gmailService.users().getProfile(userEmail).execute().getHistoryId();

            userHistoryMap.put(userEmail, latestHistoryId);
            logger.info("Stored latest history ID {} for user: {}", latestHistoryId, userEmail);
        } catch (Exception e) {
            logger.error("Error fetching history ID for user {}: {}", userEmail, e.getMessage(), e);
        }
    }



    public void processMessage(PubsubMessage pubsubMessage) throws JsonProcessingException {
        logger.info("Received Pub/Sub message: {}", pubsubMessage);

        // Extract data from the Pub/Sub message
        String data = pubsubMessage.getData().toStringUtf8();
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> dataMap = objectMapper.readValue(data, Map.class);

        String emailAddress = (String) dataMap.get("emailAddress");
        BigInteger pubSubHistoryId = new BigInteger(String.valueOf(dataMap.get("historyId")));

        logger.info("Parsed emailAddress: {}", emailAddress);
        logger.info("Parsed historyId from Pub/Sub: {}", pubSubHistoryId);

        if (emailAddress == null || pubSubHistoryId == null) {
            logger.warn("Message is missing required attributes: emailAddress or historyId.");
            return;
        }

        try {
            Gmail gmailService = gmailServiceConfig.createGmailService(emailAddress);

            // Retrieve stored history ID
            BigInteger oldHistoryId = userHistoryMap.get(emailAddress);

            if (oldHistoryId == null) {
                logger.info("No previous history ID found for user: {}. Using startup history ID.", emailAddress);
                oldHistoryId = pubSubHistoryId.subtract(BigInteger.ONE);
            } else {
                oldHistoryId = oldHistoryId.min(pubSubHistoryId.subtract(BigInteger.ONE));
            }

            logger.info("Fetching changes for user: {} from history ID: {}", emailAddress, oldHistoryId);

            ListHistoryResponse historyResponse = gmailService.users().history().list(emailAddress)
                    .setStartHistoryId(oldHistoryId)
                    .setHistoryTypes(List.of("messageAdded"))
                    .execute();

            if (historyResponse.getHistory() != null && !historyResponse.getHistory().isEmpty()) {
                List<Message> newMessages = new ArrayList<>();

                for (History history : historyResponse.getHistory()) {
                    if (history.getMessagesAdded() != null) {
                        for (HistoryMessageAdded added : history.getMessagesAdded()) {
                            try {
                                Message fullMessage = gmailService.users().messages()
                                        .get(emailAddress, added.getMessage().getId())
                                        .setFormat("full")
                                        .execute();

                                // ✅ Save Email with Inline Images!
                                emailStorageUtil.saveEmail(fullMessage, emailAddress);

                                newMessages.add(fullMessage);
                            } catch (GoogleJsonResponseException e) {
                                if (e.getStatusCode() == 404) {
                                    logger.error("Message not found (ID: {}), possibly deleted.", added.getMessage().getId());
                                } else {
                                    logger.error("Error fetching email (ID: {}): {}", added.getMessage().getId(), e.getMessage());
                                }
                            }
                        }
                    }
                }

                sendToFrontend(emailAddress, newMessages);
            } else {
                logger.info("No new messages found for email: {}", emailAddress);
            }

            // ✅ Update stored history ID to the latest one from Pub/Sub
            userHistoryMap.put(emailAddress, pubSubHistoryId);
            logger.info("Updated history ID for user: {} to {}", emailAddress, pubSubHistoryId);

        } catch (Exception e) {
            logger.error("Error processing message for email: {}", emailAddress, e);
        }
    }


    private void sendToFrontend(String emailAddress, List<Message> messages) {
        for (Message message : messages) {
            // Create a simplified payload for the frontend
            var frontendPayload = createFrontendPayload(message, emailAddress);

            try {
                // Serialize the payload to JSON using ObjectMapper
                ObjectMapper objectMapper = new ObjectMapper();
                String jsonPayload = objectMapper.writeValueAsString(frontendPayload);

                // Send to the WebSocket topic for this user as a JSON string
                messagingTemplate.convertAndSend("/topic/updates/" + emailAddress, jsonPayload);

                logger.info("Sent new email update to frontend for email: {}", emailAddress);
            } catch (JsonProcessingException e) {
                logger.error("Error serializing frontend payload to JSON: {}", e.getMessage());
            }
        }
    }

    private Map<String, Object> createFrontendPayload(Message message, String userEmail) {
        Map<String, Object> formattedMessage = new HashMap<>();
        formattedMessage.put("id", message.getId());
        formattedMessage.put("threadId", message.getThreadId());
        formattedMessage.put("snippet", message.getSnippet());
        formattedMessage.put("labelIds", message.getLabelIds());

        MessagePart payload = message.getPayload();
        if (payload != null) {
            Map<String, Object> formattedPayload = new HashMap<>();
            formattedPayload.put("headers", formatHeaders(payload.getHeaders()));

            // Extract body content and convert text to HTML if needed
            String emailContent = extractEmailContent(payload);
            if (emailContent != null) {
                Map<String, Object> bodyContent = new HashMap<>();
                bodyContent.put("data", emailContent);
                bodyContent.put("mimeType", payload.getMimeType());
                formattedPayload.put("body", bodyContent);
            }



            // Handle attachments (catch IOException inside)
            try {
                List<Map<String, Object>> attachments = extractAttachments(payload, message.getId(), userEmail);
                if (!attachments.isEmpty()) {
                    formattedPayload.put("attachments", attachments);
                }
            } catch (IOException e) {
                logger.error("Error extracting attachments for message ID {}: {}", message.getId(), e.getMessage());
            }

            formattedMessage.put("payload", formattedPayload);
        }

        return formattedMessage;
    }


    private List<Map<String, String>> formatHeaders(List<MessagePartHeader> headers) {
        if (headers == null) return new ArrayList<>();

        return headers.stream()
                .filter(header -> Arrays.asList("From", "To", "Subject", "Date").contains(header.getName()))
                .map(header -> {
                    Map<String, String> formattedHeader = new HashMap<>();
                    formattedHeader.put("name", header.getName());
                    formattedHeader.put("value", header.getValue());
                    return formattedHeader;
                })
                .collect(Collectors.toList());
    }

    private String extractEmailContent(MessagePart part) {
        if (part == null) return null;

        // Handle plain text or HTML parts
        if (part.getBody() != null && part.getBody().getData() != null) {
            return part.getBody().getData();
            /*String decodedData = decodeBody(part.getBody().getData());

            if ("text/plain".equalsIgnoreCase(part.getMimeType())) {
                return convertTextToHtml(decodedData);
            } else if ("text/html".equalsIgnoreCase(part.getMimeType())) {
                return decodedData; // Already in HTML format
            }*/
        }

        // Handle multipart messages (nested parts)
        if (part.getParts() != null) {
            for (MessagePart subPart : part.getParts()) {
                String content = extractEmailContent(subPart);
                if (content != null) {
                    return content;
                }
            }
        }

        return null;
    }

    private String convertTextToHtml(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return "";
        }

        // Escape HTML special characters
        plainText = plainText.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");

        // Convert new lines to paragraphs
        plainText = "<p>" + plainText.replace("\r\n", "</p><p>")
                .replace("\n", "</p><p>")
                .replace("\r", "</p><p>") + "</p>";

        // Convert Markdown-style formatting
        plainText = plainText.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>") // Bold (**text**)
                .replaceAll("\\*(.*?)\\*", "<b>$1</b>")       // Bold (*text*)
                .replaceAll("_(.*?)_", "<i>$1</i>")          // Italics (_text_)
                .replaceAll("`(.*?)`", "<code>$1</code>");   // Code (`text`)

        // Convert lists
        plainText = plainText.replaceAll("- (.*)", "<ul><li>$1</li></ul>");

        // Convert email subject into an <h1>
        int firstParagraphEnd = plainText.indexOf("</p>");
        if (firstParagraphEnd != -1) {
            plainText = "<h1>" + plainText.substring(3, firstParagraphEnd) + "</h1>"
                    + plainText.substring(firstParagraphEnd);
        }

        return plainText;
    }




    private List<Map<String, Object>> extractAttachments(MessagePart part, String messageId, String userEmail) throws IOException {
        Gmail userGmailService = gmailServiceConfig.createGmailService(userEmail);
        List<Map<String, Object>> attachments = new ArrayList<>();

        if (part.getParts() != null) {
            for (MessagePart subPart : part.getParts()) {
                String filename = subPart.getFilename();
                if (filename != null && !filename.isEmpty() &&
                        (subPart.getBody().getData() != null || subPart.getBody().getAttachmentId() != null)) {
                    try {
                        Map<String, Object> attachment = new HashMap<>();
                        attachment.put("filename", filename);
                        attachment.put("mimeType", subPart.getMimeType());

                        if (subPart.getBody().getAttachmentId() != null) {
                            String attachmentId = subPart.getBody().getAttachmentId();
                            MessagePartBody attachmentBody = userGmailService.users().messages()
                                    .attachments()
                                    .get(userEmail, messageId, attachmentId)
                                    .execute();

                            if (attachmentBody.getData() != null) {
                                attachment.put("data", attachmentBody.getData());
                                attachment.put("size", attachmentBody.getSize());
                            }
                        }

                        attachments.add(attachment);
                    } catch (Exception e) {
                        logger.error("Error processing attachment for message {}: {}", messageId, e.getMessage());
                    }
                }
            }
        }

        return attachments;
    }


    private String decodeBody(String encodedData) {
        if (encodedData == null || encodedData.isEmpty()) {
            return "";
        }

        try {
            byte[] decodedBytes = Base64.getUrlDecoder().decode(encodedData);
            return new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            logger.error("Error decoding message body", e);
            return "Error decoding message body";
        }
    }
}
