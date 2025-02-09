package com.gjjfintech.jiradatatransform.config;

import com.gjjfintech.jiradatatransform.client.JiraApiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JiraApiClientConfig {

    @Bean("sourceJiraApiClient")
    public JiraApiClient sourceJiraApiClient(
            @Value("${jira.source.base-url}") String baseUrl,
            @Value("${jira.source.email}") String email,
            @Value("${jira.source.authToken}") String authToken,
            @Value("${jira.source.bearerToken:}") String bearerToken) {
        JiraApiClient client = new JiraApiClient();
        client.setBaseUrl(baseUrl);
        client.setEmail(email);
        client.setAuthToken(authToken);
        client.setBearerToken(bearerToken);
        return client;
    }

    @Bean("destinationJiraApiClient")
    public JiraApiClient destinationJiraApiClient(
            @Value("${jira.destination.base-url}") String baseUrl,
            @Value("${jira.destination.email}") String email,
            @Value("${jira.destination.authToken}") String authToken,
            @Value("${jira.destination.bearerToken:}") String bearerToken) {
        JiraApiClient client = new JiraApiClient();
        client.setBaseUrl(baseUrl);
        client.setEmail(email);
        client.setAuthToken(authToken);
        client.setBearerToken(bearerToken);
        return client;
    }
}