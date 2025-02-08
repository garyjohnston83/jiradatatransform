package com.gjjfintech.jiradatatransform.controller;

import com.gjjfintech.jiradatatransform.service.JiraIssueService;
import org.springframework.beans.factory.annotation.Autowired;
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
     * GET /issues?jql=...
     * Returns a collection of flattened Jira issues (including additional linked issues).
     */
    @GetMapping
    public Collection<Map<String, Object>> getIssues(@RequestParam("jql") String jql) {
        return jiraIssueService.getIssuesByJql(jql);
    }

    @GetMapping("/test")
    public String testDisplayName() {
        return jiraIssueService.getMyProfileDisplayName();
    }
}
