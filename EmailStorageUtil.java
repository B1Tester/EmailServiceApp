package com.farmers.ecom.email.util;

import com.farmers.ecom.email.config.GmailServiceConfig;
import com.farmers.ecom.email.service.DomainUserService;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.Document;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Image;
import com.itextpdf.text.DocumentException;

import jakarta.activation.DataHandler;
import org.jsoup.Jsoup;
import org.jsoup.Jsoup;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.util.ByteArrayDataSource;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class EmailStorageUtil {
    private static final Logger logger = LoggerFactory.getLogger(EmailStorageUtil.class);
    private static final String BASE_STORAGE_PATH = "email_storage";
    private Map<String, String> inlineImages = new HashMap<>();


    private final GmailServiceConfig gmailServiceConfig;



    public EmailStorageUtil(GmailServiceConfig gmailServiceConfig) {
        this.gmailServiceConfig = gmailServiceConfig;
    }


    public void saveEmail(Message message, String userEmail) {
        try {
            Gmail gmailService = gmailServiceConfig.createGmailService(userEmail);
            String datePath = createDateBasedPath();
            createStorageDirectories(datePath);
            String baseFilename = generateBaseFilename(message, userEmail);

            // Get attachment information first
            List<AttachmentInfo> attachments = extractAttachmentInfo(message);

            // Save PDF with attachment information
            saveToPdf(message, datePath + "/" + baseFilename + ".pdf", attachments,userEmail,gmailService);

            // Save EML with attachments
            saveToEml(message, datePath + "/" + baseFilename + ".eml",gmailService,userEmail);

            // Save attachments with new naming convention
            saveAttachments(message, datePath + "/attachments", baseFilename, userEmail, gmailService);

            logger.info("Email saved successfully for user: {} with base filename: {}", userEmail, baseFilename);
        } catch (Exception e) {
            logger.error("Error saving email for user {}: {}", userEmail, e.getMessage(), e);
        }
    }

    private static class AttachmentInfo {
        String filename;
        long size;
        String mimeType;

        AttachmentInfo(String filename, long size, String mimeType) {
            this.filename = filename;
            this.size = size;
            this.mimeType = mimeType;
        }
    }

    private String createDateBasedPath() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
        return BASE_STORAGE_PATH + "/" + sdf.format(new Date());
    }

    private void createStorageDirectories(String datePath) throws IOException {
        Files.createDirectories(Paths.get(datePath));
        Files.createDirectories(Paths.get(datePath + "/attachments"));
    }

    private String generateBaseFilename(Message message, String userEmail) {
        String username = userEmail.split("@")[0];
        return String.format("received%s%s", username, message.getId());
    }

    private List<AttachmentInfo> extractAttachmentInfo(Message message) {
        List<AttachmentInfo> attachments = new ArrayList<>();
        if (message.getPayload().getParts() != null) {
            extractAttachmentInfoRecursive(message.getPayload().getParts(), attachments);
        }
        return attachments;
    }

    private void extractAttachmentInfoRecursive(List<MessagePart> parts, List<AttachmentInfo> attachments) {
        for (MessagePart part : parts) {
            if (part.getFilename() != null && !part.getFilename().isEmpty()) {
                long size = part.getBody().getSize() != null ? part.getBody().getSize() : 0;
                attachments.add(new AttachmentInfo(part.getFilename(), size, part.getMimeType()));
            }
            if (part.getParts() != null) {
                extractAttachmentInfoRecursive(part.getParts(), attachments);
            }
        }
    }

    /*private void saveToPdf(Message message, String filepath, List<AttachmentInfo> attachments) throws IOException, DocumentException {
        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(filepath));
        document.open();

        document.addTitle(getHeaderValue(message, "Subject"));
        document.addAuthor(getHeaderValue(message, "From"));
        document.addCreationDate();

        addHeadersToPdf(document, message);

        // Add attachment information
        if (!attachments.isEmpty()) {
            Font attachmentFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD);
            document.add(new Paragraph("\nAttachments:", attachmentFont));
            for (AttachmentInfo attachment : attachments) {
                document.add(new Paragraph(String.format("- %s (Type: %s, Size: %d bytes)",
                        attachment.filename, attachment.mimeType, attachment.size)));
            }
            document.add(new Paragraph("\n"));
        }

        String content = extractEmailContent(message.getPayload());
        if (content != null) {
            content = content.replaceAll("<[^>]+>", "");
            document.add(new Paragraph(content));
        }

        document.close();
    }*/



    /*private void saveToPdf(Message message, String filepath, List<AttachmentInfo> attachments, String userEmail, Gmail gmailService) throws IOException {
        // Extract email content and inline images
        Map<String, String> inlineImages = new HashMap<>();
        saveAttachmentsRecursive(gmailService, userEmail, message.getPayload().getParts(), "email_storage/attachments",
                "user", message.getId(), inlineImages);

        String emailContent = extractEmailContent(message.getPayload(), inlineImages);
        if (emailContent == null) {
            emailContent = "<p>No email content available.</p>";
        }

        // Replace inline image paths in HTML
        for (Map.Entry<String, String> entry : inlineImages.entrySet()) {
            String cid = entry.getKey();
            String imagePath = new File(entry.getValue()).toURI().toString(); // Convert to absolute file path
            emailContent = emailContent.replace("cid:" + cid, imagePath);
        }

        // Convert HTML to PDF using iText 7
        try (FileOutputStream fos = new FileOutputStream(filepath)) {
            HtmlConverter.convertToPdf(emailContent, fos);
        } catch (Exception e) {
            logger.error("Error while converting email to PDF: {}", e.getMessage());
        }
    }*/

    private String extractEmailAddress(String headerValue) {
        if (headerValue == null) return "N/A";

        // Pattern to match email addresses in format: "Name <email@example.com>"
        Pattern pattern = Pattern.compile("<([^>]+)>");
        Matcher matcher = pattern.matcher(headerValue);

        if (matcher.find()) {
            return matcher.group(1); // Return just the email part
        } else {
            // If no <email> format, return the whole string (might be just the email)
            return headerValue;
        }
    }

    private void saveToPdf(Message message, String filepath, List<AttachmentInfo> attachments, String userEmail, Gmail gmailService) throws IOException {
        // Extract email content and inline images
        Map<String, String> inlineImages = new HashMap<>();
        saveAttachmentsRecursive(gmailService, userEmail, message.getPayload().getParts(), "email_storage/attachments",
                "user", message.getId(), inlineImages);

        // Get raw header values
        String from = getHeader(message.getPayload().getHeaders(), "From");
        String to = getHeader(message.getPayload().getHeaders(), "To");
        String subject = getHeader(message.getPayload().getHeaders(), "Subject");
        String date = getHeader(message.getPayload().getHeaders(), "Date");

        // Build metadata section in email-like format
        StringBuilder metadataHtml = new StringBuilder();
        metadataHtml.append("<div style='font-family: monospace; margin-bottom: 20px; border-bottom: 1px solid #ccc; padding-bottom: 10px;'>");
        metadataHtml.append("<pre style='margin: 0; font-size: 14px;'>");
        metadataHtml.append("From: ").append(from).append("\n");
        metadataHtml.append("To: ").append(to).append("\n");
        metadataHtml.append("Subject: ").append(subject).append("\n");
        metadataHtml.append("Date: ").append(date);
        metadataHtml.append("</pre>");
        metadataHtml.append("</div>");

        // Extract email content (fix to prevent duplication)
        String emailContent = extractEmailContentNoDuplication(message.getPayload(), inlineImages);
        if (emailContent == null || emailContent.trim().isEmpty()) {
            emailContent = "<p>No email content available.</p>";
        }

        // Combine metadata and content
        String fullHtml = "<html><body>" + metadataHtml.toString() + emailContent + "</body></html>";

        // Replace inline image paths in HTML
        for (Map.Entry<String, String> entry : inlineImages.entrySet()) {
            String cid = entry.getKey();
            String imagePath = new File(entry.getValue()).toURI().toString(); // Convert to absolute file path
            fullHtml = fullHtml.replace("cid:" + cid, imagePath);
        }

        // Convert HTML to PDF using iText 7
        try (FileOutputStream fos = new FileOutputStream(filepath)) {
            HtmlConverter.convertToPdf(fullHtml, fos);
        } catch (Exception e) {
            logger.error("Error while converting email to PDF: {}", e.getMessage());
        }
    }

    // Helper method to get header values
    private String getHeader(List<MessagePartHeader> headers, String name) {
        if (headers != null) {
            for (MessagePartHeader header : headers) {
                if (header.getName().equalsIgnoreCase(name)) {
                    return header.getValue();
                }
            }
        }
        return "N/A";
    }





    private boolean isHtmlEmail(MessagePart payload) {
        return payload.getMimeType() != null && payload.getMimeType().toLowerCase().contains("html");
    }

    private List<Chunk> parseHtmlToPdfChunks(String html) {
        Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 12, Font.NORMAL);
        Font strikeFont = FontFactory.getFont(FontFactory.HELVETICA, 12, Font.STRIKETHRU);

        List<Chunk> chunks = new ArrayList<>();
        Pattern pattern = Pattern.compile("(<s>|<strike>|<del>)(.+?)(</s>|</strike>|</del>)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);

        int lastEnd = 0;
        while (matcher.find()) {
            // Add normal text before the strikethrough text
            if (matcher.start() > lastEnd) {
                chunks.add(new Chunk(html.substring(lastEnd, matcher.start()), normalFont));
            }

            // Add strikethrough text with correct styling
            chunks.add(new Chunk(matcher.group(2), strikeFont));

            lastEnd = matcher.end();
        }

        // Add remaining normal text after last match
        if (lastEnd < html.length()) {
            chunks.add(new Chunk(html.substring(lastEnd), normalFont));
        }

        return chunks;
    }




    private void addHeadersToPdf(Document document, Message message) throws DocumentException {
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD);

        Arrays.asList("From", "To", "Subject", "Date").forEach(headerName -> {
            try {
                String value = getHeaderValue(message, headerName);
                if (value != null) {
                    document.add(new Paragraph(headerName + ": " + value, headerFont));
                }
            } catch (DocumentException e) {
                logger.error("Error adding header to PDF: {}", headerName, e);
            }
        });

        document.add(new Paragraph("\n"));
    }

    private void saveToEml(Message message, String filepath, Gmail gmailService, String userId) throws IOException, MessagingException {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);
        MimeMessage mimeMessage = new MimeMessage(session);

        try {
            // Add headers
            if (message.getPayload().getHeaders() != null) {
                for (MessagePartHeader header : message.getPayload().getHeaders()) {
                    if (!header.getName().equalsIgnoreCase("Content-Type")) {
                        mimeMessage.addHeader(header.getName(), header.getValue());
                    }
                }
            }

            // Define boundary for related content (text + images)
            MimeMultipart relatedMultipart = new MimeMultipart("related");
            MimeBodyPart htmlPart = new MimeBodyPart();

            Map<String, String> inlineImages = new HashMap<>();
            saveAttachmentsRecursive(gmailService, userId, message.getPayload().getParts(), "email_storage/attachments", "user", message.getId(), inlineImages);

            // Extract full HTML content
            String content = extractEmailContentNoDuplication(message.getPayload(), inlineImages);

            // Replace inline images with correct `cid:`
            if (content != null) {
                org.jsoup.nodes.Document htmlDoc = org.jsoup.Jsoup.parse(content);
                for (org.jsoup.nodes.Element img : htmlDoc.select("img")) {
                    String src = img.attr("src");
                    if (inlineImages.containsKey(src)) {
                        img.attr("src", "cid:" + inlineImages.get(src));
                    }
                }
                String formattedHtml = htmlDoc.body().html();
                htmlPart.setContent(formattedHtml, "text/html; charset=UTF-8");
                relatedMultipart.addBodyPart(htmlPart);
            }

            // Attach inline images properly
            for (Map.Entry<String, String> entry : inlineImages.entrySet()) {
                String cid = entry.getKey();
                String imagePath = entry.getValue();

                MimeBodyPart imagePart = new MimeBodyPart();
                imagePart.attachFile(imagePath);
                imagePart.setDisposition(MimeBodyPart.INLINE);
                imagePart.setHeader("Content-ID", "<" + cid + ">");
                relatedMultipart.addBodyPart(imagePart);
            }

            mimeMessage.setContent(relatedMultipart);
            mimeMessage.saveChanges();

            try (FileOutputStream fos = new FileOutputStream(filepath)) {
                mimeMessage.writeTo(fos);
            }

            logger.info("Successfully saved EML file: {}", filepath);

        } catch (Exception e) {
            logger.error("Error creating EML file: {}", e.getMessage());
            createFallbackEml(session, filepath, e.getMessage());
        }
    }













    private void addPartsToMultipart(Gmail gmailService, String userId, Message message, MimeMultipart multipart, List<MessagePart> parts) throws MessagingException, IOException {
        for (MessagePart part : parts) {
            MimeBodyPart bodyPart = new MimeBodyPart();

            // Handle nested multipart (for alternative content)
            if (part.getParts() != null && !part.getParts().isEmpty()) {
                MimeMultipart nestedMultipart = new MimeMultipart("related");
                addPartsToMultipart(gmailService, userId, message, nestedMultipart, part.getParts());
                bodyPart.setContent(nestedMultipart);
                multipart.addBodyPart(bodyPart);
                continue;
            }

            // Download attachments if needed
            byte[] content = null;
            if (part.getBody() != null) {
                if (part.getBody().getAttachmentId() != null) {
                    MessagePartBody attachmentBody = gmailService.users().messages().attachments()
                            .get(userId, message.getId(), part.getBody().getAttachmentId())
                            .execute();
                    content = Base64.getUrlDecoder().decode(attachmentBody.getData());
                } else if (part.getBody().getData() != null) {
                    content = Base64.getUrlDecoder().decode(part.getBody().getData());
                }
            }

            // Set body content (text, HTML, or image)
            if (content != null) {
                if (part.getFilename() != null) {
                    // Attach inline image or other attachments
                    bodyPart.setDataHandler(new DataHandler(new ByteArrayDataSource(content, part.getMimeType())));
                    bodyPart.setFileName(part.getFilename());
                } else {
                    bodyPart.setText(new String(content, StandardCharsets.UTF_8), "UTF-8");
                }
            }

            // Add headers
            for (MessagePartHeader header : part.getHeaders()) {
                bodyPart.setHeader(header.getName(), header.getValue());
            }

            multipart.addBodyPart(bodyPart);
        }
    }


    private void saveAttachments(Message message, String attachmentPath, String baseFilename,
                                 String userEmail, Gmail gmailService) throws IOException {
        if (message.getPayload().getParts() != null) {
            String username = userEmail.split("@")[0];
            saveAttachmentsRecursive(gmailService,userEmail,message.getPayload().getParts(), attachmentPath,
                    username, message.getId(),inlineImages);
        }
    }




    private void saveAttachmentsRecursive(Gmail gmailService, String userId, List<MessagePart> parts,
                                          String attachmentPath, String username, String messageId,
                                          Map<String, String> inlineImages) throws IOException {
        for (MessagePart part : parts) {
            if (part.getFilename() != null && !part.getFilename().isEmpty()) {
                String extension = "";
                int lastDot = part.getFilename().lastIndexOf('.');
                if (lastDot > 0) {
                    extension = part.getFilename().substring(lastDot);
                }

                // 🔹 Sanitize filename to remove invalid characters
                String safeFilename = part.getFilename().replaceAll("[^a-zA-Z0-9.\\-_]", "_");

                // Ensure extension is retained
                String attachmentFilename = String.format("Attmtreceived%s_%s_%s", username, messageId, safeFilename);

                //String attachmentFilename = String.format("Attmtreceived%s_%s%s", username, messageId, extension);

                String fullPath = Paths.get(attachmentPath, attachmentFilename).toString();



                // 🔹 Ensure the directory exists
                Files.createDirectories(Paths.get(attachmentPath));

                byte[] attachmentData = null;

                // 🔹 Check if attachment needs to be downloaded
                if (part.getBody().getAttachmentId() != null) {
                    logger.info("Downloading attachment with ID: {}", part.getBody().getAttachmentId());

                    // Fetch the attachment from Gmail API
                    MessagePartBody attachmentBody = gmailService.users().messages().attachments()
                            .get(userId, messageId, part.getBody().getAttachmentId())
                            .execute();

                    if (attachmentBody != null && attachmentBody.getData() != null) {
                        attachmentData = Base64.getUrlDecoder().decode(attachmentBody.getData());
                    }
                } else if (part.getBody().getData() != null) {
                    attachmentData = Base64.getUrlDecoder().decode(part.getBody().getData());
                }

                if (attachmentData != null) {
                    // 🔹 Write file only after confirming the directory exists
                    Files.write(Paths.get(fullPath), attachmentData);
                    logger.info("Saved attachment: {}", fullPath);
                } else {
                    logger.error("Failed to download attachment: {}", part.getFilename());
                    continue;
                }

                // 🔹 Handle inline images
                if (part.getMimeType() != null && part.getMimeType().startsWith("image/")) {
                    for (MessagePartHeader header : part.getHeaders()) {
                        if ("Content-ID".equalsIgnoreCase(header.getName())) {
                            String contentId = header.getValue().replaceAll("[<>]", ""); // Remove angle brackets
                            inlineImages.put(contentId, fullPath);
                            logger.info("Stored inline image mapping: {} -> {}", contentId, fullPath);
                            break;
                        }
                    }
                }
            }

            if (part.getParts() != null) {
                saveAttachmentsRecursive(gmailService, userId, part.getParts(), attachmentPath, username, messageId, inlineImages);
            }
        }
    }




    private void createFallbackEml(Session session, String filepath, String errorMessage)
            throws MessagingException, IOException {
        MimeMessage fallbackMessage = new MimeMessage(session);
        fallbackMessage.setSubject("Email Content (Fallback)");
        fallbackMessage.setText("Original email could not be fully reconstructed.\n" +
                "Error: " + errorMessage);
        fallbackMessage.saveChanges();
        try (FileOutputStream fos = new FileOutputStream(filepath)) {
            fallbackMessage.writeTo(fos);
        }
    }


    /*private String extractEmailContent(MessagePart part, Map<String, String> inlineImages) {
        if (part == null) return null;

        if (part.getMimeType() != null && part.getMimeType().startsWith("multipart/")) {
            StringBuilder emailContent = new StringBuilder();

            if (part.getParts() != null) {
                for (MessagePart subPart : part.getParts()) {
                    String extractedContent = extractEmailContent(subPart, inlineImages);
                    if (extractedContent != null) {
                        emailContent.append(extractedContent);
                    }
                }
            }
            return emailContent.toString();
        }

        if (part.getMimeType() != null && part.getMimeType().startsWith("text/")) {
            if (part.getBody() != null && part.getBody().getData() != null) {
                try {
                    byte[] decodedBytes = Base64.getUrlDecoder().decode(part.getBody().getData());
                    String rawContent = new String(decodedBytes, StandardCharsets.UTF_8);

                    // Parse as full HTML
                    org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(rawContent);

                    // Fix inline image paths
                    org.jsoup.select.Elements imgTags = doc.select("img");
                    for (org.jsoup.nodes.Element img : imgTags) {
                        String src = img.attr("src");
                        if (src.startsWith("cid:")) {
                            String cid = src.substring(4);
                            if (inlineImages.containsKey(cid)) {
                                img.attr("src", "cid:" + cid);  // Ensure correct CID reference
                            }
                        }
                    }

                    return doc.body().html(); // Return full email body with fixed images

                } catch (IllegalArgumentException e) {
                    logger.error("Error decoding email content: {}", e.getMessage());
                }
            }
        }

        return null;
    }*/






    private String extractEmailContentNoDuplication(MessagePart part, Map<String, String> inlineImages) {
        if (part == null) return null;

        // First, try to find HTML content
        String htmlContent = findContentByMimeType(part, "text/html", inlineImages);
        if (htmlContent != null && !htmlContent.trim().isEmpty()) {
            return htmlContent;
        }

        // Fall back to plain text if no HTML
        String plainText = findContentByMimeType(part, "text/plain", inlineImages);
        if (plainText != null && !plainText.trim().isEmpty()) {
            return "<pre>" + escapeHtml(plainText) + "</pre>";
        }

        return null;
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String findContentByMimeType(MessagePart part, String mimeType, Map<String, String> inlineImages) {
        if (part.getMimeType() != null && part.getMimeType().equals(mimeType)) {
            if (part.getBody() != null && part.getBody().getData() != null) {
                try {
                    byte[] decodedBytes = Base64.getUrlDecoder().decode(part.getBody().getData());
                    String content = new String(decodedBytes, StandardCharsets.UTF_8);

                    if (mimeType.equals("text/html")) {
                        // Parse as full HTML
                        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(content);

                        // Fix inline image paths
                        org.jsoup.select.Elements imgTags = doc.select("img");
                        for (org.jsoup.nodes.Element img : imgTags) {
                            String src = img.attr("src");
                            if (src.startsWith("cid:")) {
                                String cid = src.substring(4);
                                if (inlineImages.containsKey(cid)) {
                                    img.attr("src", "cid:" + cid);
                                }
                            }
                        }

                        return doc.body().html();
                    }

                    return content;
                } catch (IllegalArgumentException e) {
                    logger.error("Error decoding email content: {}", e.getMessage());
                }
            }
            return null;
        }

        if (part.getParts() != null) {
            for (MessagePart subPart : part.getParts()) {
                String result = findContentByMimeType(subPart, mimeType, inlineImages);
                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }









    /*private String extractEmailContent(MessagePart part) {
        if (part == null) return null;

        // For multipart messages, recursively search for text content
        if (part.getMimeType() != null && part.getMimeType().startsWith("multipart/")) {
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

        // For text parts, decode the content
        if (part.getMimeType() != null && part.getMimeType().startsWith("text/")) {
            if (part.getBody() != null && part.getBody().getData() != null) {
                try {
                    byte[] decodedBytes = Base64.getUrlDecoder().decode(part.getBody().getData());
                    return new String(decodedBytes, StandardCharsets.UTF_8);
                } catch (IllegalArgumentException e) {
                    logger.error("Error decoding email content: {}", e.getMessage());
                }
            }
        }

        return null;
    }*/

    /*private String getHeaderValue(Message message, String headerName) {
        return message.getPayload().getHeaders().stream()
                .filter(header -> header.getName().equalsIgnoreCase(headerName))
                .findFirst()
                .map(MessagePartHeader::getValue)
                .orElse(null);
    }*/

    private boolean isTextType(String mimeType) {
        return mimeType != null && mimeType.toLowerCase().startsWith("text/");
    }

    private boolean isMultipartType(String mimeType) {
        return mimeType != null && mimeType.toLowerCase().startsWith("multipart/");
    }

    public void saveSentEmail(Message message, String userEmail) {
        try {
            if (message == null) {
                logger.error("Cannot save sent email: Message object is null for user {}", userEmail);
                return;
            }

            String datePath = createDateBasedPath();
            createStorageDirectories(datePath);
            String baseFilename = generateSentFilename(message, userEmail);

            // Save PDF (without attachment info since it's a sent email)
            saveSentToPdf(message, datePath + "/" + baseFilename + ".pdf");

            // Save EML
            //saveToEml(message, datePath + "/" + baseFilename + ".eml");

            logger.info("Sent email saved successfully for user: {} with base filename: {}", userEmail, baseFilename);
        } catch (Exception e) {
            logger.error("Error saving sent email for user {}: {}", userEmail, e.getMessage(), e);
        }
    }

    private String generateSentFilename(Message message, String userEmail) {
        String username = userEmail.split("@")[0];
        return String.format("sent_%s_%s", username, message.getId());
    }

    private void saveSentToPdf(Message message, String filepath) throws IOException, DocumentException {
        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(filepath));
        document.open();

        try {
            // Safely get message payload
            MessagePart payload = message.getPayload();
            if (payload == null) {
                logger.warn("Message payload is null, creating minimal PDF with available information");
                document.add(new Paragraph("Email ID: " + message.getId()));
                document.add(new Paragraph("Note: Full email content unavailable"));
                document.close();
                return;
            }

            // Add metadata if possible
            String subject = getHeaderValue(message, "Subject");
            String from = getHeaderValue(message, "From");
            if (subject != null) document.addTitle(subject);
            if (from != null) document.addAuthor(from);
            document.addCreationDate();

            addHeadersToPdf(document, message);

            String content = extractEmailContentNoDuplication(payload,inlineImages);
            if (content != null) {
                content = content.replaceAll("<[^>]+>", "");
                document.add(new Paragraph(content));
            } else {
                document.add(new Paragraph("No content available"));
            }
        } finally {
            document.close();
        }
    }

    private String getHeaderValue(Message message, String headerName) {
        if (message.getPayload() == null || message.getPayload().getHeaders() == null) {
            logger.warn("Unable to get header value for '{}': payload or headers are null", headerName);
            return null;
        }

        return message.getPayload().getHeaders().stream()
                .filter(header -> header.getName().equalsIgnoreCase(headerName))
                .findFirst()
                .map(MessagePartHeader::getValue)
                .orElse(null);
    }

}