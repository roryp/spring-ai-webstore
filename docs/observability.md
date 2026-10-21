# One question, one trace

Phase 4 keeps the same store and assistant. It adds vendor-neutral OpenTelemetry:

`Browser -> HTTP request -> invoke_agent -> Spring AI model -> inventory tool -> model`

The application exports OTLP/gRPC to the Container Apps managed collector.
That collector forwards traces to workspace-based Application Insights.
The same Application Insights resource is connected to the Foundry project.
An external-agent registration matches `gen_ai.agent.id=trail-store-assistant`.
The assistant still runs in Container Apps, not Foundry Agent Service.

## The small code change to show

- [The observation wrapper](../src/main/java/com/contoso/store/assistant/AssistantService.java)
  wraps a question in `invoke_agent` and carries the observation into its bounded worker.
- [AssistantObservations](../src/main/java/com/contoso/store/observability/AssistantObservations.java)
  attributes the request, model, and tool spans to the registered assistant.
- [The observe profile](../src/main/resources/application-observe.properties)
  enables gRPC export. Default local runs do not export or need a collector.

An integration test uses a real local HTTP request and the real OpenAI transport
against a fake loopback model. It proves the HTTP/model/tool spans share a trace,
two model calls total exactly 300 input and 50 output tokens, cached input is
captured, and prompts, cookies, and workshop codes are absent from attributes.
Those fixed test token counts are NOT presented as live Azure usage.

## Prepare phase 4

```powershell
azd env new trail-stage-4 --subscription <subscription-id> --location swedencentral
azd env set AZURE_RESOURCE_GROUP rg-trail-store
azd env set STORE_PHASE 4
azd env set AZURE_PRINCIPAL_ID (az ad signed-in-user show --query id --output tsv)
.\scripts\New-WorkshopAccess.ps1
azd env set BUILD_REVISION (git rev-parse HEAD)
azd up
.\scripts\Register-Assistant.ps1
.\scripts\Test-Demo.ps1 -BaseUrl <phase-4-url> -Phase 4 -SaveCapture
```

Run from the repository containing the private workshop code so both AI phases
use the same code. Do not rotate it accidentally in a second checkout.
Deploy in phase order and freeze the apps; do not re-provision an earlier
checkpoint over the final shared environment.

The collector and external-agent connection use preview infrastructure/API
surfaces. Verify them before the event. They are not a production SLA promise.

## Before the audience arrives

1. Ask the three prepared questions and copy a real trace ID from the widget.
2. Allow ingestion time during preparation, not on stage.
3. In Application Insights, open the request's end-to-end transaction and expand
   the model and `execute_tool` spans. Save the selected trace as a bookmark.
4. Run [tokens.kql](kql/tokens.kql) in Application Insights Logs.
5. In Foundry, open the `trail-store` project, Agents, `trail-store-assistant`,
   and Traces. Verify the same trace is attributed to the external agent.
6. Save sanitized captures locally. Keep an already-ingested trace open.

For the trace tree, [trace.kql](kql/trace.kql) joins requests and dependencies
by the real trace ID. Do not use the unrelated request UUID as a trace ID.

## Tokens and cost: what to say

"A question can make more than one model call. We count all of them, keep the
tool results small, cap generated tokens, and limit how many questions can run."

[cost.kql](kql/cost.kql) deliberately leaves rates null until you supply verified
prices for the deployed model/version and currency. The retail pricing lookup
did not return a verified GPT-6 Luna price during preparation; no price is invented.
Token usage is useful without pretending it is an Azure invoice.

The calculation subtracts cached input from normal input before pricing it.
Reasoning tokens are already part of output usage and must not be added again.
Missing usage or missing prices mean "unknown", never a zero-cost answer.
Keep all calls in the comparison and use identical questions and inventory.
Do not assume that a smaller cap automatically saves tokens on already-short answers.

Sampling is 100% for this small demo so the displayed traces can be compared
with the model calls. It is not a durable billing ledger: exporters and ingestion
can lose data, and sampled production traces need different accounting.

## Privacy and operating boundaries

- Prompt, completion, and tool-content logging remain OFF, including on Azure.
- The portal will show operations, model identifiers, timings, and tokens, not
  raw user conversation bodies. Use the synthetic saved answer captures to show text.
- No session IDs, access codes, or authorization headers are intentionally captured.
- Only traces are exported through the managed collector. Its Application Insights
  destination does not support metrics. Console logs remain in Log Analytics.
- The App Insights connection string routes telemetry; it is not the model API
  credential. Model key authentication stays disabled and inference uses identity.
- Test the portal login and permissions before going live. If a portal is unavailable,
  use the saved capture and say explicitly that it is recorded.
