# TeamCity Cloud VM Autoscaler - Remote Executor Service

This project is a prototype service for executing shell commands on remote infrastructure. It is submitted as part of the JetBrains Internship application for the **TeamCity Cloud VM Autoscaler** project.

The service provides a REST API that allows users to submit shell scripts, define resource limits (CPU), and monitor the execution lifecycle asynchronously.



## Architecture & Design Decisions

To fulfill the "remote executor" requirement, this service leverages **Kubernetes**. 
- **Framework:** Java 25 + Spring Boot.
- **Infrastructure Orchestration:** Fabric8 Kubernetes Client.
- **Executor:** A lightweight `alpine:latest` Docker container dynamically provisioned as a Kubernetes Pod.

**Why Kubernetes?** Using Kubernetes (via Minikube for local testing) natively solves the requirements for resource allocation (CPU limits), isolated execution environments, and state management (Pod lifecycle perfectly maps to Queued -> In Progress -> Finished).

---

## ⚙️ Prerequisites

To run this project locally, you will need the following installed on your machine:

1. **Java 17+** 2. **Docker** (or compatible container runtime)
3. **Minikube** (To run a local single-node Kubernetes cluster)
4. **kubectl** (Optional, but recommended for observing the cluster)

---

## Setup & Execution Instructions

### 1. Start the Local Kubernetes Cluster
The Spring Boot application requires access to a Kubernetes cluster. Start Minikube, which will automatically configure your local `~/.kube/config` file (which the Fabric8 client uses for authentication).

```bash
minikube start

```

### 2. Run the Spring Boot Application

From the root directory of the project, start the application using the Maven wrapper.

**Mac / Linux:**

```bash
./mvnw spring-boot:run

```

**Windows:**

```cmd
mvnw.cmd spring-boot:run

```

The API will be available at `http://localhost:8080`.

---

## 📖 API Documentation & Usage

### 1. Submit a Command

Submits a script to be executed on a remote executor and returns an tracking ID.

**Endpoint:** `POST /api/executions`
**Content-Type:** `application/json`

**Request Body:**

```json
{
  "script": "echo 'Starting...' && sleep 10 && echo 'Finished script!'",
  "cpuLimit": "100m"
}

```

*Note: `cpuLimit` follows Kubernetes resource constraints (e.g., `100m` for 100 millicores, `1` for 1 full CPU).*

**Example Request:**

```bash
curl -X POST http://localhost:8080/api/executions \
     -H "Content-Type: application/json" \
     -d '{"script": "echo Hello JetBrains && sleep 5", "cpuLimit": "100m"}'

```

**Response (202 Accepted):** Returns a UUID string (e.g., `95eda902-ec5d-4f7f-a193-1a1c9963095a`).

---

### 2. Get Execution Status

Retrieves the current status and output logs of a submitted execution.

**Endpoint:** `GET /api/executions/{id}`

**Lifecycle States:**

* `QUEUED`: Pod is being scheduled and pulling the Docker image.
* `IN_PROGRESS`: Container is running the shell script.
* `FINISHED`: Script executed successfully (Exit Code 0).
* `FAILED`: Script failed, or infrastructure error occurred.

**Example Request:**

```bash
curl http://localhost:8080/api/executions/95eda902-ec5d-4f7f-a193-1a1c9963095a

```

**Example Response (While Running):**

```json
{
  "id": "95eda902-ec5d-4f7f-a193-1a1c9963095a",
  "status": "IN_PROGRESS",
  "outputLogs": null
}

```

**Example Response (When Completed):**

```json
{
  "id": "95eda902-ec5d-4f7f-a193-1a1c9963095a",
  "status": "FINISHED",
  "outputLogs": "Hello JetBrains\n"
}

```

---

## Cleanup

The service is designed to automatically delete the remote executor pods upon completion (whether successful or failed) to prevent resource leakage. You can verify this by running `kubectl get pods`.

To tear down the local cluster when you are finished testing:

```bash
minikube stop
minikube delete

```
