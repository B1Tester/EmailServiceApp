package com.farmers.ecom.email.service;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;

@Service
public class PubSubService {
    private static final Logger logger = LoggerFactory.getLogger(PubSubService.class);

    @Value("${pubsub.subscription.name}")
    private String subscriptionName;

    @Autowired
    private JwtAuthenticationService jwtAuthService;

    public void pullMessages() {
        try {
            String accessToken = jwtAuthService.getAccessToken();

            String projectId = "claimsitdevpoc";
            String subscriptionPath = ProjectSubscriptionName.format(projectId, subscriptionName);

            Subscriber subscriber = Subscriber.newBuilder(subscriptionPath, new MessageReceiver() {
                @Override
                public void receiveMessage(PubsubMessage message, AckReplyConsumer ackReplyConsumer) {
                    logger.info("Received message ID: {}", message.getMessageId());
                    logger.info("Message data: {}", message.getData().toStringUtf8());
                    ackReplyConsumer.ack();
                }
            }).setCredentialsProvider(FixedCredentialsProvider.create(ServiceAccountCredentials.fromStream(
                    new FileInputStream("path/to/service-account-key.json")))).build();


            subscriber.startAsync().awaitRunning();
            logger.info("Subscriber started for subscription: {}", subscriptionPath);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down subscriber...");
                subscriber.stopAsync();
            }));
        } catch (Exception e) {
            logger.error("Error pulling messages from Pub/Sub", e);
        }
    }
}