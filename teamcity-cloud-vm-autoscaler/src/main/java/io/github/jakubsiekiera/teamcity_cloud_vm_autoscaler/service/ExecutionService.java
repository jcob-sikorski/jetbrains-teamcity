package io.github.jakubsiekiera.teamcity_cloud_vm_autoscaler.service;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.github.jakubsiekiera.teamcity_cloud_vm_autoscaler.model.Execution;
import io.github.jakubsiekiera.teamcity_cloud_vm_autoscaler.model.ExecutionRequest;
import io.github.jakubsiekiera.teamcity_cloud_vm_autoscaler.model.ExecutionStatus;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates the lifecycle of remote executors via Kubernetes.
 */
@Service
public class ExecutionService {

    // ConcurrentHashMap is strictly required here to prevent ConcurrentModificationExceptions 
    // and ensure memory visibility across the Spring Web threads and the common ForkJoinPool.
    private final Map<String, Execution> database = new ConcurrentHashMap<>();
    
    // The Fabric8 client auto-configures based on ~/.kube/config or in-cluster ServiceAccounts.
    private final KubernetesClient k8sClient = new KubernetesClientBuilder().build();

    /**
     * Registers a new execution and offloads the infrastructure orchestration to a background thread.
     */
    public String submitCommand(ExecutionRequest request) {
        Execution execution = new Execution();
        database.put(execution.getId(), execution);
        
        // Fire-and-forget: Offload K8s interactions to prevent blocking the HTTP worker thread.
        CompletableFuture.runAsync(() -> runOnRemoteExecutor(execution, request));
        
        return execution.getId();
    }

    public Execution getExecution(String id) {
        return database.get(id);
    }

    /**
     * Core orchestration loop. Maps the execution logic to the Kubernetes Pod lifecycle.
     */
    private void runOnRemoteExecutor(Execution execution, ExecutionRequest request) {
        String podName = "executor-" + execution.getId();

        try {
            // 1. Pod Definition
            // We use 'RestartPolicy.Never' to treat the Pod as a one-off batch job rather than a daemon.
            Pod pod = new PodBuilder()
                .withNewMetadata()
                    .withName(podName)
                .endMetadata()
                .withNewSpec()
                    .withRestartPolicy("Never")
                    .addNewContainer()
                        .withName("runner")
                        .withImage("alpine:latest") // Lightweight, minimal attack surface
                        .withCommand("sh", "-c", request.script())
                        .withNewResources()
                            // Enforcing CPU limits ensures one heavy job doesn't starve the cluster.
                            .addToRequests("cpu", new Quantity(request.cpuLimit()))
                            .addToLimits("cpu", new Quantity(request.cpuLimit()))
                        .endResources()
                    .endContainer()
                .endSpec()
                .build();

            // 2. Submit to Cluster
            k8sClient.pods().resource(pod).create();

            // 3. Wait for Initialization
            // Wait up to 2 minutes for cluster auto-scalers to provision nodes if necessary.
            k8sClient.pods().withName(podName).waitUntilCondition(
                p -> "Running".equals(p.getStatus().getPhase()) || "Succeeded".equals(p.getStatus().getPhase()),
                2, TimeUnit.MINUTES
            );
            
            // At this point, the infrastructure is ready and our container has started.
            execution.setStatus(ExecutionStatus.IN_PROGRESS);

            // 4. Wait for Execution Completion
            // Wait up to 10 minutes for the user's script to complete.
            k8sClient.pods().withName(podName).waitUntilCondition(
                p -> "Succeeded".equals(p.getStatus().getPhase()) || "Failed".equals(p.getStatus().getPhase()),
                10, TimeUnit.MINUTES
            );

            // 5. Capture Output and Final State
            // Retrieve stdout/stderr before we delete the pod.
            String logs = k8sClient.pods().withName(podName).getLog();
            execution.setOutputLogs(logs);
            
            Pod finalPodState = k8sClient.pods().withName(podName).get();
            if ("Succeeded".equals(finalPodState.getStatus().getPhase())) {
                execution.setStatus(ExecutionStatus.FINISHED);
            } else {
                execution.setStatus(ExecutionStatus.FAILED);
            }

        } catch (Exception e) {
            // Catching generic Exception ensures we don't leave the execution in a perpetual IN_PROGRESS state.
            execution.setStatus(ExecutionStatus.FAILED);
            execution.setOutputLogs("Infrastructure/System Error: " + e.getMessage());
        } finally {
            // 6. Cleanup
            // Crucial: Regardless of success or failure, we must garbage collect the executor pod 
            // to prevent cluster resource exhaustion.
            k8sClient.pods().withName(podName).delete();
        }
    }
}