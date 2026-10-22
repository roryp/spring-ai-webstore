# Trail Store

This repo teaches cloud-native Java in four small steps. Each `step-*` folder is a complete app on its own.

- Keep every step at a 101 level: small, readable, and easy to explain on stage.
- A step adds only its own idea to the step before it. If you fix something shared, fix it in every step.
- Spring Boot, Java 25, Maven wrapper and Thymeleaf. No JavaScript framework, database, accounts or checkout.
- `Catalog` is the only source of product facts. The assistant reads it through `InventoryTools`.
- Use constructor injection and records.
- Steps 2 to 4 deploy with `azd up`. Steps 3 and 4 need Azure to start; their tests use a fake model and never call Azure.
- No API keys or passwords: the app signs in to Azure with its managed identity.
- Run `./mvnw -B -ntp verify` in every step you change. On Windows use `.\mvnw.cmd -B -ntp verify`.
- Never commit credentials or `.azure/` folders.
- New behavior needs a test. Failures must be visible and honest.
- Keep UI colors in the CSS variables at the top of `style.css`.
