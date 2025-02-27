package com.farmers.ecom.email.controller;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfWriter;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.List;

@RestController
@RequestMapping("/api/gmail/export")
public class EmailExportController {
    private static final Logger logger = LoggerFactory.getLogger(EmailExportController.class);
    private final EmailFetchController emailFetchController;

    public EmailExportController(EmailFetchController emailFetchController) {
        this.emailFetchController = emailFetchController;
    }

    @GetMapping("/pdf/{messageId}")
    public ResponseEntity<ByteArrayResource> exportToPdf(@PathVariable String messageId) {
        logger.info("Received request to export email to PDF with messageId: {}", messageId);

        var emailResponse = emailFetchController.getEmail(messageId);
        if (!emailResponse.hasBody()) {
            logger.warn("Email not found for messageId: {}", messageId);
            return ResponseEntity.notFound().build();
        }

        try {
            Map<String, Object> email = emailResponse.getBody();
            if (email == null) {
                logger.error("Email response body is null for messageId: {}", messageId);
                return ResponseEntity.internalServerError().build();
            }

            byte[] pdfBytes = convertToPdf(email);
            var resource = new ByteArrayResource(pdfBytes);

            logger.info("PDF successfully generated for messageId: {}", messageId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment;filename=email_%s.pdf".formatted(messageId))
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(pdfBytes.length)
                    .body(resource);
        } catch (Exception e) {
            logger.error("Error while exporting email to PDF for messageId: {}", messageId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/eml/{messageId}")
    public ResponseEntity<ByteArrayResource> exportToEml(@PathVariable String messageId) {
        logger.info("Received request to export email to EML with messageId: {}", messageId);

        var emailResponse = emailFetchController.getEmail(messageId);
        if (!emailResponse.hasBody()) {
            logger.warn("Email not found for messageId: {}", messageId);
            return ResponseEntity.notFound().build();
        }

        try {
            Map<String, Object> email = emailResponse.getBody();
            if (email == null) {
                logger.error("Email response body is null for messageId: {}", messageId);
                return ResponseEntity.internalServerError().build();
            }

            byte[] emlBytes = convertToEml(email);
            var resource = new ByteArrayResource(emlBytes);

            logger.info("EML successfully generated for messageId: {}", messageId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment;filename=email_%s.eml".formatted(messageId))
                    .contentType(MediaType.parseMediaType("message/rfc822"))
                    .contentLength(emlBytes.length)
                    .body(resource);
        } catch (Exception e) {
            logger.error("Error while exporting email to EML for messageId: {}", messageId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private byte[] convertToPdf(Map<String, Object> email) throws DocumentException, IOException {
        try (var baos = new ByteArrayOutputStream()) {
            var document = new Document();
            PdfWriter.getInstance(document, baos);
            document.open();

            // Convert internal date to string if it's a Long
            Object internalDate = email.get("internalDate");
            String dateStr = internalDate instanceof Long
                    ? new Date((Long) internalDate).toString()
                    : String.valueOf(internalDate);

            document.addTitle("Email: " + getHeaderValue(email, "Subject"));
            document.addAuthor(getHeaderValue(email, "From"));

            addEmailContentToPdf(document, email, dateStr);
            document.close();

            return baos.toByteArray();
        } catch (Exception e) {
            logger.error("Error during PDF conversion", e);
            throw e;
        }
    }

    private void addEmailContentToPdf(Document document, Map<String, Object> email, String dateStr) throws DocumentException {
        try {
            var payload = (Map<String, Object>) email.get("payload");
            if (payload == null) {
                logger.error("Email payload is null");
                throw new IllegalArgumentException("Email payload is missing");
            }

            // Create headers with converted date
            var headers = List.of(
                    new EmailHeader("From", getHeaderValue(email, "From")),
                    new EmailHeader("To", getHeaderValue(email, "To")),
                    new EmailHeader("Subject", getHeaderValue(email, "Subject")),
                    new EmailHeader("Date", dateStr)
            );

            // Add headers to document
            Font headerFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);
            Font normalFont = new Font(Font.FontFamily.HELVETICA, 12, Font.NORMAL);

            headers.forEach(header -> {
                try {
                    Paragraph headerPara = new Paragraph();
                    headerPara.add(new Chunk(header.name() + ": ", headerFont));
                    headerPara.add(new Chunk(header.value(), normalFont));
                    document.add(headerPara);
                } catch (DocumentException e) {
                    logger.error("Error while adding email header to PDF", e);
                    throw new RuntimeException(e);
                }
            });

            document.add(new Paragraph("\n"));

            // Add body content
            var body = (Map<String, Object>) payload.get("body");
            if (body != null && body.get("data") != null) {
                var content = new EmailContent(
                        (String) body.get("data"),
                        (String) body.get("mimeType")
                );
                document.add(new Paragraph(content.content(), normalFont));
            }

            // Add attachments if any
            var parts = (List<Map<String, Object>>) payload.get("parts");
            if (parts != null && !parts.isEmpty()) {
                document.add(new Paragraph("\nAttachments:", headerFont));
                parts.stream()
                        .map(part -> (String) part.get("filename"))
                        .filter(filename -> filename != null && !filename.isEmpty())
                        .forEach(filename -> {
                            try {
                                document.add(new Paragraph("- " + filename, normalFont));
                            } catch (DocumentException e) {
                                logger.error("Error while adding attachment information to PDF", e);
                                throw new RuntimeException(e);
                            }
                        });
            }
        } catch (Exception e) {
            logger.error("Error while adding email content to PDF", e);
            throw e;
        }
    }

    private byte[] convertToEml(Map<String, Object> email) throws Exception {
        logger.info("Starting EML conversion for email");

        try {
            var props = new Properties();
            var session = Session.getDefaultInstance(props);
            var mimeMessage = new MimeMessage(session);

            // Set headers
            String from = getHeaderValue(email, "From");
            String subject = getHeaderValue(email, "Subject");
            String to = getHeaderValue(email, "To");

            if (from == null || to == null) {
                logger.error("Required headers are missing: From={}, To={}", from, to);
                throw new IllegalArgumentException("Email headers are incomplete.");
            }

            mimeMessage.setFrom(new InternetAddress(from));
            mimeMessage.setRecipients(Message.RecipientType.TO, to);
            mimeMessage.setSubject(subject != null ? subject : "No Subject");

            // Set date from internalDate
            Object internalDate = email.get("internalDate");
            if (internalDate instanceof Long) {
                mimeMessage.setSentDate(new Date((Long) internalDate));
            }

            // Handle body content
            var payload = (Map<String, Object>) email.get("payload");
            if (payload == null) {
                mimeMessage.setText("This email has no body content.", "utf-8");
                return writeMessageToBytes(mimeMessage);
            }

            MimeMultipart multipart = new MimeMultipart("alternative");

            // Handle multipart content
            if ("multipart/alternative".equals(payload.get("mimeType"))) {
                List<Map<String, Object>> parts = (List<Map<String, Object>>) payload.get("parts");
                if (parts != null) {
                    for (Map<String, Object> part : parts) {
                        Map<String, Object> body = (Map<String, Object>) part.get("body");
                        String mimeType = (String) part.get("mimeType");
                        String content = body != null ? (String) body.get("data") : null;

                        if (content != null && mimeType != null) {
                            MimeBodyPart bodyPart = new MimeBodyPart();
                            bodyPart.setContent(content, mimeType);
                            multipart.addBodyPart(bodyPart);
                        }
                    }
                }
            } else {
                // Handle single part content
                var body = (Map<String, Object>) payload.get("body");
                String content = body != null ? (String) body.get("data") : null;
                String mimeType = (String) payload.get("mimeType");

                if (content == null || content.isEmpty()) {
                    content = "This email has no body content.";
                    mimeType = "text/plain";
                }

                MimeBodyPart bodyPart = new MimeBodyPart();
                bodyPart.setContent(content, mimeType != null ? mimeType : "text/plain");
                multipart.addBodyPart(bodyPart);
            }

            mimeMessage.setContent(multipart);
            mimeMessage.saveChanges();

            return writeMessageToBytes(mimeMessage);
        } catch (Exception e) {
            logger.error("Error during EML conversion", e);
            throw e;
        }
    }

    private byte[] writeMessageToBytes(MimeMessage message) throws IOException, jakarta.mail.MessagingException {
        try (var baos = new ByteArrayOutputStream()) {
            message.writeTo(baos);
            logger.info("EML conversion successful");
            return baos.toByteArray();
        }
    }

    private String getHeaderValue(Map<String, Object> email, String headerName) {
        var payload = (Map<String, Object>) email.get("payload");
        if (payload == null) {
            logger.error("Payload is null when fetching header: {}", headerName);
            return "";
        }

        var headers = (List<Map<String, Object>>) payload.get("headers");
        if (headers == null) {
            logger.error("Headers are null when fetching header: {}", headerName);
            return "";
        }

        return headers.stream()
                .filter(h -> headerName.equals(h.get("name")))
                .map(h -> (String) h.get("value"))
                .findFirst()
                .orElse("");
    }

    private record EmailHeader(String name, String value) {}
    private record EmailContent(String content, String mimeType) {}
}