# Verification and remaining checks

This is an evidence summary, not a claim that an unattended build replaces rehearsal.
Recorded AI answers are real synthetic workshop requests made on 2026-10-21.

## Verified

- Phase 1: 11 tests, twelve products, exact catalog prices/stock, filters, desktop/mobile,
  light/dark themes, and no Azure requirement.
- Phase 2: 12 tests, Bicep compilation, successful `azd up`, ACR remote build,
  non-root container, managed-identity image pull, HTTPS catalog and health probes.
  The GitHub OIDC deployment completed without a client secret:
  [successful run](https://github.com/roryp/spring-ai-webstore/actions/runs/39651609230).
- Phase 3: 30 tests, real Foundry inference using the app's identity, deployment-name
  routing, explicit offline behavior, access protection, tool and request limits,
  cancellation, and exact concurrency boundaries.
  [Successful workflow](https://github.com/roryp/spring-ai-webstore/actions/runs/39659343244).
- Phase 4: 31 tests, including a real HTTP integration test with a loopback model.
  The test proves HTTP/model/tool correlation, exactly two model calls, token/cache
  accounting, and the absence of raw prompts, cookies, and access codes in span attributes.
- All three cloud apps were provisioned as separate URLs and independent images.
- All three rehearsed questions returned the expected catalog facts on the deployed
  AI apps. An unavailable product includes verified in-stock alternatives.
- Unknown products and off-topic requests did not invent product facts in the
  tested live examples. This is not a guarantee against every possible prompt.
- Real phase-4 trace IDs were found in Application Insights. The three saved
  rehearsals each made two model calls; usage fields were present.
- Foundry's external-agent registration matches `trail-store-assistant`, and the
  project is connected to the same Application Insights resource.
- Four actual browser recordings play from the local fallback gallery with all
  external browser network traffic blocked. No fabricated successful responses.

## Repeatable commands

```powershell
.\mvnw.cmd -B -ntp verify
az bicep build --file .\infra\main.bicep --stdout
.\scripts\Prepare-Presenter.ps1
.\scripts\Prepare-Presenter.ps1 -Offline
.\scripts\Start-Local.ps1
```

Run the warm-up and live checks **before** the event:

```powershell
.\scripts\Prepare-Presenter.ps1 -WarmAzure
```

This uses a private workshop access code and the selected Azure environment.
It does not print the code or replace the frozen backup evidence.

## Intentionally not claimed complete

- [ ] Sign in to Application Insights and Foundry in the actual presentation browser;
  open and verify the selected trace views. Browser sign-in was deferred by the user.
- [ ] Deliver the presenter sequence in 50 minutes or less, leaving ten minutes for
  questions and buffer. An automated app test is not a timed speaking rehearsal.
- [ ] Have a participant unfamiliar with the code finish the browser walkthrough
  within 45 minutes.
- [ ] Confirm expected audience size and access distribution. The demo permits
  two simultaneous AI questions and 20 accepted questions/minute per app.
  Larger groups should work in pairs or use the recordings.
- [ ] Verify current model token prices before displaying a dollar estimate.
  The retail lookup did not provide a verified price; the cost query leaves rates
  null rather than inventing a value.

These checks remain tracked by [the preparation issue](https://github.com/roryp/spring-ai-webstore/issues/6).
The roadmap should remain open until the presenter is satisfied with rehearsal.

## Cost and cleanup

The apps can scale to zero. Warming them keeps instances running and billable.
Always restore zero minimum replicas when no longer rehearsing, or remove the
dedicated resource group after attendees finish. See [cleanup](cleanup.md).
