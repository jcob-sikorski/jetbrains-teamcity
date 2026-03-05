package io.github.jakubsiekiera.teamcity_cloud_vm_autoscaler.controller;

import io.github.jakubsiekiera.teamcity_cloud_vm_autoscaler.model.Execution;
import io.github.jakubsiekiera.teamcity_cloud_vm_autoscaler.model.ExecutionRequest;
import io.github.jakubsiekiera.teamcity_cloud_vm_autoscaler.service.ExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoint for managing remote script executions.
 *
 * @apiNote This controller is designed asynchronously: submission returns immediately 
 * with a tracking ID, and clients must poll the status endpoint to observe progress.
 */
@RestController
@RequestMapping("/api/executions")
public class ExecutionController {

    private final ExecutionService service;

    public ExecutionController(ExecutionService service) {
        this.service = service;
    }

    /**
     * Accepts a new command execution request.
     * * @implNote We return HTTP 202 (Accepted) rather than 200/201 because the actual 
     * execution happens entirely asynchronously in the background. The response body 
     * contains the UUID needed to track the execution.
     */
    @PostMapping
    public ResponseEntity<String> executeCommand(@RequestBody ExecutionRequest request) {
        String executionId = service.submitCommand(request);
        return ResponseEntity.accepted().body(executionId);
    }

    /**
     * Retrieves the current state of an execution.
     * * @param id The UUID of the execution.
     * @return 200 OK with the execution state, or 404 Not Found if the ID is unknown.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Execution> getExecutionStatus(@PathVariable String id) {
        Execution execution = service.getExecution(id);
        if (execution == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(execution);
    }
}