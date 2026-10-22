# Trail Store

A tiny Spring AI web store for a 45-minute workshop and a one-hour Microsoft live event.

Four prepared checkpoints tell the story:

1. A local Spring Boot catalog.
2. The same application deployed to Azure Container Apps.
3. Inventory questions answered with Spring AI and Microsoft Foundry.
4. Requests, tools, and token usage made visible with OpenTelemetry.

Twelve products. No database, checkout, accounts, or microservices.

## Start here: presenter edition

**Everything is prepared before the event. No live builds, deployments, or branch switching.**

| Phase | Prepared app | Code checkpoint |
|---|---|---|
| 1. Local | [Local catalog](http://127.0.0.1:8080), started below | [stage-1](https://github.com/roryp/spring-ai-webstore/tree/stage-1) |
| 2. Azure | [Azure catalog](https://ca-trail-stage-2.proudocean-b53ddbc9.swedencentral.azurecontainerapps.io) | [stage-2](https://github.com/roryp/spring-ai-webstore/tree/stage-2) |
| 3. AI | [Inventory assistant](https://ca-trail-stage-3.proudocean-b53ddbc9.swedencentral.azurecontainerapps.io) | [stage-3](https://github.com/roryp/spring-ai-webstore/tree/stage-3) |
| 4. Observe | [Observable assistant](https://ca-trail-stage-4.proudocean-b53ddbc9.swedencentral.azurecontainerapps.io) | [stage-4](https://github.com/roryp/spring-ai-webstore/tree/stage-4) |

Before the event, in PowerShell 7:

```powershell
.\scripts\Prepare-Presenter.ps1 -WarmAzure
.\scripts\Start-Local.ps1
```

The first command prepares immutable code folders and warms the cloud apps.
The second runs the already-built local checkpoint; leave its terminal open.
For local-only preparation, omit `-WarmAzure`. After the first preparation,
`-Offline` works with cached dependencies and local tags.

- [One-hour presenter cue sheet](docs/presenter.md): what to click, say, show, and use if something fails.
- [45-minute participant guide](docs/workshop.md): browser-only, no Azure subscription needed.
- [Offline backup gallery](http://127.0.0.1:8080/backups.html): four **real recordings**, screenshots, and captured token results.
- [Verification and remaining human checks](docs/verification.md).

The AI apps require the presenter-supplied workshop code. On the prepared machine
it is in the gitignored `.azure/workshop-access.txt`; it is deliberately not in this
repository. Ask the presenter if you are following along. Each question starts fresh.

The Application Insights connection and Foundry external-agent registration are
verified through their APIs. **Visual portal sign-in and a timed presenter/audience
rehearsal remain human pre-event checks.** The local backups do not depend on them.

Cloud resources are billable. The preparation command keeps one replica warm per
app; restore zero after the event or follow [cleanup](docs/cleanup.md).

## Run this checkpoint

Install JDK 25. The Maven wrapper downloads Maven on the first run.

```powershell
.\mvnw.cmd spring-boot:run
```

On macOS/Linux: `./mvnw spring-boot:run`. Open <http://localhost:8080>.
If Maven reports an invalid `JAVA_HOME`, point it at your JDK 25 installation.

## Verify

```powershell
.\mvnw.cmd -B -ntp verify
```

The tests use the bundled inventory and do not call Azure.

## What to show

Open the catalog, filter for **Jackets** and **In stock only**, and find the same
products in [inventory.json](src/main/resources/inventory.json).
The [catalog service](src/main/java/com/contoso/store/catalog/CatalogService.java)
is the single source of product facts for the page and JSON API.

- `GET /api/products?category=Jackets&inStock=true`
- `GET /api/products/JKT-003`

Prices are USD. Inventory is read-only and packaged with the application.
There is intentionally no checkout or persistent stock management.

## Prepared phases

Implementation is tracked in the [roadmap](https://github.com/roryp/spring-ai-webstore/issues/1).
Each completed phase receives an immutable `stage-N` tag.
Check out `stage-1` for the standalone version without cloud dependencies.
This checkpoint adds Azure deployment without changing the catalog.

See [Azure preparation](docs/azure.md) for `azd up`, protected GitHub deployment,
warming the app before the event, pricing, and cleanup.

Phase 3 adds **Ask Trail**, powered by Spring AI and Microsoft Foundry.
The default local run remains offline and credential-free. See
[inventory chat preparation](docs/assistant.md) for the live profile, workshop
access, limits, and the three rehearsed questions.

Phase 4 adds [OpenTelemetry and the prepared trace walkthrough](docs/observability.md).
Raw chat content is not logged. The widget shows a real trace ID when tracing is
active; Application Insights and Foundry provide the operator views.
