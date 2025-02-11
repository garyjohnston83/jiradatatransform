package com.gjjfintech.jiradatatransform.controller;

import com.gjjfintech.jiradatatransform.service.JiraIssueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;

@RestController
@RequestMapping("/issues")
public class JiraIssueController {

    private final JiraIssueService jiraIssueService;

    @Autowired
    public JiraIssueController(JiraIssueService jiraIssueService) {
        this.jiraIssueService = jiraIssueService;
    }

    /**
     * GET /issues?jql=...&source=true|false
     *
     * The "source" parameter determines which Jira instance to query.
     * - If source=true, issues are retrieved from the source Jira instance.
     * - If source=false, issues are retrieved from the destination Jira instance.
     */
    @GetMapping
    public Collection<Map<String, Object>> getIssues(@RequestParam(name="jql", required=true) String jql,
                                                     @RequestParam(name="source", required=false) Boolean isSource) {
        boolean useSourceJiraInstance = isSource != null && isSource;
        return jiraIssueService.getIssuesByJql(jql, useSourceJiraInstance);
    }

    /**
     * GET /issues/test?source=true|false
     *
     * Retrieves the profile display name from the chosen Jira instance.
     */
    @GetMapping("/test")
    public String testDisplayName(@RequestParam(name="source", required=false) Boolean isSource) {
        boolean useSourceJiraInstance = isSource != null && isSource;
        return jiraIssueService.getMyProfileDisplayName(useSourceJiraInstance);
    }

    /**
     * POST /issue?source=true|false
     *
     * This endpoint updates or creates a single Jira issue in the chosen instance.
     * - If the JSON payload includes a non-empty "issueKey", an update is performed.
     * - Otherwise, a new issue is created (and the payload must include a "projectKey").
     *
     * Sample usage with Postman:
     *   POST http://localhost:8080/issue?source=false
     *   Content-Type: application/json
     *   { ... }
     *
     * @param isSource  boolean flag indicating which instance to operate on.
     * @param issueData flattened map of key fields.
     * @return A ResponseEntity indicating success.
     */
    @PostMapping
    public ResponseEntity<String> updateOrCreateIssue(@RequestBody Map<String, Object> issueData,
                                                      @RequestParam(name="source", required=false) Boolean isSource) {
        boolean useSourceJiraInstance = isSource != null && isSource;
        jiraIssueService.updateOrCreateIssue(useSourceJiraInstance, issueData);
        return ResponseEntity.ok("Issue update/create operation completed successfully.");
    }
}
