package com.gjjfintech.jiradatatransform.controller;

import com.gjjfintech.jiradatatransform.model.WorkItem;
import com.gjjfintech.jiradatatransform.service.WorkItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/work-items")
public class WorkItemController {

    private final WorkItemService workItemService;

    @Autowired
    public WorkItemController(WorkItemService workItemService) {
        this.workItemService = workItemService;
    }

    /**
     * GET /work-items?quarter=Q1YYYY
     *
     * Returns a list of work items for the specified quarter.
     *
     * @param quarter Quarter in format Q1YYYY (e.g., Q12025)
     * @return A list of work items or a 400 Bad Request if the quarter is invalid.
     */
    @GetMapping
    public ResponseEntity<List<WorkItem>> getWorkItems(@RequestParam("quarter") String quarter) {
        // Validate quarter format: should match Q[1-4][0-9]{4}
        if (!quarter.matches("^Q[1-4]\\d{4}$")) {
            return ResponseEntity.badRequest().build();
        }
        List<WorkItem> workItems = workItemService.getWorkItemsForQuarter(quarter);
        return ResponseEntity.ok(workItems);
    }
}
