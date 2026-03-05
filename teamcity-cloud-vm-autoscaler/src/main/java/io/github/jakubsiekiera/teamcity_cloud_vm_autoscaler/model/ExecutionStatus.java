package io.github.jakubsiekiera.teamcity_cloud_vm_autoscaler.model;

/**
 * Defines the lifecycle states of a remote execution.
 */
public enum ExecutionStatus {
    /** The request has been accepted but the remote executor (Pod) is not yet running. */
    QUEUED, 
    /** The remote executor has started and the script is currently running. */
    IN_PROGRESS, 
    /** The script executed completely and returned an exit code of 0. */
    FINISHED, 
    /** The script failed (non-zero exit code), or the underlying infrastructure failed. */
    FAILED
}