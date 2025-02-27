/*
package com.farmers.ecom.email.config;

import com.farmers.ecom.email.controller.EmailExportController;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Configuration
public class GmailServiceConfig1 {

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final Logger logger = LoggerFactory.getLogger(EmailExportController.class);

    @Value("${gmail.service-account.credentials}")
    private Resource serviceAccountCredentials;

    @Value("${gmail.service-account.user}")
    private String serviceAccountUser;

    @Bean
    public Gmail gmailService() throws GeneralSecurityException, IOException {
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        // Load service account credentials
        GoogleCredentials credentials = ServiceAccountCredentials.fromStream(serviceAccountCredentials.getInputStream())
                .createScoped(Collections.singleton("https://mail.google.com/"))
                .createDelegated(serviceAccountUser);

        // Create Gmail service
        return new Gmail.Builder(httpTransport, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                .setApplicationName("Email Service")
                .build();
    }

    public Gmail createGmailService(String userEmail) throws IOException {

        try {

            // Validate userEmail is not null or "me"

            if (userEmail == null || userEmail.equals("me")) {

                userEmail = serviceAccountUser; // Use default service account user

            }

            // Create credentials with impersonation

            GoogleCredentials impersonatedCredentials = serviceAccountCredentials.createScoped(

                    Collections.singletonList("https://mail.google.com/")

            ).createDelegated(userEmail);

            // Build and return Gmail service

            String finalUserEmail = userEmail;

            return new Gmail.Builder(httpTransport, JSON_FACTORY, request -> {

                try {

                    impersonatedCredentials.refreshIfExpired();

                    String accessToken = impersonatedCredentials.getAccessToken().getTokenValue();

                    request.getHeaders().setAuthorization("Bearer " + accessToken);

                } catch (IOException e) {

                    logger.error("Failed to refresh access token for user: {}", finalUserEmail, e);

                    throw new RuntimeException("Token refresh failed for user: " + finalUserEmail, e);

                }

            })

                    .setApplicationName("Email Service")

                    .build();

        } catch (Exception e) {

            logger.error("Error creating Gmail service for user: {}", userEmail, e);

            throw new IOException("Failed to create Gmail service for user " + userEmail + ": " + e.getMessage());

        }

    }

}*/
