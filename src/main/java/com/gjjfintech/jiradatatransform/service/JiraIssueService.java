package com.gjjfintech.jiradatatransform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gjjfintech.jiradatatransform.client.JiraApiClient;
import com.gjjfintech.jiradatatransform.client.JiraCsvClient;
import com.gjjfintech.jiradatatransform.config.JiraMappingProperties;
import com.gjjfintech.jiradatatransform.util.FileUtils;
import com.gjjfintech.jiradatatransform.util.JsonNodeUtils;
import com.gjjfintech.jiradatatransform.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
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

    private final JiraCsvClient jiraCsvClient = new JiraCsvClient();

    private final ObjectMapper objectMapper;

    // Data folder paths injected from configuration
    @Value("${jira.source.data-folder:}")
    private String sourceDataFolder;

    @Value("${jira.destination.data-folder:}")
    private String destinationDataFolder;

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

    public Collection<Map<String, Object>> getIssuesByFile(boolean useSource, boolean latestFile, String filename) {
        // Determine which data folder to use.
        String folder = useSource ? sourceDataFolder : destinationDataFolder;
        JiraMappingProperties mappingProps = useSource ? sourceMappingProperties : destinationMappingProperties;
        if (folder == null || folder.trim().isEmpty()) {
            throw new IllegalStateException("Data folder is not configured for " + (useSource ? "source" : "destination"));
        }
        // Determine full path to CSV file.
        String filePath = FileUtils.determineCsvFilePath(folder, latestFile, filename);

        return jiraCsvClient.getIssuesByFile(filePath, mappingProps);
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
     * Updates or creates a single Jira issue in the chosen instance.
     *
     * The provided map (issueData) should be a flattened representation of the key fields.
     * - If "issueKey" is present and non‑empty, an update is performed.
     * - Otherwise, a creation is performed, requiring a "projectKey" field.
     *
     * When creating an issue, this method adds the "issuetype" field.
     * If no "issueType" is provided in the input, it defaults to "Epic".
     *
     * This method does not filter out issues based on External Linking ID.
     *
     * @param isSource if true, operate on the source instance; otherwise, operate on the destination instance.
     * @param issueData a flattened map of the key fields.
     */
    public void updateOrCreateIssue(boolean isSource, Map<String, Object> issueData) {
        // Choose the appropriate Jira API client and mapping configuration.
        JiraApiClient client = isSource ? sourceJiraApiClient : destinationJiraApiClient;
        JiraMappingProperties mappingProps = isSource ? sourceMappingProperties : destinationMappingProperties;

        // Build the payload fields from the mapping configuration.
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
            String flatKey = StringUtils.toCamelCase(displayName);
            if (issueData.containsKey(flatKey)) {
                Object value = issueData.get(flatKey);
                // Remove any "fields." prefix from the mapping's attribute name.
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
            // Remove the "key" field from fields if present.
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
            Map<String, Object> projectField = new HashMap<>();
            projectField.put("key", projectKey);
            fieldsPayload.put("project", projectField);

            // Add issuetype logic: if an "issueType" is provided, use it; otherwise, default to "Epic".
            String issueType = (String) issueData.get("issueType");
            if (issueType == null || issueType.trim().isEmpty()) {
                issueType = "Epic";
            }
            Map<String, Object> issueTypeField = new HashMap<>();
            issueTypeField.put("name", issueType);
            fieldsPayload.put("issuetype", issueTypeField);

            payload.put("fields", fieldsPayload);
            payloadNode = objectMapper.valueToTree(payload);
            client.createIssue(payloadNode);
        }
    }

    /**
     * Synchronizes a collection of source issues to the destination Jira instance.
     * Only issues with a non-empty External Linking ID are processed.
     * If the External Linking ID equals "[New]" (ignoring case), then a new issue is created.
     * Otherwise, the value is treated as the destination Jira key and the issue is updated.
     *
     * @param sourceIssues the collection of flattened source issues.
     */
    public void synchronizeIssuesToDestination(Collection<Map<String, Object>> sourceIssues) {
        for (Map<String, Object> sourceIssue : sourceIssues) {
            // Check the External Linking ID field.
            String extLinkKey = StringUtils.toCamelCase("External Linking ID");
            String externalLinkingId = (String) sourceIssue.get(extLinkKey);
            if (externalLinkingId == null || externalLinkingId.trim().isEmpty()) {
                // Skip this issue if no External Linking ID.
                continue;
            }
            // If the External Linking ID is not "[New]", then override the "issueKey"
            // so that the destination issue key is used.
            if (!externalLinkingId.trim().startsWith("[")) {
                sourceIssue.put("issueKey", externalLinkingId.trim());
            } else {
                sourceIssue.remove("issueKey");
                sourceIssue.put("projectKey", externalLinkingId.trim().substring(1, externalLinkingId.trim().length()-1));
            }
            sourceIssue.remove(extLinkKey);

            // Now process the issue.
            updateOrCreateIssue(false, sourceIssue);
        }
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
