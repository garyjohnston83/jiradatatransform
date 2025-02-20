package com.gjjfintech.jiradatatransform.client;

import com.gjjfintech.jiradatatransform.model.CreateConfluencePageRequestBody;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

@Service
public class ConfluenceApiClient {

    private final RestTemplate restTemplate;
    private final String instanceUrl;
    private final String username;
    private final String apiToken;

    public ConfluenceApiClient(RestTemplateBuilder restTemplateBuilder,
                               @Value("${confluence.instanceUrl}") String instanceUrl,
                               @Value("${confluence.username}") String username,
                               @Value("${confluence.apiToken}") String apiToken) {
        this.instanceUrl = instanceUrl;
        this.username = username;
        this.apiToken = apiToken;
        this.restTemplate = restTemplateBuilder.build();
    }

    // Helper method to create headers with Basic Authentication.
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, apiToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }

    /**
     * Creates a new Confluence page using the provided DTO.
     *
     * @param createPageDTO the DTO representing the new page
     * @return the response entity (as a String or optionally a dedicated DTO)
     */
    public ResponseEntity<String> createPage(CreateConfluencePageRequestBody createPageDTO) {
        String url = instanceUrl + "/wiki/rest/api/content";
        HttpEntity<CreateConfluencePageRequestBody> request = new HttpEntity<>(createPageDTO, createHeaders());
        return restTemplate.exchange(url, HttpMethod.POST, request, String.class);
    }
}
