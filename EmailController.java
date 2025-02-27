package com.farmers.ecom.email.controller;

import com.farmers.ecom.email.config.GmailServiceConfig;
import com.farmers.ecom.email.dto.AttachmentDTO;
import com.farmers.ecom.email.dto.EmailRequestDTO;
import com.farmers.ecom.email.service.DomainUserService;
import com.farmers.ecom.email.service.EmailSender;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

@RestController
public class EmailController {

    @Autowired
    private DomainUserService domainUserService;

    @Autowired
    private GmailServiceConfig gmailServiceConfig;

    private final EmailSender emailSender;

    public EmailController(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @GetMapping("/get-all-emails")
    public ResponseEntity<List<String>> showEmailList(Model model) {
        try {
            List<String> emailAddresses = domainUserService.getAllUserEmails();
            model.addAttribute("emails", emailAddresses);
            return ResponseEntity.ok(emailAddresses);
        } catch (Exception e) {
            model.addAttribute("error", "Unable to fetch email addresses: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.singletonList("Failed to send email: " + e.getMessage()));
        }
    }

    @PostMapping("/send-email")
    public ResponseEntity<String> sendEmailWithAttachment(@RequestBody EmailRequestDTO emailRequestDTO) {
        try {
            File file = null;

            // Check if attachmentBase64 is not null or empty
            /*if (emailRequestDTO.getAttachments() != null) {
                // Decode the Base64 encoded attachment
                byte[] decodedBytes = Base64.getDecoder().decode(emailRequestDTO.getAttachmentBase64());

                // Use the original filename passed in the request
                String originalFileName = emailRequestDTO.getAttachmentFileName(); // e.g., "document.pdf"
                if (originalFileName == null || originalFileName.isEmpty()) {
                    return ResponseEntity.badRequest().body("Attachment file name is missing.");
                }
            }*/

            List<AttachmentDTO> attachments = emailRequestDTO.getAttachments();
            List<File> attachmentFiles = convertBase64ToFiles(attachments);

            // Send the email with or without the file
            emailSender.sendEmailWithAttachment(
                    emailRequestDTO.getSubject(),
                    emailRequestDTO.getBodyText(),
                    emailRequestDTO.getFromEmail(),
                    emailRequestDTO.getToEmail(),
                    emailRequestDTO.getCcEmail(),
                    attachmentFiles
            );
            return ResponseEntity.ok("Email sent successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to send email: " + e.getMessage());
        }
    }

    private List<File> convertBase64ToFiles(List<AttachmentDTO> attachments) throws IOException {
        List<File> attachmentFiles = new ArrayList<>();
        for (AttachmentDTO attachment : attachments) {
            byte[] decodedBytes = Base64.getDecoder().decode(attachment.getBase64Data().replace('-', '+').replace('_', '/'));
            String uniqueId = UUID.randomUUID().toString();
            File file = new File(uniqueId + "_" +attachment.getFileName());
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(decodedBytes);
            }
            attachmentFiles.add(file);
        }
        return attachmentFiles;
    }



}



