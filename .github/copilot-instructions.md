# Trail Store

- Follow the current phase issue. Do not implement later phases early.
- One Spring Boot app, Java 25, Maven wrapper, Thymeleaf and vanilla JavaScript.
- Use constructor injection and records. Keep code small enough to explain on stage.
- The bundled inventory and CatalogService are the single source of product facts.
- No database, accounts, checkout, conversation memory, or frontend framework.
- Run `./mvnw -B -ntp verify` before finishing. On Windows use `.\mvnw.cmd`.
- Never commit credentials or local Azure environment files.
- New behavior requires tests; failures must be visible and honest.
- Keep all UI colors in the existing theme variables.
- Later Azure phases must preserve offline catalog startup.
