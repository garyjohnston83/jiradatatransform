package com.gjjfintech.jiradatatransform.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class JiraApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private String baseUrl;
    private String email;
    private String authToken;
    private String bearerToken;

    public JiraApiClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Creates HTTP headers including the appropriate Authorization header.
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (bearerToken != null && !bearerToken.isEmpty()) {
            headers.set("Authorization", "Bearer " + bearerToken);
        } else if (email != null && !email.isEmpty() && authToken != null && !authToken.isEmpty()) {
            String auth = email + ":" + authToken;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            headers.set("Authorization", "Basic " + encodedAuth);
        }
        return headers;
    }

    /**
     * Searches Jira issues using the provided JQL.
     */
    public JsonNode searchIssues(String jql) {
        try {
            // Optionally encode the JQL if needed.
            // String encodedJql = URLEncoder.encode(jql, StandardCharsets.UTF_8.toString());
            String url = baseUrl + "/rest/api/2/search?jql=" + jql;
            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute searchIssues", e);
        }
    }

    /**
     * Retrieves a single Jira issue by its key.
     */
    public JsonNode getIssue(String issueKey) {
        String url = baseUrl + "/rest/api/2/issue/" + issueKey;
        HttpHeaders headers = createHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse getIssue response for key: " + issueKey, e);
        }
    }

    /**
     * Retrieves the current user's profile.
     */
    public JsonNode getMyProfile() {
        String url = baseUrl + "/rest/api/2/myself";
        HttpHeaders headers = createHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Failed to call myself: ", e);
        }
    }

    /**
     * Creates a new Jira issue using the provided JSON payload.
     *
     * @param issuePayload the JSON payload representing the new issue (e.g., {"fields": { ... }})
     * @return the JSON response from Jira containing details of the created issue.
     */
    public JsonNode createIssue(JsonNode issuePayload) {
        try {
            String url = baseUrl + "/rest/api/2/issue";
            HttpHeaders headers = createHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String body = objectMapper.writeValueAsString(issuePayload);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create issue", e);
        }
    }

    /**
     * Updates an existing Jira issue identified by issueKey using the provided JSON payload.
     *
     * @param issueKey     the key of the issue to update.
     * @param issuePayload the JSON payload representing the updated fields.
     */
    public void updateIssue(String issueKey, JsonNode issuePayload) {
        try {
            String url = baseUrl + "/rest/api/2/issue/" + issueKey;
            HttpHeaders headers = createHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String body = objectMapper.writeValueAsString(issuePayload);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            // Jira typically returns a 204 No Content on successful update.
            restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to update issue with key: " + issueKey, e);
        }
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public void setBearerToken(String bearerToken) {
        this.bearerToken = bearerToken;
    }
}
