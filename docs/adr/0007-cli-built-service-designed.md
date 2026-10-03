# Build the core as a CLI; design the service around it

The design describes a production Ask service (`POST /api/v1/asks`) behind an API Gateway that authenticates clients and rate-limits them, with retry, a circuit breaker, Opik tracing, a Statsig provider experiment and a canary rollout. The exercise builds only its core: knowledge base, retrieval, both guardrails, Conflict rendering, model adapters with a timeout, traces and the eval, run in-process from a CLI. We chose this so a reviewer can install and run it in under ten minutes with one API key and no infrastructure, while the design still shows how it would run in production. Because the CLI, the future HTTP controller and the eval share one `Ask` use case, adding the service is new adapters, not a redesign.

## Consequences

- The API Gateway, retry, circuit breaker, Opik and Statsig appear in the README with their settings but have no code or tests here; the README marks every such part "designed".
- Measured latency and cost come from the in-process path and exclude network hops and queueing a deployed service would add.
