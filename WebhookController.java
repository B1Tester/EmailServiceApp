package com.farmers.ecom.email.controller;

import com.farmers.ecom.email.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/webhook")
public class WebhookController {
    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);

    @Autowired
    private EmailService emailService;

    @PostMapping("/pubsub")
    public ResponseEntity<String> pubSubWebhook(@RequestBody Map<String, Object> payload) {
        try {
            logger.info("Received Pub/Sub message: {}", payload);

            String emailAddress = (String) payload.get("emailAddress");
            String historyId = (String) payload.get("historyId");

            if (emailAddress != null && historyId != null) {
                processNotification(emailAddress, historyId);
            }

            return ResponseEntity.ok("Received");
        } catch (Exception e) {
            logger.error("Error processing Pub/Sub webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error");
        }
    }

    private void processNotification(String emailAddress, String historyId) {
        // Fetch updates and broadcast to frontend
        emailService.processUpdates(emailAddress, historyId);
    }
}

