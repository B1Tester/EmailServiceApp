// EmailFetchController.java
package com.farmers.ecom.email.controller;

import com.farmers.ecom.email.config.GmailServiceConfig;
import com.farmers.ecom.email.service.DomainUserService;
import com.farmers.ecom.email.service.JwtAuthenticationService;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import com.google.api.services.gmail.model.Thread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZonedDateTime;

@RestController
@RequestMapping("/api/gmail")
public class EmailFetchController {
    private static final Logger logger = LoggerFactory.getLogger(EmailFetchController.class);
    //private final Gmail gmailService;
    private final DomainUserService domainUserService;
    private final GmailServiceConfig gmailServiceConfig;
    private static final Set<String> TEXT_MIME_TYPES = Set.of("text/plain", "text/html");
    private static final int MAX_RESULTS = 20;

    @Value("${gmail.service-account.user}")
    private String serviceAccountUser;

    public EmailFetchController(GmailServiceConfig gmailServiceConfig, DomainUserService domainUserService) {
        this.gmailServiceConfig = gmailServiceConfig;
        this.domainUserService = domainUserService;
    }


    @GetMapping("/all-users/emails")
    public ResponseEntity<Map<String, Object>> getAllUsersEmails(
            @RequestParam(required = false) String pageToken,
            @RequestParam(defaultValue = "100") Integer maxResults) {
        try {
            List<String> userEmails = domainUserService.getAllUserEmails();
            Map<String, Object> result = new LinkedHashMap<>();
            List<Map<String, Object>> allEmails = new ArrayList<>();
            Map<String, String> processingStatus = new LinkedHashMap<>();

            logger.info("Starting to fetch emails for {} users", userEmails.size());

            for (String userEmail : userEmails) {
                logger.info("Processing emails for user: {}", userEmail);
                try {
                    // Attempt to create Gmail service first to validate permissions
                    Gmail userGmailService = gmailServiceConfig.createGmailService(userEmail);

                    Map<String, Object> userEmailsData = fetchUserEmails(userEmail, pageToken, maxResults);
                    userEmailsData.put("userEmail", userEmail);
                    allEmails.add(userEmailsData);
                    processingStatus.put(userEmail, "SUCCESS");
                    logger.info("Successfully fetched emails for user: {}", userEmail);
                } catch (Exception e) {
                    logger.error("Error fetching emails for user: {} - Error: {}", userEmail, e.getMessage());
                    processingStatus.put(userEmail, "FAILED - " + e.getMessage());
                    // Continue with next user if one fails
                    continue;
                }
            }

            result.put("userEmails", allEmails);
            result.put("processingStatus", processingStatus);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching all users' emails", e);
            return ResponseEntity.internalServerError().build();
        }
    }


    private Map<String, Object> fetchUserEmails(String userEmail, String pageToken, Integer maxResults) throws IOException {
        logger.debug("Fetching emails for user: {} with maxResults: {}", userEmail, maxResults);

        // Create a Gmail service instance for this specific user
        Gmail userGmailService = gmailServiceConfig.createGmailService(userEmail);

        Gmail.Users.Threads.List threadRequest = userGmailService.users().threads()
                .list(userEmail)
                .setMaxResults(Long.valueOf(maxResults));

        if (pageToken != null && !pageToken.isEmpty()) {
            threadRequest.setPageToken(pageToken);
        }

        ListThreadsResponse threadResponse;
        try {
            threadResponse = threadRequest.execute();
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 400 && e.getDetails().getMessage().contains("Mail service not enabled")) {
                logger.warn("Mail service not enabled for user: {}", userEmail);
                return createEmptyResult();
            }
            throw e;
        }

        List<Map<String, Object>> formattedThreads = new ArrayList<>();

        logger.info("Found {} threads for user: {}",
                threadResponse.getThreads() != null ? threadResponse.getThreads().size() : 0,
                userEmail);

        if (threadResponse.getThreads() != null) {
            for (Thread thread : threadResponse.getThreads()) {
                Thread fullThread = userGmailService.users().threads()
                        .get(userEmail, thread.getId())
                        .execute();

                Map<String, Object> formattedThread = formatThread(fullThread);
                formattedThread.put("userEmail", userEmail);

                // Sort the messages within the thread by date
                List<Map<String, Object>> messages = (List<Map<String, Object>>) formattedThread.get("messages");
                messages.sort((msg1, msg2) -> {
                    Instant date1 = extractDateFromMessage(msg1);
                    Instant date2 = extractDateFromMessage(msg2);
                    return date2.compareTo(date1); // Newest first
                });

                formattedThreads.add(formattedThread);
            }
        }

        // Sort threads by the latest message's date (newest first)
        formattedThreads.sort((t1, t2) -> {
            Instant date1 = extractDateFromThread(t1);
            Instant date2 = extractDateFromThread(t2);
            return date2.compareTo(date1); // Newest first
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("threads", formattedThreads);
        result.put("nextPageToken", threadResponse.getNextPageToken());
        result.put("resultSizeEstimate", threadResponse.getResultSizeEstimate());

        return result;
    }


    private Map<String, Object> createEmptyResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("threads", new ArrayList<>());
        result.put("nextPageToken", null);
        result.put("resultSizeEstimate", 0);
        return result;
    }

    @PostMapping("/setup-watch")
    public ResponseEntity<String> setupGmailWatch(
            @RequestParam(defaultValue = "utkarshs@claimsitdev.farmers.com") String userEmail) {
        try {
            // Create Gmail service for the specific user
            Gmail userGmailService = gmailServiceConfig.createGmailService(userEmail);

            WatchRequest watchRequest = new WatchRequest()
                    .setTopicName("projects/claimsitdevpoc/topics/gmail-watch-topic")
                    .setLabelIds(Collections.singletonList("INBOX"))
                    .setLabelFilterAction("include");

            WatchResponse watchResponse = userGmailService.users()
                    .watch(userEmail, watchRequest)  // Use actual email instead of "me"
                    .execute();

            logger.info("Watch setup successful for user: {}", userEmail);
            return ResponseEntity.ok("Watch created. Expiry: " + watchResponse.getExpiration());
        } catch (Exception e) {
            logger.error("Error setting up Gmail watch for user: {}", userEmail, e);
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    /*@PostMapping("/setup-watch")
    public ResponseEntity<String> setupGmailWatch(

            //@RequestParam String userEmail
            @RequestParam(defaultValue = "utkarshs@claimsitdev.farmers.com") String userEmail) {
        try {
            Gmail userGmailService = gmailServiceConfig.createGmailService(userEmail);

            WatchRequest watchRequest = new WatchRequest()
                    .setTopicName("projects/claimsitdevpoc/topics/email-notifications")
                    .setLabelIds(Collections.singletonList("INBOX"))
                    .setLabelFilterAction("include");

            WatchResponse watchResponse = userGmailService.users()
                    .watch(userEmail, watchRequest)
                    .execute();

            logger.info("Watch setup successful for user: {}", userEmail);
            return ResponseEntity.ok("Watch created. Expiry: " + watchResponse.getExpiration());
        } catch (Exception e) {
            logger.error("Error setting up Gmail watch for user: {}", userEmail, e);
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }*/




    @GetMapping("/user/{username}/emails")
    public ResponseEntity<Map<String, Object>> getEmails(
            @PathVariable String username,
            @RequestParam(required = false) String pageToken,
            @RequestParam(defaultValue = "20") Integer maxResults) {
        try {
            String fullEmail = username + "@claimsitdev.farmers.com";

            Gmail userGmailService;

            if(fullEmail.equals("devadmin@claimsitdev.farmers.com")){
                 userGmailService = gmailServiceConfig.createGmailService("me");
            }
            else{
                userGmailService = gmailServiceConfig.createGmailService(fullEmail);
            }

            Gmail.Users.Threads.List threadRequest = userGmailService.users().threads()
                    .list("me")
                    .setMaxResults(Long.valueOf(maxResults));

            if (pageToken != null && !pageToken.isEmpty()) {
                threadRequest.setPageToken(pageToken);
            }

            ListThreadsResponse threadResponse = threadRequest.execute();
            List<Map<String, Object>> formattedThreads = new ArrayList<>();

            if (threadResponse.getThreads() != null) {
                for (Thread thread : threadResponse.getThreads()) {
                    // Fetch full thread with all messages
                    Thread fullThread = userGmailService.users().threads()
                            .get("me", thread.getId())
                            .execute();

                    // Use LinkedHashMap to maintain insertion order
                    Map<String, Object> formattedThread = new LinkedHashMap<>();
                    formattedThread.put("id", fullThread.getId());  // Thread ID first
                    formattedThread.put("historyId", fullThread.getHistoryId());  // History ID second

                    List<Map<String, Object>> messages = new ArrayList<>();
                    if (fullThread.getMessages() != null) {
                        for (Message message : fullThread.getMessages()) {
                            messages.add(formatMessage(message,fullEmail));
                        }
                    }
                    formattedThread.put("messages", messages);  // Messages last

                    formattedThreads.add(formattedThread);
                }
            }

            // Use LinkedHashMap for the result as well to maintain order
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("threads", formattedThreads);
            result.put("nextPageToken", threadResponse.getNextPageToken());
            result.put("resultSizeEstimate", threadResponse.getResultSizeEstimate());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching emails", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/user/{username}/inbox-emails")
    public ResponseEntity<Map<String, Object>> getInboxEmails(
            @PathVariable String username,
            @RequestParam(required = false) String pageToken) {
        try {
            String fullEmail = username + "@claimsitdev.farmers.com";
            Gmail userGmailService = gmailServiceConfig.createGmailService(fullEmail);

            // Step 1: Get total count of emails in the Inbox
            Gmail.Users.Labels.Get labelRequest = userGmailService.users().labels().get(fullEmail, "INBOX");
            Label inboxLabel = labelRequest.execute();
            long totalInboxEmails = inboxLabel.getMessagesTotal();

            // Step 2: Fetch only Inbox emails with pagination
            Gmail.Users.Messages.List messageRequest = userGmailService.users().messages()
                    .list(fullEmail)
                    .setQ("label:INBOX") // Fetch only Inbox emails
                    .setMaxResults(20L); // Fetch 20 messages per request

            if (pageToken != null && !pageToken.isEmpty()) {
                messageRequest.setPageToken(pageToken);
            }

            ListMessagesResponse response = messageRequest.execute();
            List<Message> messages = response.getMessages();
            String newPageToken = response.getNextPageToken();

            if (newPageToken != null) {
                logger.info("Next Page Token: {}", newPageToken);
            } else {
                logger.info("No more pages available.");
            }

            List<Map<String, Object>> formattedMessages = new ArrayList<>();

            // Step 3: Fetch full email details & apply formatting
            for (Message message : messages) {
                Message fullMessage = userGmailService.users().messages()
                        .get("me", message.getId())
                        .setFormat("full")
                        .execute();

                Map<String, Object> formattedMessage = formatMessage(fullMessage, fullEmail);
                formattedMessages.add(formattedMessage);
            }

            // Step 4: Sort messages by date (newest first)
            formattedMessages.sort((msg1, msg2) -> {
                Instant date1 = extractDateFromMessage(msg1);
                Instant date2 = extractDateFromMessage(msg2);
                return date2.compareTo(date1);
            });

            // Step 5: Prepare response
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("totalInboxEmails", totalInboxEmails); // Total Inbox count
            result.put("messages", formattedMessages); // Inbox emails
            result.put("nextPageToken", newPageToken); // Next Page Token

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching Inbox emails", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/email/{messageId}")
    public ResponseEntity<Map<String, Object>> getEmail(@PathVariable String messageId) {
        try {
            Gmail userGmailService = gmailServiceConfig.createGmailService("me");
            Message message = userGmailService.users().messages()
                    .get("me", messageId)
                    .setFormat("full")
                    .execute();
            return ResponseEntity.ok(formatMessage(message,serviceAccountUser));
        } catch (Exception e) {
            logger.error("Error fetching email with ID: {}", messageId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private Map<String, Object> formatThread(Thread thread) throws IOException {
        Map<String, Object> formattedThread = new LinkedHashMap<>();
        formattedThread.put("id", thread.getId());
        formattedThread.put("historyId", thread.getHistoryId());

        List<Map<String, Object>> messages = new ArrayList<>();
        if (thread.getMessages() != null) {
            for (Message message : thread.getMessages()) {
                messages.add(formatMessage(message,serviceAccountUser));
            }
        }
        formattedThread.put("messages", messages);

        return formattedThread;
    }

    private Map<String, Object> formatMessage(Message message, String userEmail) throws IOException {
        Map<String, Object> formattedMessage = new HashMap<>();
        formattedMessage.put("id", message.getId());
        formattedMessage.put("threadId", message.getThreadId());
        formattedMessage.put("labelIds", message.getLabelIds());
        formattedMessage.put("snippet", message.getSnippet());

        MessagePart payload = message.getPayload();
        if (payload != null) {
            Map<String, Object> formattedPayload = new HashMap<>();
            formattedPayload.put("headers", formatHeaders(payload.getHeaders()));

            // Handle body content
            String emailContent = extractEmailContent(payload);
            if (emailContent != null) {
                Map<String, Object> bodyContent = new HashMap<>();
                bodyContent.put("data", emailContent);
                bodyContent.put("mimeType", payload.getMimeType());
                formattedPayload.put("body", bodyContent);
            }

            // Handle attachments
            List<Map<String, Object>> attachments = extractAttachments(payload, message.getId(), userEmail);
            if (!attachments.isEmpty()) {
                formattedPayload.put("attachments", attachments);
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

        // Handle simple text/html parts

        if (part.getBody() != null && part.getBody().getData() != null) {

            return part.getBody().getData();

            /*String decodedData = decodeBody(part.getBody().getData());

            if ("text/plain".equalsIgnoreCase(part.getMimeType())) {

                return convertTextToHtml(decodedData);

            } else if ("text/html".equalsIgnoreCase(part.getMimeType())) {

                return decodedData; // Already in HTML format

            }*/

        }

        // Handle multipart messages

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
                        (subPart.getBody().getData() != null ||
                                subPart.getBody().getAttachmentId() != null)) {
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
                        logger.error("Error processing attachment for message {}: {}",
                                messageId, e.getMessage());
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

    private Instant extractDateFromMessage(Map<String, Object> message) {
        try {
            List<Map<String, Object>> headers = (List<Map<String, Object>>)
                    ((Map<String, Object>) message.get("payload")).get("headers");

            for (Map<String, Object> header : headers) {
                if ("Date".equalsIgnoreCase((String) header.get("name"))) {
                    String dateStr = (String) header.get("value");
                    // Remove timezone name in parentheses
                    dateStr = dateStr.replaceAll("\\s*\\(.*\\)$", "");

                    try {
                        return ZonedDateTime.parse(dateStr,
                                DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                    } catch (DateTimeParseException e) {
                        // Fallback parsing
                        return Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(dateStr));
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error extracting date from message", e);
        }
        return Instant.MIN;
    }

    private Instant extractDateFromThread(Map<String, Object> thread) {
        try {
            List<Map<String, Object>> messages = (List<Map<String, Object>>) thread.get("messages");
            if (messages != null && !messages.isEmpty()) {
                Map<String, Object> latestMessage = messages.get(0); // Assuming first message is the latest
                return extractDateFromMessage(latestMessage);
            }
        } catch (Exception e) {
            logger.error("Error extracting date from thread: {}", thread.get("id"), e);
        }
        return Instant.MIN; // Return the earliest time if date extraction fails
    }




    // Optional: Add search functionality
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchEmails(
            @RequestParam String query,
            @RequestParam(required = false) String pageToken) {
        try {
            Gmail userGmailService = gmailServiceConfig.createGmailService("me");
            Gmail.Users.Messages.List request = userGmailService.users().messages()
                    .list("me")
                    .setQ(query)
                    .setMaxResults(Long.valueOf(MAX_RESULTS));

            if (pageToken != null && !pageToken.isEmpty()) {
                request.setPageToken(pageToken);
            }

            ListMessagesResponse response = request.execute();
            List<Map<String, Object>> emails = new ArrayList<>();

            if (response.getMessages() != null) {
                for (Message message : response.getMessages()) {
                    Message fullMessage = userGmailService.users().messages()
                            .get("me", message.getId())
                            .setFormat("full")
                            .execute();
                    emails.add(formatMessage(fullMessage,serviceAccountUser));
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("messages", emails);
            result.put("nextPageToken", response.getNextPageToken());
            result.put("resultSizeEstimate", response.getResultSizeEstimate());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error searching emails", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}