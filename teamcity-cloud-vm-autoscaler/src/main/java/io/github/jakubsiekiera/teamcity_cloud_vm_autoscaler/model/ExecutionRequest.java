package io.github.jakubsiekiera.teamcity_cloud_vm_autoscaler.model;

/**
 * DTO carrying the execution parameters.
 */
public record ExecutionRequest(String script, String cpuLimit) {}