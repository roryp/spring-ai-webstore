# Step 4 · See inside every answer

With OpenTelemetry, every request leaves a **trace**: a timeline of everything the app did to answer it. Each timed step in a trace is a **span**, such as the web request, a call to the model, or a tool call. Container Apps sends the traces to Application Insights.

![The store with tracing](../docs/screenshots/step-4.png)

## What's new

The app's code doesn't change. Three small changes turn on tracing:

| File | Change |
|---|---|
| [pom.xml](pom.xml) | Adds `spring-boot-starter-opentelemetry`. |
| [application.properties](src/main/resources/application.properties) | Three lines: trace every request instead of the default 10%, and send only traces. |
| [infra/app.bicep](infra/app.bicep) | Adds Log Analytics and Application Insights, and tells the Container Apps environment to forward the app's traces there. |

The app itself doesn't say where to send traces. On Azure, Container Apps sets the standard `OTEL_EXPORTER_OTLP_*` environment variables, and Spring Boot reads them. On your laptop they aren't set, so nothing is sent.

![Diagram: the app, with OpenTelemetry, sends traces to the Container Apps environment, which forwards them to Application Insights. There you can query tokens and cost with KQL, or open one question as a timeline in Search.](../docs/diagrams/step-4-pipeline.png)

There's also one new test in [StoreApplicationTests.java](src/test/java/com/contoso/store/StoreApplicationTests.java). It checks that asking a question creates one trace that contains the web request and the Spring AI call. To keep the spans in memory, it uses `opentelemetry-sdk-testing`, a test-only dependency.

## What one question looks like

![Timeline of one real question: the 3.5-second web request contains a 1.5-second model call, a 1-millisecond tool call and a 1.9-second model call. Together they use 549 input and 52 output tokens, which cost $0.000081.](../docs/diagrams/step-4-trace.png)

| Span | What it is |
|---|---|
| `http post /ask` | The web request. |
| `chat gpt-6-luna` | First model call: the model asks for the tool. About 190 input and 13 output tokens. |
| `execute_tool listProducts` | Spring AI runs the Java method. It takes about 1 ms. |
| `chat gpt-6-luna` | Second model call: the model writes the answer. About 350 input and 30 to 55 output tokens. |

Spring AI also adds a few framework spans, such as `spring_ai chat_client`.

That's a question about the products. For an off-topic question, the model usually answers in one call, without the tool.

The traces record timings, the model name, token counts, and tool names. They don't record the question or the answer.

## See it in Azure

1. Ask the assistant a question.
2. In the [Azure portal](https://portal.azure.com), open the resource group `rg-<environment name>`, and then the Application Insights resource, whose name starts with `appi-`.
3. Wait one to three minutes for the data to arrive. Then open **Logs** and run this query. It's written in KQL, the query language for Azure logs.

```kusto
// Tokens and cost of each question.
// GPT-6 Luna, Global Standard: $0.10 per 1M input tokens, $0.50 per 1M output tokens.
dependencies
| where tostring(customDimensions["gen_ai.operation.name"]) == "chat"
| summarize askedAt = min(timestamp),
            modelCalls = count(),
            inputTokens = sum(toint(customDimensions["gen_ai.usage.input_tokens"])),
            outputTokens = sum(toint(customDimensions["gen_ai.usage.output_tokens"]))
         by operation_Id
| extend costUsd = (inputTokens * 0.10 + outputTokens * 0.50) / 1000000
| project askedAt, modelCalls, inputTokens, outputTokens, costUsd
| order by askedAt desc
```

Here's what the query returned for our test questions on 22 October 2026. We added the questions ourselves, because the traces don't contain them.

| Question | modelCalls | inputTokens | outputTokens | costUsd |
|---|---|---|---|---|
| Which jackets under $150 are in stock? | 2 | 533 | 43 | 0.000075 |
| Is the Summit Waterproof Jacket in stock? If not, what should I buy instead? | 2 | 549 | 52 | 0.000081 |
| I'm camping this weekend and have $400. What should I buy? | 2 | 543 | 65 | 0.000087 |
| Do you sell kayaks? | 2 | 527 | 44 | 0.000075 |
| Write me a poem about the weather. | 1 | 188 | 22 | 0.000030 |

To see one question as a timeline, go to **Investigate** > **Search**, and select an `http post /ask` request. This opens its transaction diagnostics.

## Deploy it

You need the same tools as in [step 2](../step-2-azure/README.md#deploy-it).

```bash
cd step-4-observe
azd auth login
azd up
```

Use a new environment name, such as `trail-step4`. As in step 3, pick a region where your subscription has quota for `gpt-6-luna`.

As in step 3, the first question after a quiet spell can take up to a minute while the app starts.

## Test it

Like step 3, this step needs Azure to run. The tests don't, because they use a fake model. Run `./mvnw verify`. On Windows, run `.\mvnw.cmd verify`.

## Cost

This step costs the same as step 3, plus Log Analytics, where the first 5 GB a month are free. A demo sends a few megabytes. For the details, see [What it costs](../README.md#what-it-costs).

## Clean up

```bash
azd down --purge
```

**Back to the start:** [README](../README.md)
