package com.gjjfintech.jiradatatransform.controller;

import com.gjjfintech.jiradatatransform.service.JiraIssueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;

@RestController
@RequestMapping("/sync-job")
public class JiraSyncJobController {

    private final JiraIssueService jiraIssueService;

    @Autowired
    public JiraSyncJobController(JiraIssueService jiraIssueService) {
        this.jiraIssueService = jiraIssueService;
    }

    /**
     * POST /sync
     * Accepts a JSON payload with a "jql" field.
     * Example payload: { "jql": "project=MYPROJECT AND issuetype=Epic" }
     *
     * This endpoint will:
     *   1. Retrieve issues from the source Jira instance using the provided JQL.
     *   2. Synchronize these issues to the destination Jira instance by creating/updating issues.
     *
     * @param syncRequest the request payload containing the JQL.
     * @return a ResponseEntity with a confirmation message.
     */
    @PostMapping
    public ResponseEntity<String> syncIssues(@RequestBody SyncRequest syncRequest) {
        String jql = syncRequest.getJql();
        Collection<Map<String, Object>> sourceIssues = jiraIssueService.getIssuesByJql(jql, true);
        jiraIssueService.synchronizeIssuesToDestination(sourceIssues);
        return ResponseEntity.ok("Sync completed successfully.");
    }

    /**
     * Simple DTO for the sync request payload.
     */
    public static class SyncRequest {
        private String jql;

        public String getJql() {
            return jql;
        }

        public void setJql(String jql) {
            this.jql = jql;
        }
    }
}
