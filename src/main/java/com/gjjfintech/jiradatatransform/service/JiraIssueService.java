package com.gjjfintech.jiradatatransform.service;

import com.gjjfintech.jiradatatransform.client.JiraApiClient;
import com.gjjfintech.jiradatatransform.config.JiraMappingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class JiraIssueService {

    private final JiraApiClient jiraApiClient;
    private final JiraMappingProperties mappingProperties;
    private final ObjectMapper objectMapper;

    @Autowired
    public JiraIssueService(JiraApiClient jiraApiClient,
                            JiraMappingProperties mappingProperties,
                            ObjectMapper objectMapper) {
        this.jiraApiClient = jiraApiClient;
        this.mappingProperties = mappingProperties;
        this.objectMapper = objectMapper;
    }

    public String getMyProfileDisplayName() {
        // 1. Fetch initial issues using the provided JQL.
        JsonNode myProfile = jiraApiClient.getMyProfile();
        JsonNode displayNameNode = myProfile.get("displayName");
        return displayNameNode.asText();
    }

    /**
     * Main method: search for issues using JQL, flatten each one based on your YAML mapping,
     * then for each issue, process its Parent Link and Dependant Issues (issue links)
     * to fetch any missing issues.
     */
    public Collection<Map<String, Object>> getIssuesByJql(String jql) {
        Map<String, Map<String, Object>> allIssues = new HashMap<>();

        // 1. Fetch initial issues using the provided JQL.
        JsonNode searchResult = jiraApiClient.searchIssues(jql);
        JsonNode issuesArray = searchResult.get("issues");
        if (issuesArray != null && issuesArray.isArray()) {
            for (JsonNode issueNode : issuesArray) {
                String issueKey = issueNode.get("key").asText();
                Map<String, Object> flatIssue = flattenIssue(issueNode);
                allIssues.put(issueKey, flatIssue);
            }
        }

        // 2. Process each flattened issue for Parent Link and Dependant Issues.
        //    If any linked issue is not already in our collection, fetch it.
        Set<String> keysToProcess = new HashSet<>(allIssues.keySet());
        for (String key : keysToProcess) {
            Map<String, Object> flatIssue = allIssues.get(key);

            // Process Parent Link: the flattened field key will be "parentLink"
            if (flatIssue.containsKey("parentLink")) {
                String parentKey = (String) flatIssue.get("parentLink");
                if (parentKey != null && !parentKey.isEmpty() && !allIssues.containsKey(parentKey)) {
                    JsonNode parentIssueNode = jiraApiClient.getIssue(parentKey);
                    if (parentIssueNode != null) {
                        Map<String, Object> flatParent = flattenIssue(parentIssueNode);
                        allIssues.put(parentKey, flatParent);
                    }
                }
            }

            // Process Dependant Issues: the flattened field key will be "dependantIssues"
            if (flatIssue.containsKey("dependantIssues")) {
                Object depsObj = flatIssue.get("dependantIssues");
                if (depsObj instanceof List) {
                    List<String> depKeys = (List<String>) depsObj;
                    for (String depKey : depKeys) {
                        if (!allIssues.containsKey(depKey)) {
                            JsonNode depIssueNode = jiraApiClient.getIssue(depKey);
                            if (depIssueNode != null) {
                                Map<String, Object> flatDep = flattenIssue(depIssueNode);
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
     * Flattens a single Jira issue using the field mappings provided in your YAML config.
     */
    private Map<String, Object> flattenIssue(JsonNode issue) {
        Map<String, Object> flat = new HashMap<>();

        // Process each field mapping defined in the configuration.
        for (Map.Entry<String, JiraMappingProperties.FieldMapping> entry : mappingProperties.getJiraFieldMappings().entrySet()) {
            String displayName = entry.getKey();  // e.g., "Issue Key", "Summary", etc.
            JiraMappingProperties.FieldMapping mapping = entry.getValue();
            String flatKey = toCamelCase(displayName);

            // If the mapping defines an issueLink block (for Dependant Issues)
            if (mapping.getIssueLink() != null) {
                List<String> linkedIssues = processIssueLinks(issue, mapping.getIssueLink());
                flat.put(flatKey, linkedIssues);
            }
            // Otherwise, if an attribute name is provided, process it normally.
            else if (mapping.getIssueAttributeName() != null) {
                String jsonPointer = convertToJsonPointer(mapping.getIssueAttributeName());
                JsonNode valueNode = issue.at(jsonPointer);
                if (!valueNode.isMissingNode() && !valueNode.isNull()) {
                    String processedValue = processValue(valueNode, mapping.getDataType());
                    flat.put(flatKey, processedValue);
                }
            }
        }
        return flat;
    }

    /**
     * Process a value based on its dataType.
     * - For "String" or "String[IssueKey]", simply return the text.
     * - For "DateAsString[pattern]", verify/format the date.
     */
    private String processValue(JsonNode valueNode, String dataType) {
        if (dataType == null || dataType.startsWith("String")) {
            return valueNode.asText();
        } else if (dataType.startsWith("DateAsString")) {
            // Expected format: DateAsString[yyyy-mm-dd] (or similar)
            String pattern = dataType.substring(dataType.indexOf('[') + 1, dataType.indexOf(']'));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            try {
                LocalDate date = LocalDate.parse(valueNode.asText(), formatter);
                return date.format(formatter);
            } catch (Exception e) {
                // In case of a parsing error, return the original text.
                return valueNode.asText();
            }
        }
        return valueNode.asText();
    }

    /**
     * Processes the issue links from the Jira issue payload based on the provided link configuration.
     * It filters based on the "isInward" flag and allowed link types.
     */
    private List<String> processIssueLinks(JsonNode issue, JiraMappingProperties.IssueLinkMapping linkMapping) {
        List<String> linkedIssueKeys = new ArrayList<>();
        // Issue links are typically under "fields.issuelinks"
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

    /**
     * Converts a dotted path (e.g., "fields.summary") to a JSON pointer (e.g., "/fields/summary").
     */
    private String convertToJsonPointer(String dottedPath) {
        return "/" + dottedPath.replace(".", "/");
    }

    /**
     * Converts a human-readable field name (e.g., "Issue Key") into camelCase (e.g., "issueKey").
     */
    private String toCamelCase(String input) {
        StringBuilder sb = new StringBuilder();
        boolean nextCapital = false;
        for (char c : input.toCharArray()) {
            if (Character.isWhitespace(c) || c == '-' || c == '_') {
                nextCapital = true;
            } else {
                if (sb.length() == 0) {
                    sb.append(Character.toLowerCase(c));
                } else if (nextCapital) {
                    sb.append(Character.toUpperCase(c));
                    nextCapital = false;
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}