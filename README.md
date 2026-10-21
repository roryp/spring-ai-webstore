# Trail Store

A tiny Spring AI web store for a 45-minute workshop and a one-hour Microsoft live event.

Four prepared checkpoints tell the story:

1. A local Spring Boot catalog.
2. The same application deployed to Azure Container Apps.
3. Inventory questions answered with Spring AI and Microsoft Foundry.
4. Requests, tools, and token usage made visible with OpenTelemetry.

Twelve products. No database, checkout, accounts, or microservices.

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
