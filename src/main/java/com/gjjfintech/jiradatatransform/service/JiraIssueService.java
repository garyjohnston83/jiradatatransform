package com.gjjfintech.jiradatatransform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gjjfintech.jiradatatransform.client.JiraApiClient;
import com.gjjfintech.jiradatatransform.config.JiraMappingProperties;
import com.gjjfintech.jiradatatransform.util.JsonNodeUtils;
import com.gjjfintech.jiradatatransform.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class JiraIssueService {

    // Client and mapping for source instance
    private final JiraApiClient sourceJiraApiClient;
    private final JiraMappingProperties sourceMappingProperties;

    // Client and mapping for destination instance
    private final JiraApiClient destinationJiraApiClient;
    private final JiraMappingProperties destinationMappingProperties;

    private final ObjectMapper objectMapper;

    @Autowired
    public JiraIssueService(
            @Qualifier("sourceJiraApiClient") JiraApiClient sourceJiraApiClient,
            @Qualifier("sourceMappingProperties") JiraMappingProperties sourceMappingProperties,
            @Qualifier("destinationJiraApiClient") JiraApiClient destinationJiraApiClient,
            @Qualifier("destinationMappingProperties") JiraMappingProperties destinationMappingProperties,
            ObjectMapper objectMapper) {
        this.sourceJiraApiClient = sourceJiraApiClient;
        this.sourceMappingProperties = sourceMappingProperties;
        this.destinationJiraApiClient = destinationJiraApiClient;
        this.destinationMappingProperties = destinationMappingProperties;
        this.objectMapper = objectMapper;
    }

    public String getMyProfileDisplayName(boolean isSource) {
        // Choose the appropriate Jira API client based on the flag.
        JiraApiClient client = isSource ? sourceJiraApiClient : destinationJiraApiClient;
        // Fetch the profile and return the display name.
        JsonNode myProfile = client.getMyProfile();
        JsonNode displayNameNode = myProfile.get("displayName");
        return displayNameNode.asText();
    }

    /**
     * Searches for issues using the provided JQL in the chosen Jira instance and returns
     * a collection of flattened issues.
     */
    public Collection<Map<String, Object>> getIssuesByJql(String jql, boolean useSource) {
        Map<String, Map<String, Object>> allIssues = new HashMap<>();

        // Choose the appropriate Jira API client and mapping properties based on the flag.
        JiraApiClient client = useSource ? sourceJiraApiClient : destinationJiraApiClient;
        JiraMappingProperties mappingProps = useSource ? sourceMappingProperties : destinationMappingProperties;

        // 1. Fetch initial issues using the provided JQL.
        JsonNode searchResult = client.searchIssues(jql);
        JsonNode issuesArray = searchResult.get("issues");
        if (issuesArray != null && issuesArray.isArray()) {
            for (JsonNode issueNode : issuesArray) {
                String issueKey = issueNode.get("key").asText();
                Map<String, Object> flatIssue = flattenIssue(issueNode, mappingProps);
                allIssues.put(issueKey, flatIssue);
            }
        }

        // 2. Process each flattened issue for Parent Link and Dependant Issues.
        Set<String> keysToProcess = new HashSet<>(allIssues.keySet());
        for (String key : keysToProcess) {
            Map<String, Object> flatIssue = allIssues.get(key);

            // Process Parent Link (if present)
            if (flatIssue.containsKey("parentLink")) {
                String parentKey = (String) flatIssue.get("parentLink");
                if (parentKey != null && !parentKey.isEmpty() && !allIssues.containsKey(parentKey)) {
                    JsonNode parentIssueNode = client.getIssue(parentKey);
                    if (parentIssueNode != null) {
                        Map<String, Object> flatParent = flattenIssue(parentIssueNode, mappingProps);
                        allIssues.put(parentKey, flatParent);
                    }
                }
            }

            // Process Dependant Issues (if present)
            if (flatIssue.containsKey("dependantIssues")) {
                Object depsObj = flatIssue.get("dependantIssues");
                if (depsObj instanceof List) {
                    List<String> depKeys = (List<String>) depsObj;
                    for (String depKey : depKeys) {
                        if (!allIssues.containsKey(depKey)) {
                            JsonNode depIssueNode = client.getIssue(depKey);
                            if (depIssueNode != null) {
                                Map<String, Object> flatDep = flattenIssue(depIssueNode, mappingProps);
                                allIssues.put(depKey, flatDep);
                            }
                        }
                    }
                }
            }
        }
        return allIssues.values();
    }

    /**
     * Synchronizes a collection of source issues to the destination Jira instance.
     */
    public void synchronizeIssuesToDestination(Collection<Map<String, Object>> sourceIssues) {
        for (Map<String, Object> sourceIssue : sourceIssues) {
            String linkingIdKey = StringUtils.toCamelCase("External Linking ID"); // e.g., "externalLinkingId"
            boolean isEpic = sourceIssue.containsKey(linkingIdKey) &&
                    sourceIssue.get(linkingIdKey) != null &&
                    !((String) sourceIssue.get(linkingIdKey)).trim().isEmpty();

            String destinationIssueKey = null;
            if (isEpic) {
                destinationIssueKey = (String) sourceIssue.get(linkingIdKey);
            } else {
                String summaryKey = StringUtils.toCamelCase("Summary"); // e.g., "summary"
                String summary = (String) sourceIssue.get(summaryKey);
                if (summary != null && !summary.isEmpty()) {
                    String jql = "summary ~ \"" + summary + "\"";
                    JsonNode searchResult = destinationJiraApiClient.searchIssues(jql);
                    JsonNode issuesArray = searchResult.get("issues");
                    if (issuesArray != null && issuesArray.isArray() && issuesArray.size() > 0) {
                        destinationIssueKey = issuesArray.get(0).get("key").asText();
                    }
                }
            }

            Map<String, Object> fieldsPayload = new HashMap<>();
            for (Map.Entry<String, JiraMappingProperties.FieldMapping> entry : destinationMappingProperties.getJiraFieldMappings().entrySet()) {
                String displayName = entry.getKey();
                JiraMappingProperties.FieldMapping mapping = entry.getValue();

                if ((mapping.getIsLinkingId() != null && mapping.getIsLinkingId()) ||
                        (mapping.getIsParentLink() != null && mapping.getIsParentLink()) ||
                        mapping.getIssueLink() != null) {
                    continue;
                }
                String flatKey = StringUtils.toCamelCase(displayName);
                if (sourceIssue.containsKey(flatKey)) {
                    Object value = sourceIssue.get(flatKey);
                    fieldsPayload.put(mapping.getIssueAttributeName(), value);
                }
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("fields", fieldsPayload);
            JsonNode payloadNode = objectMapper.valueToTree(payload);

            if (destinationIssueKey != null && !destinationIssueKey.isEmpty()) {
                destinationJiraApiClient.updateIssue(destinationIssueKey, payloadNode);
            } else {
                String projectKey = (String) sourceIssue.get(StringUtils.toCamelCase("Project Key"));
                if (projectKey == null || projectKey.trim().isEmpty()) {
                    throw new IllegalArgumentException("Project key is required for creating a new issue.");
                }
                Map<String, Object> projectField = new HashMap<>();
                projectField.put("key", projectKey);
                fieldsPayload.put("project", projectField);
                payload.put("fields", fieldsPayload);
                payloadNode = objectMapper.valueToTree(payload);
                destinationJiraApiClient.createIssue(payloadNode);
            }
        }
    }

    /**
     * Updates or creates a single Jira issue in the chosen instance.
     *
     * The provided map (issueData) should be a flattened representation of the key fields.
     * - If the map contains a non-empty "issueKey" field, it will be treated as an update.
     * - Otherwise, it will be treated as a creation, requiring a "projectKey" field.
     *
     * When creating an issue, this method adds the "issuetype" field.
     * If no "issueType" is provided in the input, it defaults to "Epic".
     *
     * @param isSource if true, operate on the source instance; otherwise, operate on the destination instance.
     * @param issueData a flattened map of the key fields.
     */
    public void updateOrCreateIssue(boolean isSource, Map<String, Object> issueData) {
        // Choose the appropriate Jira API client and mapping configuration.
        JiraApiClient client = isSource ? sourceJiraApiClient : destinationJiraApiClient;
        JiraMappingProperties mappingProps = isSource ? sourceMappingProperties : destinationMappingProperties;

        // Build the payload using the mapping configuration.
        Map<String, Object> fieldsPayload = new HashMap<>();
        for (Map.Entry<String, JiraMappingProperties.FieldMapping> entry : mappingProps.getJiraFieldMappings().entrySet()) {
            String displayName = entry.getKey();
            JiraMappingProperties.FieldMapping mapping = entry.getValue();
            // Skip special fields: linking IDs, parent links, or issue links.
            if ((mapping.getIsLinkingId() != null && mapping.getIsLinkingId()) ||
                    (mapping.getIsParentLink() != null && mapping.getIsParentLink()) ||
                    mapping.getIssueLink() != null) {
                continue;
            }
            // Use the human‑readable field name converted to camelCase as the key.
            String flatKey = StringUtils.toCamelCase(displayName);
            if (issueData.containsKey(flatKey)) {
                Object value = issueData.get(flatKey);
                // Remove a "fields." prefix if present, so that we only send, for example, "summary" rather than "fields.summary".
                String attrName = mapping.getIssueAttributeName();
                if (attrName.startsWith("fields.")) {
                    attrName = attrName.substring("fields.".length());
                }
                fieldsPayload.put(attrName, value);
            }
        }

        // Construct the final payload in the form: { "fields": { ... } }
        Map<String, Object> payload = new HashMap<>();
        payload.put("fields", fieldsPayload);
        JsonNode payloadNode = objectMapper.valueToTree(payload);

        // Check if an "issueKey" is provided in the flattened data.
        String issueKey = (String) issueData.get("issueKey");
        if (issueKey != null && !issueKey.trim().isEmpty()) {
            // Update scenario.
            // Remove the "key" field from the payload (if present) so that we don't send it in the "fields" object.
            fieldsPayload.remove("key");
            payload.put("fields", fieldsPayload);
            payloadNode = objectMapper.valueToTree(payload);
            client.updateIssue(issueKey, payloadNode);
        } else {
            // Creation scenario: require a "projectKey" in the flattened data.
            String projectKey = (String) issueData.get("projectKey");
            if (projectKey == null || projectKey.trim().isEmpty()) {
                throw new IllegalArgumentException("Project key is required for creating a new issue.");
            }
            // For Jira create payloads, the project field is typically an object: { "project": { "key": "XXX" } }.
            Map<String, Object> projectField = new HashMap<>();
            projectField.put("key", projectKey);
            fieldsPayload.put("project", projectField);

            // Add issuetype logic: if an "issueType" is provided, use it; otherwise, default to "Epic".
            String issueType = (String) issueData.get("issueType");
            if (issueType == null || issueType.trim().isEmpty()) {
                issueType = "Epic";
            }
            // Jira expects the issue type as an object with a "name" property.
            Map<String, Object> issueTypeField = new HashMap<>();
            issueTypeField.put("name", issueType);
            fieldsPayload.put("issuetype", issueTypeField);

            payload.put("fields", fieldsPayload);
            payloadNode = objectMapper.valueToTree(payload);
            client.createIssue(payloadNode);
        }
    }


    /**
     * Flattens a single Jira issue using the provided mapping configuration.
     */
    private Map<String, Object> flattenIssue(JsonNode issue, JiraMappingProperties mappingProps) {
        Map<String, Object> flat = new HashMap<>();
        for (Map.Entry<String, JiraMappingProperties.FieldMapping> entry : mappingProps.getJiraFieldMappings().entrySet()) {
            String displayName = entry.getKey();
            JiraMappingProperties.FieldMapping mapping = entry.getValue();
            String flatKey = StringUtils.toCamelCase(displayName);
            if (mapping.getIssueLink() != null) {
                List<String> linkedIssues = processIssueLinks(issue, mapping.getIssueLink());
                flat.put(flatKey, linkedIssues);
            } else if (mapping.getIssueAttributeName() != null) {
                String jsonPointer = StringUtils.convertToJsonPointer(mapping.getIssueAttributeName());
                JsonNode valueNode = issue.at(jsonPointer);
                if (!valueNode.isMissingNode() && !valueNode.isNull()) {
                    if (mapping.getDataType() != null && mapping.getDataType().startsWith("String[]")) {
                        List<String> processedValues = JsonNodeUtils.processStringArrayValue(valueNode);
                        flat.put(flatKey, processedValues);
                    } else {
                        String processedValue = processValue(valueNode, mapping.getDataType());
                        flat.put(flatKey, processedValue);
                    }
                }
            }
        }
        return flat;
    }

    /**
     * Processes a value based on its dataType.
     * - For "String" or "String[IssueKey]", simply return the text.
     * - For "DateAsString[pattern]", verify/format the date.
     */
    private String processValue(JsonNode valueNode, String dataType) {
        if (dataType == null || (dataType.startsWith("String") && !dataType.startsWith("String["))) {
            return valueNode.asText();
        } else if (dataType.startsWith("DateAsString")) {
            String pattern = dataType.substring(dataType.indexOf('[') + 1, dataType.indexOf(']'));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            try {
                LocalDate date = LocalDate.parse(valueNode.asText(), formatter);
                return date.format(formatter);
            } catch (Exception e) {
                return valueNode.asText();
            }
        }
        return valueNode.asText();
    }

    /**
     * Processes the issue links from the Jira issue payload based on the provided link configuration.
     */
    private List<String> processIssueLinks(JsonNode issue, JiraMappingProperties.IssueLinkMapping linkMapping) {
        List<String> linkedIssueKeys = new ArrayList<>();
        JsonNode linksArray = issue.at("/fields/issuelinks");
        if (linksArray != null && linksArray.isArray()) {
            for (JsonNode linkNode : linksArray) {
                if (linkMapping.isInward()) {
                    JsonNode inwardIssue = linkNode.get("inwardIssue");
                    JsonNode typeNode = linkNode.get("type");
                    if (inwardIssue != null && typeNode != null) {
                        String inwardType = typeNode.get("inward").asText();
                        if (linkMapping.getLinkTypes().contains(inwardType)) {
                            String issueKey = inwardIssue.get("key").asText();
                            linkedIssueKeys.add(issueKey);
                        }
                    }
                } else {
                    JsonNode outwardIssue = linkNode.get("outwardIssue");
                    JsonNode typeNode = linkNode.get("type");
                    if (outwardIssue != null && typeNode != null) {
                        String outwardType = typeNode.get("outward").asText();
                        if (linkMapping.getLinkTypes().contains(outwardType)) {
                            String issueKey = outwardIssue.get("key").asText();
                            linkedIssueKeys.add(issueKey);
                        }
                    }
                }
            }
        }
        return linkedIssueKeys;
    }
}
