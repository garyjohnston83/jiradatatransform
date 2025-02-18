package com.gjjfintech.jiradatatransform.service;

import com.gjjfintech.jiradatatransform.client.JiraApiClient;
import com.gjjfintech.jiradatatransform.config.JiraMappingProperties;
import com.gjjfintech.jiradatatransform.model.WorkItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class WorkItemService {

    private final JiraIssueService jiraIssueService;
    // Assume we need the destination Jira base URL to build epic URLs.
    // In a real application, consider reading this from configuration.
    private String destinationJiraBaseUrl;


    @Autowired
    public WorkItemService(JiraIssueService jiraIssueService,
                           @Qualifier("destinationJiraApiClient") JiraApiClient destinationJiraApiClient) {
        this.jiraIssueService = jiraIssueService;
        this.destinationJiraBaseUrl = destinationJiraApiClient.getBaseUrl();
    }

    public List<WorkItem> getWorkItemsForQuarter(String quarter) {
        // Build the JQL string. Adjust "cf[12345]" as needed.
        String jql = "project=ELSALUCRO and issuetype = Epic and cf[12345] = " + quarter;

        // Get all issues (epics and their potential parent features) from the destination instance.
        Collection<Map<String, Object>> allIssues = jiraIssueService.getIssuesByJql(jql, false);

        // Build a map for feature issues (where issueType equals "Feature").
        Map<String, Map<String, Object>> parentKeyToFeatureIssue = new HashMap<>();
        List<Map<String, Object>> epicIssues = new ArrayList<>();

        for (Map<String, Object> issue : allIssues) {
            String issueType = (String) issue.get("issueType");
            if (issueType != null) {
                if (issueType.equalsIgnoreCase("Feature")) {
                    String key = (String) issue.get("issueKey");
                    if (key != null) {
                        parentKeyToFeatureIssue.put(key, issue);
                    }
                } else if (issueType.equalsIgnoreCase("Epic")) {
                    epicIssues.add(issue);
                }
            }
        }

        // Build work items from each epic.
        List<WorkItem> workItems = new ArrayList<>();
        for (Map<String, Object> epic : epicIssues) {
            WorkItem workItem = new WorkItem();

            // Epic Name: use epic's "summary"
            String epicName = (String) epic.get("summary");
            workItem.setEpicName(epicName);

            // Epic Link: concatenate destinationJiraBaseUrl + "issue/" + epic's key
            String epicKey = (String) epic.get("issueKey");
            workItem.setEpicLink(destinationJiraBaseUrl + "/browse/" + epicKey);

            // Feature details: use the parent's flattened issue if available.
            String parentKey = (String) epic.get("parentLink");
            if (parentKey != null && !parentKey.trim().isEmpty()) {
                Map<String, Object> parentIssue = parentKeyToFeatureIssue.get(parentKey);
                if (parentIssue != null) {
                    String featureName = (String) parentIssue.get("summary");
                    if (featureName == null || featureName.trim().isEmpty()) {
                        featureName = "[None]";
                    }
                    workItem.setFeatureName(featureName);

                    String featurePriority = (String) parentIssue.get("featurePriority");
                    workItem.setFeaturePriority(featurePriority != null ? featurePriority : "[None]");
                } else {
                    workItem.setFeatureName("[None]");
                    workItem.setFeaturePriority("[None]");
                }
            } else {
                workItem.setFeatureName("[None]");
                workItem.setFeaturePriority("[None]");
            }

            // Planned: from the epic's "plannedUnplanned" field.
            Boolean planned = (Boolean) epic.get("plannedUnplanned");
            workItem.setPlanned(planned != null ? planned : false);

            // DevOps: from the epic's "storyPoints" field.
            Object devOpsObj = epic.get("storyPoints");
            double devOps = 0;
            if (devOpsObj instanceof Number) {
                devOps = ((Number) devOpsObj).doubleValue();
            } else if (devOpsObj instanceof String) {
                try {
                    devOps = Double.parseDouble((String) devOpsObj);
                } catch (NumberFormatException ex) {
                    devOps = 0;
                }
            }
            workItem.setDevOps(devOps);

            // Default values for Engineering, Architecture, Other.
            workItem.setEngineering(0);
            workItem.setArchitecture(0);
            workItem.setOther(0);

            // Status: derived from the epic's fields.
            String status = getWorkItemStatus(epic);
            workItem.setStatus(status);

            workItems.add(workItem);
        }

        return workItems;
    }

    /**
     * Derives a work item status from a flattened Jira issue.
     * For now, simply returns the "status" field or "Not started" if missing.
     */
    public String getWorkItemStatus(Map<String, Object> jiraFields) {
        String status = (String) jiraFields.get("status");
        return (status != null && !status.trim().isEmpty()) ? status : "Not started";
    }

}