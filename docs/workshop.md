# Follow the trail: 45-minute workshop

**You need a browser. You do not need an Azure subscription, Docker, or a local JDK.**
Your presenter supplies a workshop access code for AI questions. This is a guided
walkthrough of prepared checkpoints, not a from-scratch deployment lab.

## 0-4 minutes: open the starting points

Open this guide and the [repository](https://github.com/roryp/spring-ai-webstore).
Keep the [recorded captures](media) available if a shared service is busy.

## 4-11 minutes: it works locally

Watch the presenter open the local app.
Read the [phase-1 inventory](https://github.com/roryp/spring-ai-webstore/blob/stage-1/src/main/resources/inventory.json)
and [catalog service](https://github.com/roryp/spring-ai-webstore/blob/stage-1/src/main/java/com/contoso/store/catalog/CatalogService.java).

**Try:** find the price and stock count for JKT-003. Why should we not recommend it as available?

## 11-20 minutes: the same app on Azure

Open the [Azure catalog](https://ca-trail-stage-2.proudocean-b53ddbc9.swedencentral.azurecontainerapps.io).
Select **Jackets** and **In stock only**. There should be two results.

Read [azure.yaml](https://github.com/roryp/spring-ai-webstore/blob/stage-2/azure.yaml)
and the [completed deployment PR](https://github.com/roryp/spring-ai-webstore/pull/8).
The presenter shows the successful GitHub workflow, not a deployment running live.

**Find:** the application is packaged in a container; Bicep describes its resources;
the app identity pulls its image. The inventory code has not become more complicated.

## 20-32 minutes: ask the inventory

Open the [AI catalog](https://ca-trail-stage-3.proudocean-b53ddbc9.swedencentral.azurecontainerapps.io).
Click **Ask Trail**, open **Workshop access**, and enter the supplied code.
Click the first suggested question, then **Ask the inventory**.

Expected facts: JKT-001 costs $99 and has 12 in stock; JKT-002 costs $139 and has 4.
Try the JKT-003 alternative question next.

Read the two methods in [InventoryTools](https://github.com/roryp/spring-ai-webstore/blob/stage-3/src/main/java/com/contoso/store/assistant/InventoryTools.java).
Both use the same catalog as the product page. An unavailable product's lookup
includes verified in-stock alternatives.

**Important:** each question is independent. There is no checkout or chat memory.
Shared demos admit two simultaneous questions. Work in pairs or stagger requests.
If busy, use the [real recorded answers](media/stage-3-answers.json); you can finish
the exercise without making another paid request.

## 32-40 minutes: see what happened

Open the [observable AI app](https://ca-trail-stage-4.proudocean-b53ddbc9.swedencentral.azurecontainerapps.io).
Enter the same workshop code and ask one question. Notice the trace ID below the answer.

Follow the presenter's **already-ingested** Application Insights trace:
HTTP request, assistant, model call, inventory tool, then another model call.
The Foundry view attributes those spans to the registered external assistant.

**Find:** why can one question cost more than one model call?
The [token query](kql/tokens.kql) counts model spans only. Raw prompts and access
codes are not logged. Token-based estimates are not the Azure bill.

No portal access is required for attendees; use the sanitized captures supplied
by the presenter.

## 40-45 minutes: recap

Explain the four changes to a partner: local, Azure, AI, observe.
Keep the [tagged releases](https://github.com/roryp/spring-ai-webstore/releases)
for later exploration.

**Optional after the workshop:** install JDK 25, clone a checkpoint, and follow
the README to run locally. Deploying into your own Azure subscription is a separate
exercise with billable resources, not part of today's 45-minute completion criteria.
