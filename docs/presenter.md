# Presenter cue sheet: one hour, point and click

**Do not build, deploy, merge, change branches, or wait for an agent on stage.**
Tell the audience these are prepared snapshots of one application's evolution.

## Before the event

1. Run `.\scripts\Prepare-Presenter.ps1 -WarmAzure` with your prepared azd environment selected.
2. In another terminal, run `.\scripts\Start-Local.ps1`. Leave it running.
3. Enter the private workshop code once on each AI app's tab; do not display the code on a recording.
4. Sign in to Application Insights and Foundry. Open an existing verified trace and token-query results.
5. Open [local backups](http://127.0.0.1:8080/backups.html) and verify recordings play without internet.
6. Set browser/VS Code zoom for the room, disable notifications, and close unrelated tabs.

The preflight prints today's trace IDs. It does not overwrite the frozen backup captures.

The code lives in the gitignored `.azure/workshop-access.txt`. Share it privately
with workshop participants, not in the public repository.

## Your sequence

| Time | Click | Say | Show only |
|---|---|---|---|
| 0-5 | This roadmap | "Modernization creates the foundation for what comes next." | The four phase names |
| 5-13 | [Local catalog](http://127.0.0.1:8080) | "It works on my laptop." | A filter; phase-1 CatalogService |
| 13-25 | [Azure catalog](https://ca-trail-stage-2.proudocean-b53ddbc9.swedencentral.azurecontainerapps.io) | "Same business code. Repeatable deployment." | azure.yaml; Bicep entry point; merged PR; green workflow |
| 25-40 | [AI catalog](https://ca-trail-stage-3.proudocean-b53ddbc9.swedencentral.azurecontainerapps.io) | "The model asks our application for product facts." | Two prepared questions; InventoryTools; managed identity |
| 40-50 | [Observed app](https://ca-trail-stage-4.proudocean-b53ddbc9.swedencentral.azurecontainerapps.io), then saved trace | "Now I can see the request, tool calls, and tokens." | One existing trace; one token query; Foundry's external-agent view |
| 50-60 | Recap and Q&A | "Local. Azure. AI. Observe." | Participant guide and releases |

### Exactly two live AI prompts

1. "Which waterproof jackets under $150 are in stock?"
2. "Is JKT-003 available? Show an in-stock alternative."

If desired, use "Show two in-stock items for a rainy day hike, with their prices
and SKUs" as the third, rehearsed example. No surprise audience prompts are needed.
While an answer is pending, explain the tool method. Do not repeatedly click Send.

### The cost sentence

"Each question can make several model calls. We cap the output, tool calls, input,
concurrency, and request rate. These token counts help us reason about cost."

Do not quote an unverified model price, promise a tuning change saves money, or
describe trace estimates as billing. The saved cost query keeps unknown rates null.

## If anything fails

Allow one short retry, then open the corresponding item in the local backup gallery.
Say: **"This is the recorded run of the same checkpoint."** Continue explaining the code.

| Problem | Fallback |
|---|---|
| Local app unavailable | Open the saved phase-1 screenshot directly from docs/media |
| Azure unavailable | Local phase recording or screenshot |
| Chat slow, busy, or throttled | Recorded answer/clip; never pretend a canned answer is live |
| Fresh trace not visible | The existing trace you opened before the event |
| Portal logged out | Saved sanitized trace capture/results |
| Network down | Local code snapshots in .demo and local backup gallery |

## After the event

Restore zero minimum replicas if keeping the demo temporarily. Delete the dedicated
resource group only when all attendees are finished. See [cleanup](cleanup.md).
No cleanup or identity changes are part of the live presentation.

## Final human checks

- [ ] Rehearse this exact sequence within 50 minutes, leaving ten for Q&A/buffer.
- [ ] Have someone who did not build the app complete the participant guide within 45 minutes.
- [ ] Confirm the expected audience fits the shared demo limits or use the recorded path.
- [ ] Verify portal sign-in and the selected trace on the actual presentation machine.
