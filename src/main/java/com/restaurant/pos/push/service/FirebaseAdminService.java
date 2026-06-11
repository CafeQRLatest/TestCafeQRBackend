package com.restaurant.pos.push.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.BatchResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class FirebaseAdminService {

    @Value("${app.firebase.project-id}")
    private String projectId;

    @Value("${app.firebase.client-email}")
    private String clientEmail;

    @Value("${app.firebase.private-key}")
    private String privateKey;

    @PostConstruct
    public void init() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                String formattedPrivateKey = privateKey.replace("\\n", "\n");
                
                // Formulate the JSON format service account credentials using Jackson to ensure correct escaping
                Map<String, Object> serviceAccountMap = new java.util.HashMap<>();
                serviceAccountMap.put("type", "service_account");
                serviceAccountMap.put("project_id", projectId);
                serviceAccountMap.put("client_email", clientEmail);
                serviceAccountMap.put("private_key", formattedPrivateKey);

                String serviceAccountJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(serviceAccountMap);

                InputStream serviceAccountStream = new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8));
                
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccountStream))
                        .setProjectId(projectId)
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("Firebase Admin SDK successfully initialized for project: {}", projectId);
            }
        } catch (Exception e) {
            log.error("Failed to initialize Firebase Admin SDK", e);
        }
    }

    public BatchResponse sendMulticast(String title, String body, Map<String, String> data, List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return null;
        }
        try {
            MulticastMessage message = MulticastMessage.builder()
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putAllData(data)
                    .addAllTokens(tokens)
                    .build();

            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            log.info("Successfully sent multicast message. Success count: {}, Failure count: {}",
                    response.getSuccessCount(), response.getFailureCount());
            return response;
        } catch (Exception e) {
            log.error("Failed to send Firebase multicast message", e);
            return null;
        }
    }
}
