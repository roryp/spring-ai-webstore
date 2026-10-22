# After the workshop

All three cloud apps share a dedicated resource group, registry, Container Apps
environment, Foundry resource, and logging resources. Do not delete one phase's
shared infrastructure while another phase is still in use.

## Keeping the demo temporarily

Restore zero minimum replicas for `ca-trail-stage-2`, `ca-trail-stage-3`, and
`ca-trail-stage-4` with `az containerapp update --min-replicas 0`, scoped to your
approved subscription and resource group. Do this after attendees finish.

Scale-to-zero does not remove registry/storage/log charges. Model inference still
costs money while requests are made. The in-memory application limits are not an
Azure billing cap.

## Removing it completely

1. Confirm the subscription and the dedicated resource group in the Azure portal.
2. Confirm it contains no unrelated resources and nobody needs the workshop URLs.
3. Disable the repository's Azure deployment workflow (or remove the deployment
   client-ID variable), so a future push does not try to deploy into a removed group.
4. Delete the external `trail-store-assistant` registration in the Foundry project.
5. Review the resources shown by `azd down`, then approve removal of the dedicated group.
6. Review any soft-deleted Foundry account and purge it only if permanent removal is intended.
7. Confirm no demo resources or billable services remain. Review the managed identity
   and its federated credential, which were created in the same dedicated group.

Never run a broad subscription cleanup or delete unrelated resource groups.

## Local

Stop the local presenter app with Ctrl+C in its terminal. Git worktrees under
`.demo` are deliberately retained as offline code snapshots. If you remove them
later, use `git worktree remove` on each specific checkpoint after checking for
local edits. Do not recursively delete your repository or home directory.
