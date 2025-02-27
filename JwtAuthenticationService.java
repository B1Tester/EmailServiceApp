package com.farmers.ecom.email.service;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.auth.oauth2.ServiceAccountCredentials;
import org.springframework.core.io.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import java.nio.charset.StandardCharsets;

@Service
public class JwtAuthenticationService {
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationService.class);
    private static final String OAUTH2_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final int TOKEN_LIFETIME_SECONDS = 3600;

    @Value("${gmail.service-account.credentials}")
    private Resource serviceAccountCredentials;

    @Value("${gmail.service-account.user}")
    private String serviceAccountUser;

    private final RestTemplate restTemplate;

    public JwtAuthenticationService() {
        this.restTemplate = new RestTemplate();
    }

    public String getAccessToken() throws IOException {
        // Ensure serviceAccountUser is a full email address
        if (serviceAccountUser == null || !serviceAccountUser.contains("@")) {
            throw new IllegalArgumentException("Invalid service account user. Must be a full email address.");
        }

        // Load service account credentials
        ServiceAccountCredentials serviceAccount = ServiceAccountCredentials
                .fromStream(serviceAccountCredentials.getInputStream());

        // Create JWT claims
        Map<String, Object> claims = new HashMap<>();
        Instant now = Instant.now();
        claims.put("iss", serviceAccount.getClientEmail());
        claims.put("sub", serviceAccountUser); // Use full email address
        claims.put("scope", String.join(" ",
                "https://mail.google.com/",
                "https://www.googleapis.com/auth/gmail.readonly",
                "https://www.googleapis.com/auth/gmail.modify",
                "https://www.googleapis.com/auth/gmail.settings.basic",
                "https://www.googleapis.com/auth/admin.directory.user.readonly"
        ));
        claims.put("aud", OAUTH2_TOKEN_URL);
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plusSeconds(TOKEN_LIFETIME_SECONDS).getEpochSecond());

        // Create signed JWT
        String signedJwt = createAndSignJwt(serviceAccount, claims);

        // Exchange JWT for access token
        return exchangeJwtForAccessToken(signedJwt);
    }



    private String createAndSignJwt(ServiceAccountCredentials serviceAccount, Map<String, Object> claims) throws IOException {
        try {
            // Create JWT Header
            String header = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    "{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8)
            );

            // Encode claims to Base64
            String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    JSON_FACTORY.toByteArray(claims)
            );

            // Combine Header and Payload
            String unsignedJwt = header + "." + payload;

            // Sign the JWT with the service account's private key
            byte[] signature = serviceAccount.sign(unsignedJwt.getBytes(StandardCharsets.UTF_8));
            String encodedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);

            // Construct the final JWT
            String signedJwt = unsignedJwt + "." + encodedSignature;

            logger.debug("Generated signed JWT: {}", signedJwt);
            return signedJwt;
        } catch (Exception e) {
            logger.error("Error signing JWT", e);
            throw new IOException("Failed to sign JWT", e);
        }
    }

    private String exchangeJwtForAccessToken(String signedJwt) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
        body.add("assertion", signedJwt);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    OAUTH2_TOKEN_URL,
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            if (response.getBody() != null && response.getBody().containsKey("access_token")) {
                logger.debug("Access token obtained successfully");
                return (String) response.getBody().get("access_token");
            } else {
                logger.error("No access token in response: {}", response.getBody());
                throw new IOException("No access token in response");
            }
        } catch (Exception e) {
            logger.error("Error exchanging JWT for access token", e);
            throw new IOException("Failed to obtain access token", e);
        }
    }
}

