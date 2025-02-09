package com.gjjfintech.jiradatatransform.service;

import com.gjjfintech.jiradatatransform.client.JiraApiClient;
import com.gjjfintech.jiradatatransform.config.JiraMappingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    public String getMyProfileDisplayName(Boolean isSource) {
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
    public Collection<Map<String, Object>> getIssuesByJql(String jql, Boolean isSource) {
        Map<String, Map<String, Object>> allIssues = new HashMap<>();

        // Choose the appropriate Jira API client and mapping properties based on the flag.
        JiraApiClient client = isSource ? sourceJiraApiClient : destinationJiraApiClient;
        JiraMappingProperties mappingProps = isSource ? sourceMappingProperties : destinationMappingProperties;

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
        //    If any linked issue is not already in our collection, fetch it.
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
     * For each source issue:
     *  - For epics, if the source issue contains an External Linking ID (flagged in the mapping),
     *    then that value is used as the destination issue key.
     *  - For non-epics, matching is based on the Summary field.
     *  - Only non-special fields are included in the creation/update payload.
     */
    public void synchronizeIssuesToDestination(Collection<Map<String, Object>> sourceIssues) {
        for (Map<String, Object> sourceIssue : sourceIssues) {
            // Determine if this is an epic by checking for the External Linking ID field.
            String linkingIdKey = toCamelCase("External Linking ID"); // e.g., "externalLinkingId"
            boolean isEpic = sourceIssue.containsKey(linkingIdKey) &&
                    sourceIssue.get(linkingIdKey) != null &&
                    !((String) sourceIssue.get(linkingIdKey)).trim().isEmpty();

            String destinationIssueKey = null;
            if (isEpic) {
                // For epics, use the external linking id as the destination issue key.
                destinationIssueKey = (String) sourceIssue.get(linkingIdKey);
            } else {
                // For non-epics, match based on Summary.
                String summaryKey = toCamelCase("Summary"); // e.g., "summary"
                String summary = (String) sourceIssue.get(summaryKey);
                if (summary != null && !summary.isEmpty()) {
                    // Use a simple JQL search for destination issues with the same summary.
                    String jql = "summary ~ \"" + summary + "\"";
                    JsonNode searchResult = destinationJiraApiClient.searchIssues(jql);
                    JsonNode issuesArray = searchResult.get("issues");
                    if (issuesArray != null && issuesArray.isArray() && issuesArray.size() > 0) {
                        destinationIssueKey = issuesArray.get(0).get("key").asText();
                    }
                }
            }

            // Build the payload for creation or update.
            // Use the destination mapping to know which fields to include.
            Map<String, Object> fieldsPayload = new HashMap<>();
            for (Map.Entry<String, JiraMappingProperties.FieldMapping> entry : destinationMappingProperties.getJiraFieldMappings().entrySet()) {
                String displayName = entry.getKey();
                JiraMappingProperties.FieldMapping mapping = entry.getValue();

                // Skip special fields (linking IDs, parent links, or issue links)
                if ((mapping.getIsLinkingId() != null && mapping.getIsLinkingId()) ||
                        (mapping.getIsParentLink() != null && mapping.getIsParentLink()) ||
                        mapping.getIssueLink() != null) {
                    continue;
                }

                // Use the human-readable field name (converted to camelCase) as the key in the flattened source issue.
                String flatKey = toCamelCase(displayName);
                if (sourceIssue.containsKey(flatKey)) {
                    Object value = sourceIssue.get(flatKey);
                    // Place the value in the payload using the destination's attribute name.
                    fieldsPayload.put(mapping.getIssueAttributeName(), value);
                }
            }
            // Construct the final payload in the form: { "fields": { ... } }
            Map<String, Object> payload = new HashMap<>();
            payload.put("fields", fieldsPayload);

            // Convert the payload to a JsonNode.
            JsonNode payloadNode = objectMapper.valueToTree(payload);

            // Call the appropriate destination Jira API method.
            if (destinationIssueKey != null && !destinationIssueKey.isEmpty()) {
                // Update the existing destination issue.
                destinationJiraApiClient.updateIssue(destinationIssueKey, payloadNode);
            } else {
                // Create a new issue in the destination instance.
                destinationJiraApiClient.createIssue(payloadNode);
            }
        }
    }

    /**
     * Flattens a single Jira issue using the provided mapping configuration.
     */
    private Map<String, Object> flattenIssue(JsonNode issue, JiraMappingProperties mappingProps) {
        Map<String, Object> flat = new HashMap<>();

        // Process each field mapping defined in the configuration.
        for (Map.Entry<String, JiraMappingProperties.FieldMapping> entry : mappingProps.getJiraFieldMappings().entrySet()) {
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
                    // Check if the field should be processed as a String array.
                    if (mapping.getDataType() != null && mapping.getDataType().startsWith("String[]")) {
                        List<String> processedValues = processStringArrayValue(valueNode);
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
     * Process a value based on its dataType.
     * - For "String" or "String[IssueKey]", simply return the text.
     * - For "DateAsString[pattern]", verify/format the date.
     */
    private String processValue(JsonNode valueNode, String dataType) {
        if (dataType == null || (dataType.startsWith("String") && !dataType.startsWith("String["))) {
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
     * Processes a JsonNode expected to represent an array of strings.
     */
    private List<String> processStringArrayValue(JsonNode valueNode) {
        List<String> result = new ArrayList<>();
        if (valueNode.isArray()) {
            for (JsonNode element : valueNode) {
                result.add(element.asText());
            }
        } else {
            result.add(valueNode.asText());
        }
        return result;
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
