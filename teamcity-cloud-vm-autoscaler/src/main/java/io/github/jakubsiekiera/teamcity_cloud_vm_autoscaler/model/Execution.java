package io.github.jakubsiekiera.teamcity_cloud_vm_autoscaler.model;

import java.util.UUID;

/**
 * Represents the mutable state of a remote execution.
 * * @implNote This object is shared across thread boundaries: it is instantiated by the 
 * web thread, mutated by the background execution thread, and read by subsequent web 
 * polling threads.
 */
public class Execution {
    private final String id;
    private ExecutionStatus status;
    private String outputLogs;

    public Execution() {
        // Pre-generate a UUID to decouple the ID assignment from the database/storage layer.
        this.id = UUID.randomUUID().toString();
        this.status = ExecutionStatus.QUEUED;
    }

    public String getId() { return id; }
    public ExecutionStatus getStatus() { return status; }
    public void setStatus(ExecutionStatus status) { this.status = status; }
    public String getOutputLogs() { return outputLogs; }
    public void setOutputLogs(String outputLogs) { this.outputLogs = outputLogs; }
}