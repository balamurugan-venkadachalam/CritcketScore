#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# localstack/init/04-secrets.sh
#
# Creates Secrets Manager secrets that mirror the production secret names.
# The application-local.yml points to LocalStack, so the app can start with
# Secrets Manager lookups enabled without needing real AWS credentials.
#
# Secrets created:
#   t20/producer/config   – producer Kafka credentials (placeholder values)
#   t20/consumer/config   – consumer Kafka credentials (placeholder values)
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

ENDPOINT="http://localhost:4566"
REGION="ap-southeast-2"
AWS="aws --endpoint-url=${ENDPOINT} --region=${REGION}"

echo "==> [04-secrets] Creating Secrets Manager secrets..."

# Producer secret — mirrors what TlsSaslConfig.java reads in prod
$AWS secretsmanager create-secret \
  --name "t20/producer/config" \
  --description "T20 Producer Kafka credentials (local placeholder)" \
  --secret-string '{
    "kafka.bootstrap.servers": "localhost:9092",
    "kafka.sasl.username": "local-user",
    "kafka.sasl.password": "local-pass"
  }' \
  2>/dev/null && echo "    ✓ t20/producer/config created" || \
$AWS secretsmanager update-secret \
  --secret-id "t20/producer/config" \
  --secret-string '{
    "kafka.bootstrap.servers": "localhost:9092",
    "kafka.sasl.username": "local-user",
    "kafka.sasl.password": "local-pass"
  }' \
  2>/dev/null && echo "    ~ t20/producer/config updated"

# Consumer secret
$AWS secretsmanager create-secret \
  --name "t20/consumer/config" \
  --description "T20 Consumer Kafka credentials (local placeholder)" \
  --secret-string '{
    "kafka.bootstrap.servers": "localhost:9092",
    "kafka.sasl.username": "local-user",
    "kafka.sasl.password": "local-pass"
  }' \
  2>/dev/null && echo "    ✓ t20/consumer/config created" || \
$AWS secretsmanager update-secret \
  --secret-id "t20/consumer/config" \
  --secret-string '{
    "kafka.bootstrap.servers": "localhost:9092",
    "kafka.sasl.username": "local-user",
    "kafka.sasl.password": "local-pass"
  }' \
  2>/dev/null && echo "    ~ t20/consumer/config updated"

echo "==> [04-secrets] Done."
echo ""
echo "Local Secrets Manager endpoint: http://localhost:4566"
echo "Secret ARN pattern: arn:aws:secretsmanager:ap-southeast-2:000000000000:secret:<name>"
