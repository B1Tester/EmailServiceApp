/*
package com.farmers.ecom.email.model;


import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_history")
public class EmailHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String threadId;
    private String messageId;
    private String fromEmail;
    private String toEmail;
    private String subject;

    @Lob
    private String body;

    private String claimNumber;
    private LocalDateTime receivedAt;

    public EmailHistory() {}

    public EmailHistory(String threadId, String messageId, String fromEmail, String toEmail, String subject, String body, String claimNumber) {
        this.threadId = threadId;
        this.messageId = messageId;
        this.fromEmail = fromEmail;
        this.toEmail = toEmail;
        this.subject = subject;
        this.body = body;
        this.claimNumber = claimNumber;
        this.receivedAt = LocalDateTime.now();
    }

    // Getters and setters...
}

*/
