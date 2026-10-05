#!/usr/bin/env bash
# One-time bootstrap of the Google Cloud resources the pipeline deploys into.
# Usage: PROJECT_ID=my-project GITHUB_REPO=SajeevU/flight-plan CAAS_API_KEY=xxx ./infra/setup-gcp.sh
set -euo pipefail

: "${PROJECT_ID:?set PROJECT_ID}"
: "${GITHUB_REPO:?set GITHUB_REPO (owner/name)}"
REGION="${REGION:-asia-southeast1}"
SA="github-deployer@${PROJECT_ID}.iam.gserviceaccount.com"

gcloud config set project "$PROJECT_ID"
gcloud services enable run.googleapis.com artifactregistry.googleapis.com secretmanager.googleapis.com iamcredentials.googleapis.com

# Container registry for the images CI pushes.
gcloud artifacts repositories describe flight-plan --location "$REGION" >/dev/null 2>&1 ||
  gcloud artifacts repositories create flight-plan --repository-format docker --location "$REGION"

# CAAS API key lives in Secret Manager, never in the repo or the image.
if [[ -n "${CAAS_API_KEY:-}" ]]; then
  gcloud secrets describe caas-api-key >/dev/null 2>&1 || gcloud secrets create caas-api-key --replication-policy automatic
  printf '%s' "$CAAS_API_KEY" | gcloud secrets versions add caas-api-key --data-file=-
fi

# Deployer identity used by GitHub Actions.
gcloud iam service-accounts describe "$SA" >/dev/null 2>&1 ||
  gcloud iam service-accounts create github-deployer --display-name "GitHub Actions deployer"
for role in roles/run.admin roles/artifactregistry.writer roles/iam.serviceAccountUser; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" --member "serviceAccount:$SA" --role "$role" --condition None >/dev/null
done
# Cloud Run's runtime identity must be able to read the secret.
PROJECT_NUMBER=$(gcloud projects describe "$PROJECT_ID" --format 'value(projectNumber)')
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member "serviceAccount:${PROJECT_NUMBER}-compute@developer.gserviceaccount.com" \
  --role roles/secretmanager.secretAccessor --condition None >/dev/null

# Keyless auth: GitHub's OIDC token for this repo can impersonate the deployer.
gcloud iam workload-identity-pools describe github --location global >/dev/null 2>&1 ||
  gcloud iam workload-identity-pools create github --location global --display-name "GitHub"
gcloud iam workload-identity-pools providers describe github --location global --workload-identity-pool github >/dev/null 2>&1 ||
  gcloud iam workload-identity-pools providers create-oidc github --location global --workload-identity-pool github \
    --issuer-uri https://token.actions.githubusercontent.com \
    --attribute-mapping google.subject=assertion.sub,attribute.repository=assertion.repository \
    --attribute-condition "assertion.repository == '${GITHUB_REPO}'"
gcloud iam service-accounts add-iam-policy-binding "$SA" --role roles/iam.workloadIdentityUser \
  --member "principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/github/attribute.repository/${GITHUB_REPO}" >/dev/null

cat <<OUT

Done. Add these as GitHub repository variables (Settings > Secrets and variables > Actions > Variables):
  GCP_PROJECT_ID      = ${PROJECT_ID}
  GCP_REGION          = ${REGION}
  GCP_SERVICE_ACCOUNT = ${SA}
  GCP_WIF_PROVIDER    = projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/github/providers/github
OUT
