# Canary releases gated by the eval; model provider chosen by a Statsig experiment

In production the Ask service runs on Kubernetes and every new version ships through an Argo Rollouts canary (5% → 25% → 100%). Promotion requires the eval's Hard Lines to pass on the new image and the live 5xx rate, p95 and Output Guardrail drop rate to stay within bounds. Which model answers a request is a separate, runtime decision made by the Statsig experiment `model_provider`. We split these because they change at different speeds and carry different risks: a code or prompt change needs a new version and a canary, while moving traffic between Anthropic and DeepSeek should take seconds, be measurable per request, and need no deploy.

## Considered Options

- **Provider choice by env var per deployment.** What the built CLI does; in production it would need a redeploy to change providers and couldn't compare them on live traffic.
- **Statsig for releases as well.** A release gate per build version would duplicate what the canary already does, with less control over automated analysis and rollback.
