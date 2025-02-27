package com.farmers.ecom.email.service;

import com.farmers.ecom.email.config.GmailServiceConfig;
import com.google.api.client.util.Base64;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import com.farmers.ecom.email.util.EmailStorageUtil;

@Component
public class EmailSender {

    private final EmailStorageUtil emailStorageUtil;

    public EmailSender(GmailServiceConfig gmailServiceConfig, EmailStorageUtil emailStorageUtil) {
        this.gmailServiceConfig = gmailServiceConfig;
        this.emailStorageUtil = emailStorageUtil;
    }

    private final GmailServiceConfig gmailServiceConfig;

    public void sendEmailWithAttachment(String subject, String bodyText, String fromEmail,
                                        List<String> toEmail, List<String> ccEmail,
                                        List<File> attachments) throws MessagingException, IOException {
        // Get the Gmail service for the sender
        Gmail service = gmailServiceConfig.createGmailService(fromEmail);

        // Create the email content
        MimeMessage email = createEmailWithAttachment(subject, bodyText, fromEmail, toEmail, ccEmail, attachments);

        // Convert the MimeMessage to Gmail API's Message
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        email.writeTo(buffer);
        byte[] rawEmail = buffer.toByteArray();
        String encodedEmail = Base64.encodeBase64URLSafeString(rawEmail);
        Message message = new Message();
        message.setRaw(encodedEmail);

        // Send the email
        Message sentMessage = service.users().messages().send("me", message).execute();
        System.out.println("Email sent successfully using Gmail API.");

        emailStorageUtil.saveSentEmail(sentMessage, fromEmail);
    }

    private MimeMessage createEmailWithAttachment(String subject, String bodyText,
                                                  String fromEmail, List<String> toEmail,
                                                  List<String> ccEmail, List<File> attachments) throws MessagingException, IOException {
        // Set up the email session
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        // Create a new MimeMessage
        MimeMessage email = new MimeMessage(session);
        email.setFrom(new InternetAddress(fromEmail));

        for (String toAddress : toEmail) {
            email.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(toAddress));
        }

        // Add multiple "CC" recipients
        for (String ccAddress : ccEmail) {
            email.addRecipient(jakarta.mail.Message.RecipientType.CC, new InternetAddress(ccAddress));
        }

        email.setSubject(subject);

        MimeMultipart multipart = new MimeMultipart();

        // Add the body text
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setContent(bodyText, "text/html");
        multipart.addBodyPart(textPart);

        if (attachments != null) {
            for (File attachment : attachments) {
                MimeBodyPart attachmentPart = new MimeBodyPart();
                FileDataSource fileDataSource = new FileDataSource(attachment);
                attachmentPart.setDataHandler(new DataHandler(fileDataSource));
                attachmentPart.setFileName(attachment.getName());  // Use the filename of the attachment

                multipart.addBodyPart(attachmentPart);
            }

        }

        // Set the multipart content to the email
        email.setContent(multipart, "text/html");

        return email;
    }
}
