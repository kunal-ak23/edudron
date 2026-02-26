Act as an Expert Java/Spring Boot Software Architect.

I want to migrate my educational SaaS application, "Edudron", from a Microservices architecture to a "Modular Monolith".

Currently, the application consists of separate microservice projects (e.g., `student`, `content`, etc.) built with Spring Boot. They communicate with each other over the network via HTTP calls (e.g., using RestTemplate, WebClient, or Feign) and are deployed as separate Docker images on Azure Container Apps with an API Gateway routing traffic.

My goal is to consolidate these microservices into a single Spring Boot application deployed as a single Docker image on Azure Container Apps to reduce baseline infrastructure costs, simplify deployments, and eliminate internal network latency.

Please create a step-by-step implementation plan and help me execute this migration.

Here are the strict requirements for the refactoring:

1. **Build System Consolidation:** Restructure the repositories/folders into a single multi-module Maven (or Gradle) project. There should be a parent POM, sub-modules for each domain (`student`, `content`, etc.), and a new `application` (or `bootstrap`) module that bundles them together into a single executable JAR.
2. **Configuration Merging:** Consolidate the `application.yml` files. Ensure there are no conflicting property names, port contentions, or duplicate Spring Bean names across the modules.
3. **Refactor Inter-Module Communication:** This is critical. Identify all instances where modules communicate over HTTP. Replace these network requests with direct Java interface method calls by adding the necessary module dependencies in the `pom.xml`.
4. **Enforce Modular Boundaries:** Ensure that domains remain decoupled. Modules should only communicate through explicit public Service interfaces. Database repositories and internal business logic should be made `package-private` where possible to prevent "spaghetti code." (Consider utilizing Spring Modulith if applicable).
5. **Consolidate Security:** Merge the authentication and authorization logic (e.g., JWT validation) so it sits at the entry point of the monolith, rather than being duplicated across or handled by a separate internal gateway.
6. **Azure Deployment Adaptation:** Provide guidance on how to update my Azure Container Apps infrastructure configuration. Since the internal Gateway is no longer needed for service-to-service routing, advise on the best way to expose the monolithic endpoints externally.

Before writing any code, please analyze the current project structure (I will provide access to the files) and give me a high-level summary of the required changes and any potential major roadblocks you foresee based on my specific codebase. Once I approve the plan, we will execute it step-by-step.
