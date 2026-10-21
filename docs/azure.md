# Azure preparation, not a live exercise

Use a dedicated subscription/resource group that you are authorized to provision.
Resources are billable. This checkpoint uses one 0.5-vCPU / 1-GiB Container App,
a Basic registry, a shared Consumption environment, and Log Analytics.
Apps default to zero minimum replicas and a maximum of one.

## Deploy

Prerequisites: JDK 25, Azure CLI, Azure Developer CLI, and permission to create
resources and assign roles. The image builds in ACR; local Docker is not needed.

```powershell
azd auth login
azd env new trail-stage-2 --subscription <subscription-id> --location swedencentral
azd env set AZURE_RESOURCE_GROUP rg-trail-store
azd env set STORE_PHASE 2
azd env set BUILD_REVISION (git rev-parse HEAD)
azd up
```

`azure.yaml` selects the specific application by resource name, so later phase
apps can coexist in the same resource group without ambiguous service discovery.
Shared registry/environment names are derived from the resource group. Later
phases get distinct app names and identities. Deploy checkpoints in order.

Re-provisioning uses `SERVICE_WEB_RESOURCE_EXISTS` to retain the deployed image.
Do not set that value to true before the first deployment.

## GitHub deployment

The deployment workflow runs tests and validates Bicep before accessing Azure.
PR CI has no Azure permissions. Deployment runs only on `main` or a manual
dispatch, using the protected `workshop` environment.

Before enabling it:
1. Create a dedicated deployment identity scoped to the demo resources.
2. Configure GitHub OIDC trust for this repository's `workshop` environment.
3. Restrict that GitHub environment to `main`.
4. Set repository variables `AZURE_DEPLOY_CLIENT_ID`, `AZURE_TENANT_ID`,
   `AZURE_SUBSCRIPTION_ID`, `AZURE_LOCATION`, and `AZURE_RESOURCE_GROUP`.
5. Confirm the actual OIDC subject matches the repository's current subject format.

Bootstrap provisioning and role assignments are performed by an authorized
operator. Do not grant untrusted PR workflows provisioning privileges.
Never create a deployment client secret.

## Warm before presenting

Before the event, set `MIN_REPLICAS=1` and re-provision the prepared environment.
Open the app and health URL, then leave the verified tabs open.
Restore `MIN_REPLICAS=0` after the event if keeping the environment temporarily.
Do not change this configuration on stage.

## Cleanup

The phases share infrastructure: delete it only after all attendees finish.
Review the dedicated resource group in Azure first, then run `azd down`.
Confirm the resource group and its billable resources are gone. Never run cleanup
against a resource group containing unrelated resources.

## Pricing snapshot

Retail rates checked on 2026-10-21 for Sweden Central, USD, before free allowances:
- Basic registry: $0.1666/day, plus applicable build/storage usage.
- Active CPU: $0.000024/vCPU-second.
- Memory: $0.000003/GiB-second.
- Three 0.5-vCPU / 1-GiB apps continuously active: approximately $0.162/hour.

This is not a full quote or spending cap. Logging, requests, network traffic, and
later AI usage are additional. Scale-to-zero reduces idle application charges,
but does not remove registry or retained log charges.
