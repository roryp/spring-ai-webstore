# Inventory chat

## The code to explain

1. [InventoryTools](../src/main/java/com/contoso/store/assistant/InventoryTools.java):
   two read-only methods call the same catalog service as the page.
2. [AssistantConfiguration](../src/main/java/com/contoso/store/assistant/AssistantConfiguration.java):
   a Spring AI ChatClient, a short prompt, and a bounded tool loop.
3. [The prompt](../src/main/resources/prompts/assistant.txt):
   product facts must come from tools, not the model's memory.

Questions are independent. There is no chat history, session identifier, or
database. The authoritative inventory is bundled with the app; changing it
requires a new deployment. No tool can buy products or modify stock.

## Local/offline

The default profile disables every OpenAI model auto-configuration. The catalog
works without Azure credentials and the assistant explicitly reports offline.
Tests never contact a real AI endpoint. One test uses a loopback HTTP server to
prove the deployment name is sent on the wire.

For a live local run, first deploy the AI environment below, then:

```powershell
az login
$env:AZURE_OPENAI_ENDPOINT = azd env get-value AZURE_OPENAI_ENDPOINT
$env:AZURE_OPENAI_DEPLOYMENT = azd env get-value AZURE_OPENAI_DEPLOYMENT
$env:WORKSHOP_ACCESS_CODE_HASH = azd env get-value WORKSHOP_ACCESS_CODE_HASH
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=ai"
```

Do not import the whole azd environment into your shell. In particular, its
`AZURE_CLIENT_ID` belongs to the deployed managed identity, not your local user.
Do not set `OPENAI_API_KEY` or `MICROSOFT_FOUNDRY_API_KEY`; local key authentication
is disabled on this Foundry account.

## Prepare the separate Azure AI checkpoint

Use the same dedicated resource group as phase 2. Phase 2's app is not updated.

```powershell
azd env new trail-stage-3 --subscription <subscription-id> --location swedencentral
azd env set AZURE_RESOURCE_GROUP rg-trail-store
azd env set STORE_PHASE 3
azd env set AZURE_PRINCIPAL_ID (az ad signed-in-user show --query id --output tsv)
.\scripts\New-WorkshopAccess.ps1
azd env set BUILD_REVISION (git rev-parse HEAD)
azd up
```

The script creates a random code in the gitignored `.azure/workshop-access.txt`.
Only a SHA-256 hash is deployed. Open the file locally and share the code privately
with participants. Enter it once in **Ask Trail > Workshop access** before presenting.
The browser remembers it only for that tab. This is a simple shared workshop gate,
not a user-account or production authorization system.

The model uses the Container App's managed identity with the Foundry User role.
The developer gets that role for local verification. The GitHub deployment
identity does not receive model inference access. There are no model API keys.

The Global Standard model deployment has a configurable capacity of 100, subject
to subscription quota. This is a throughput allocation, not a purchased PTU.
The runtime sends the deployment name `chat`, not the underlying model name.

## Limits and failure behavior

| Boundary | Limit |
|---|---|
| JSON body | 4,096 bytes, known Content-Length required |
| Question | 600 characters |
| Concurrent model questions per app | 2, with no waiting queue |
| Accepted questions per app | 20/minute and 240/hour |
| Tool results | At most 5 products per search |
| Tool executions per question | 3; at most 4 model calls |
| Per-model-call timeout | 8 seconds, no SDK retries |
| Whole-question deadline | 30 seconds, then cancellation |
| Generated tokens per model call | 600, including reasoning tokens |

The two AI phase apps have independent in-memory request budgets. Limits reset
on restart and are not billing controls. Keep one maximum replica per app; these
are not distributed rate limiters. Rehearse the expected group size before the event.
The provider can still throttle shared quota; failures are explicit and the catalog
stays available. Token prices and Azure charges are not assumed by the application.

On failure, the API returns a non-2xx Problem Details response. It never substitutes
a canned AI answer. The UI renders model output as text, not HTML.

## Three rehearsal questions

| Question | Required facts |
|---|---|
| Which waterproof jackets under $150 are in stock? | JKT-001: $99, stock 12; JKT-002: $139, stock 4; no recommendation of JKT-003 as available |
| Is JKT-003 available? Show an in-stock alternative. | JKT-003: out of stock; alternative from the current jacket inventory |
| Show two in-stock items for a rainy day hike, with their prices and SKUs. | Both SKUs exist, stock is positive, and quoted prices match the cards |

Also check an unknown SKU, an off-topic question, and an attempted instruction
override. These checks are preparation work. Do not try surprise prompts on stage.
