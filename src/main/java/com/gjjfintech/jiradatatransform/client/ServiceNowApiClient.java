package com.gjjfintech.jiradatatransform.client;

import com.gjjfintech.jiradatatransform.model.ServiceNowIncident;
import com.gjjfintech.jiradatatransform.model.ServiceNowIncidentsResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

@Service
public class ServiceNowApiClient {

    private final RestTemplate restTemplate;
    private final String instanceUrl;
    private final String username;
    private final String password;

    public ServiceNowApiClient(RestTemplateBuilder restTemplateBuilder,
                               @Value("${servicenow.instanceUrl}") String instanceUrl,
                               @Value("${servicenow.username}") String username,
                               @Value("${servicenow.password}") String password) {
        this.instanceUrl = instanceUrl;
        this.username = username;
        this.password = password;
        this.restTemplate = restTemplateBuilder.build();
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, password);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /**
     * Retrieves incidents from ServiceNow filtered by the provided sysparm query.
     *
     * @param sysparmQuery a URL-encoded query string (e.g. "active=true")
     * @return a list of IncidentDto objects
     */
    public List<ServiceNowIncident> getIncidents(String sysparmQuery) {
        String encodedQuery = UriUtils.encode(sysparmQuery, StandardCharsets.UTF_8);
        String url = instanceUrl + "/api/now/table/incident?sysparm_query=" + encodedQuery;

        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<ServiceNowIncidentsResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, ServiceNowIncidentsResponse.class);

        if (response.getBody() != null) {
            return response.getBody().getResult();
        } else {
            return Collections.emptyList();
        }
    }
}
