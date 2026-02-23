#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# localstack/init/02-s3.sh
#
# Creates S3 buckets used by the T20 Live Scoring application locally.
# Executed automatically by LocalStack on first startup.
#
# Buckets:
#   t20-artifacts-local  – general artifact storage (logs, exports)
#   t20-vpc-flow-logs-local – VPC flow log simulation (mirrors prod bucket purpose)
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

ENDPOINT="http://localhost:4566"
REGION="ap-southeast-2"
AWS="aws --endpoint-url=${ENDPOINT} --region=${REGION}"

echo "==> [02-s3] Creating S3 buckets..."

$AWS s3api create-bucket \
  --bucket t20-artifacts-local \
  --region "${REGION}" \
  --create-bucket-configuration LocationConstraint="${REGION}" \
  2>/dev/null && echo "    ✓ t20-artifacts-local created" || echo "    ~ t20-artifacts-local already exists"

$AWS s3api create-bucket \
  --bucket t20-vpc-flow-logs-local \
  --region "${REGION}" \
  --create-bucket-configuration LocationConstraint="${REGION}" \
  2>/dev/null && echo "    ✓ t20-vpc-flow-logs-local created" || echo "    ~ t20-vpc-flow-logs-local already exists"

echo "==> [02-s3] Done."
