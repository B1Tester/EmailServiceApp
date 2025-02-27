/*
package com.farmers.ecom.email.controller;

import com.farmers.ecom.email.config.GmailServiceConfig;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
public class EmailRegexSearchController {

    private static final Logger logger = LoggerFactory.getLogger(EmailRegexSearchController.class);
    private final GmailServiceConfig gmailServiceConfig;
    private static final Pattern CLAIM_PATTERN = Pattern.compile("\\b\\d{10}-\\d\\b");
    private static final Pattern EXPOSURE_PATTERN = Pattern.compile("\\b\\d{10}-\\d(-\\d{1,2})?\\b");

    public EmailRegexSearchController(GmailServiceConfig gmailServiceConfig) {
        this.gmailServiceConfig = gmailServiceConfig;
    }

    @GetMapping("/api/gmail/regex-search")
    public ResponseEntity<Map<String, Object>> searchEmailsByRegex(
            @RequestParam String userEmail,
            @RequestParam(required = false) String pageToken) {
        try {
            Gmail userGmailService = gmailServiceConfig.createGmailService(userEmail);
            Gmail.Users.Messages.List request = userGmailService.users().messages()
                    .list(userEmail)
                    .setMaxResults(50L);

            if (pageToken != null && !pageToken.isEmpty()) {
                request.setPageToken(pageToken);
            }

            ListMessagesResponse response = request.execute();
            List<Map<String, Object>> matchedEmails = new ArrayList<>();

            if (response.getMessages() != null) {
                for (Message message : response.getMessages()) {
                    Message fullMessage = userGmailService.users().messages()
                            .get(userEmail, message.getId())
                            .setFormat("full")
                            .execute();

                    String content = extractEmailContent(fullMessage);
                    if (content != null && (containsPattern(content, CLAIM_PATTERN) || containsPattern(content, EXPOSURE_PATTERN))) {
                        Map<String, Object> emailData = new HashMap<>();
                        emailData.put("id", fullMessage.getId());
                        emailData.put("snippet", fullMessage.getSnippet());
                        emailData.put("matchedContent", getMatchedPatterns(content));
                        matchedEmails.add(emailData);
                    }
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("matchedEmails", matchedEmails);
            result.put("nextPageToken", response.getNextPageToken());

            return ResponseEntity.ok(result);
        } catch (IOException e) {
            logger.error("Error searching emails by regex", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private String extractEmailContent(Message message) {
        if (message.getPayload() == null) return null;
        StringBuilder contentBuilder = new StringBuilder();
        extractParts(message.getPayload(), contentBuilder);
        return contentBuilder.toString();
    }

    private void extractParts(MessagePart part, StringBuilder contentBuilder) {
        if (part.getBody() != null && part.getBody().getData() != null) {
            String decodedData = new String(Base64.getUrlDecoder().decode(part.getBody().getData()));
            contentBuilder.append(decodedData);
        }
        if (part.getParts() != null) {
            for (MessagePart subPart : part.getParts()) {
                extractParts(subPart, contentBuilder);
            }
        }
    }

    private boolean containsPattern(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find();
    }

    private List<String> getMatchedPatterns(String text) {
        List<String> matches = new ArrayList<>();
        Matcher claimMatcher = CLAIM_PATTERN.matcher(text);
        while (claimMatcher.find()) {
            matches.add("Claim: " + claimMatcher.group());
        }
        Matcher exposureMatcher = EXPOSURE_PATTERN.matcher(text);
        while (exposureMatcher.find()) {
            matches.add("Exposure: " + exposureMatcher.group());
        }
        return matches;
    }
}
*/

/*package com.farmers.ecom.email.controller;

import com.farmers.ecom.email.config.GmailServiceConfig;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Base64;

@RestController
public class EmailRegexSearchController {

    private static final Logger logger = LoggerFactory.getLogger(EmailRegexSearchController.class);
    private final GmailServiceConfig gmailServiceConfig;
    private static final Pattern CLAIM_PATTERN = Pattern.compile("\\b\\d{10}-\\d\\b");
    private static final Pattern EXPOSURE_PATTERN = Pattern.compile("\\b\\d{10}-\\d(-\\d{1,2})?\\b");

    public EmailRegexSearchController(GmailServiceConfig gmailServiceConfig) {
        this.gmailServiceConfig = gmailServiceConfig;
    }

    @GetMapping("/api/gmail/regex-search")
    public ResponseEntity<Map<String, Object>> searchEmailsByRegex(
            @RequestParam String userEmail,
            @RequestParam(required = false) String pageToken) {
        try {
            Gmail userGmailService = gmailServiceConfig.createGmailService(userEmail);
            Gmail.Users.Messages.List request = userGmailService.users().messages()
                    .list(userEmail)
                    .setMaxResults(50L);

            if (pageToken != null && !pageToken.isEmpty()) {
                request.setPageToken(pageToken);
            }

            ListMessagesResponse response = request.execute();
            List<Map<String, Object>> matchedEmails = new ArrayList<>();

            if (response.getMessages() != null) {
                for (Message message : response.getMessages()) {
                    Message fullMessage = userGmailService.users().messages()
                            .get(userEmail, message.getId())
                            .setFormat("full")
                            .execute();

                    String content = extractEmailContent(fullMessage.getPayload());
                    String subject = extractHeader(fullMessage.getPayload(), "Subject");
                    String from = extractHeader(fullMessage.getPayload(), "From");
                    String to = extractHeader(fullMessage.getPayload(), "To");

                    // Combine subject and body for pattern matching
                    String combinedContent = (subject != null ? subject + "\n" : "") + content;

                    List<String> matchedPatterns = getMatchedPatterns(combinedContent);

                    if (!matchedPatterns.isEmpty()) {
                        Map<String, Object> simplifiedMessage = new HashMap<>();
                        simplifiedMessage.put("id", fullMessage.getId());
                        simplifiedMessage.put("threadId", fullMessage.getThreadId());
                        simplifiedMessage.put("snippet", fullMessage.getSnippet());
                        simplifiedMessage.put("labelIds", fullMessage.getLabelIds());
                        simplifiedMessage.put("matchedContent", matchedPatterns);
                        simplifiedMessage.put("body", content);
                        simplifiedMessage.put("subject", subject);
                        simplifiedMessage.put("from", from);
                        simplifiedMessage.put("to", to);
                        simplifiedMessage.put("headers", extractHeaders(fullMessage.getPayload()));

                        matchedEmails.add(simplifiedMessage);
                    }
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("threads", matchedEmails);
            result.put("nextPageToken", response.getNextPageToken());
            result.put("resultSizeEstimate", matchedEmails.size());

            return ResponseEntity.ok(result);
        } catch (IOException e) {
            logger.error("Error searching emails by regex", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private String extractEmailContent(MessagePart part) {
        if (part == null) return null;

        StringBuilder contentBuilder = new StringBuilder();
        extractParts(part, contentBuilder);
        return contentBuilder.toString();
    }

    private void extractParts(MessagePart part, StringBuilder contentBuilder) {
        if (part.getBody() != null && part.getBody().getData() != null) {
            String decodedData = new String(Base64.getUrlDecoder().decode(part.getBody().getData()));
            contentBuilder.append(decodedData);
        }
        if (part.getParts() != null) {
            for (MessagePart subPart : part.getParts()) {
                extractParts(subPart, contentBuilder);
            }
        }
    }

    private List<Map<String, String>> extractHeaders(MessagePart payload) {
        List<Map<String, String>> headersList = new ArrayList<>();
        if (payload != null && payload.getHeaders() != null) {
            for (MessagePartHeader header : payload.getHeaders()) {
                Map<String, String> headerMap = new HashMap<>();
                headerMap.put("name", header.getName());
                headerMap.put("value", header.getValue());
                headersList.add(headerMap);
            }
        }
        return headersList;
    }

    private String extractHeader(MessagePart payload, String headerName) {
        if (payload != null && payload.getHeaders() != null) {
            for (MessagePartHeader header : payload.getHeaders()) {
                if (headerName.equalsIgnoreCase(header.getName())) {
                    return header.getValue();
                }
            }
        }
        return null;
    }

    private List<String> getMatchedPatterns(String text) {
        List<String> matches = new ArrayList<>();
        Matcher claimMatcher = CLAIM_PATTERN.matcher(text);
        while (claimMatcher.find()) {
            matches.add("Claim: " + claimMatcher.group());
        }
        Matcher exposureMatcher = EXPOSURE_PATTERN.matcher(text);
        while (exposureMatcher.find()) {
            matches.add("Exposure: " + exposureMatcher.group());
        }
        return matches;
    }
}*/

package com.farmers.ecom.email.controller;

import com.farmers.ecom.email.config.GmailServiceConfig;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Base64;

@RestController
public class EmailRegexSearchController {

    private static final Logger logger = LoggerFactory.getLogger(EmailRegexSearchController.class);
    private final GmailServiceConfig gmailServiceConfig;
    private static final Pattern CLAIM_PATTERN = Pattern.compile("\\b\\d{10}-\\d\\b");
    private static final Pattern EXPOSURE_PATTERN = Pattern.compile("\\b\\d{10}-\\d(-\\d{1,2})?\\b");

    public EmailRegexSearchController(GmailServiceConfig gmailServiceConfig) {
        this.gmailServiceConfig = gmailServiceConfig;
    }

    @GetMapping("/api/gmail/regex-search")
    public ResponseEntity<Map<String, Object>> searchEmailsByRegex(
            @RequestParam String userEmail,
            @RequestParam(required = false) String pageToken) {
        try {
            Gmail userGmailService = gmailServiceConfig.createGmailService(userEmail);
            Gmail.Users.Messages.List request = userGmailService.users().messages()
                    .list(userEmail)
                    .setMaxResults(50L);

            if (pageToken != null && !pageToken.isEmpty()) {
                request.setPageToken(pageToken);
            }

            ListMessagesResponse response = request.execute();
            List<Map<String, Object>> matchedEmails = new ArrayList<>();

            if (response.getMessages() != null) {
                for (Message message : response.getMessages()) {
                    Message fullMessage = userGmailService.users().messages()
                            .get(userEmail, message.getId())
                            .setFormat("full")
                            .execute();

                    String content = extractEmailContent(fullMessage.getPayload());
                    String subject = extractHeader(fullMessage.getPayload(), "Subject");
                    String from = extractHeader(fullMessage.getPayload(), "From");
                    String to = extractHeader(fullMessage.getPayload(), "To");

                    // Combine subject and body for pattern matching
                    String combinedContent = (subject != null ? subject + "\n" : "") + content;

                    List<String> matchedPatterns = getMatchedPatterns(combinedContent);

                    if (!matchedPatterns.isEmpty()) {
                        Map<String, Object> simplifiedMessage = new HashMap<>();
                        simplifiedMessage.put("id", fullMessage.getId());
                        simplifiedMessage.put("threadId", fullMessage.getThreadId());
                        simplifiedMessage.put("snippet", fullMessage.getSnippet());
                        simplifiedMessage.put("labelIds", fullMessage.getLabelIds());
                        simplifiedMessage.put("matchedContent", matchedPatterns);
                        simplifiedMessage.put("body", content);
                        simplifiedMessage.put("subject", subject);
                        simplifiedMessage.put("from", from);
                        simplifiedMessage.put("to", to);
                        simplifiedMessage.put("headers", extractHeaders(fullMessage.getPayload()));

                        matchedEmails.add(simplifiedMessage);
                    }
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("threads", matchedEmails);
            result.put("nextPageToken", response.getNextPageToken());
            result.put("resultSizeEstimate", matchedEmails.size());

            return ResponseEntity.ok(result);
        } catch (IOException e) {
            logger.error("Error searching emails by regex", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private String extractEmailContent(MessagePart part) {
        if (part == null) return null;

        StringBuilder contentBuilder = new StringBuilder();
        extractParts(part, contentBuilder);
        return contentBuilder.toString();
    }

    private void extractParts(MessagePart part, StringBuilder contentBuilder) {
        if (part.getBody() != null && part.getBody().getData() != null) {
            String decodedData = new String(Base64.getUrlDecoder().decode(part.getBody().getData()));
            contentBuilder.append(decodedData);
        }
        if (part.getParts() != null) {
            for (MessagePart subPart : part.getParts()) {
                extractParts(subPart, contentBuilder);
            }
        }
    }

    private List<Map<String, String>> extractHeaders(MessagePart payload) {
        List<Map<String, String>> headersList = new ArrayList<>();
        if (payload != null && payload.getHeaders() != null) {
            for (MessagePartHeader header : payload.getHeaders()) {
                Map<String, String> headerMap = new HashMap<>();
                headerMap.put("name", header.getName());
                headerMap.put("value", header.getValue());
                headersList.add(headerMap);
            }
        }
        return headersList;
    }

    private String extractHeader(MessagePart payload, String headerName) {
        if (payload != null && payload.getHeaders() != null) {
            for (MessagePartHeader header : payload.getHeaders()) {
                if (headerName.equalsIgnoreCase(header.getName())) {
                    return header.getValue();
                }
            }
        }
        return null;
    }

    private List<String> getMatchedPatterns(String text) {
        List<String> matches = new ArrayList<>();
        Matcher claimMatcher = CLAIM_PATTERN.matcher(text);
        while (claimMatcher.find()) {
            matches.add("Claim: " + claimMatcher.group());
        }
        Matcher exposureMatcher = EXPOSURE_PATTERN.matcher(text);
        while (exposureMatcher.find()) {
            matches.add("Exposure: " + exposureMatcher.group());
        }
        return matches;
    }
}







