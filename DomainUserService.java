package com.farmers.ecom.email.service;

import com.farmers.ecom.email.model.DirectoryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DomainUserService {
    private static final Logger logger = LoggerFactory.getLogger(DomainUserService.class);
    private static final String DIRECTORY_API_URL = "https://admin.googleapis.com/admin/directory/v1/users";

    @Autowired
    private JwtAuthenticationService jwtAuthService;

    private final RestTemplate restTemplate;

    private static final Set<String> EXCLUDED_ACCOUNTS = Set.of(
            "okta-provisioning@claimsitdev.farmers.com"
            // Add any other service accounts that should be excluded
    );

    public DomainUserService() {
        this.restTemplate = new RestTemplate();
    }

    public List<String> getAllUserEmails() {
        try {
            String accessToken = jwtAuthService.getAccessToken();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<?> entity = new HttpEntity<>(headers);

            String url = DIRECTORY_API_URL + "?customer=C04ffb37r";
            logger.debug("Making request to: {}", url);

            ResponseEntity<DirectoryResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    DirectoryResponse.class
            );

            List<String> userEmails = new ArrayList<>();
            if (response.getBody() != null && response.getBody().getUsers() != null) {
                response.getBody().getUsers().forEach(user -> {
                    String email = user.getPrimaryEmail();
                    if (email != null && !EXCLUDED_ACCOUNTS.contains(email)) {
                        logger.debug("Found user email: {}", email);
                        userEmails.add(email);
                    }
                });
            }

            logger.info("Successfully retrieved {} user emails", userEmails.size());
            return userEmails;
        } catch (IOException e) {
            logger.error("Error fetching user emails", e);
            throw new RuntimeException("Failed to fetch user emails", e);
        }
    }




}