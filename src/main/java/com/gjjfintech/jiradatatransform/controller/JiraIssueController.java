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
    public Collection<Map<String, Object>> getIssues(@RequestParam("jql") String jql,
                                                     @RequestParam("source") Boolean isSource) {
        // Assuming your service now has an overloaded method that accepts a Boolean flag:
        return jiraIssueService.getIssuesByJql(jql, isSource);
    }

    /**
     * GET /issues/test?source=true|false
     *
     * Retrieves the profile display name from the chosen Jira instance.
     */
    @GetMapping("/test")
    public String testDisplayName(@RequestParam("source") Boolean isSource) {
        // Assuming your service now has an overloaded method that accepts a Boolean flag:
        return jiraIssueService.getMyProfileDisplayName(isSource);
    }
}
