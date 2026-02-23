#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# localstack/init/03-sns.sh
#
# Creates SNS topics used by the consumer's dead-letter notification service.
# Executed automatically by LocalStack on first startup.
#
# Topics:
#   t20-dlt-alerts    – receives SNS notifications when events land in DLT
#   t20-ops-alarms    – general operational alarms
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

ENDPOINT="http://localhost:4566"
REGION="ap-southeast-2"
AWS="aws --endpoint-url=${ENDPOINT} --region=${REGION}"

echo "==> [03-sns] Creating SNS topics..."

DLT_ALERT_ARN=$($AWS sns create-topic \
  --name t20-dlt-alerts \
  --tags Key=Environment,Value=local \
  --query 'TopicArn' \
  --output text 2>/dev/null)
echo "    ✓ t20-dlt-alerts: ${DLT_ALERT_ARN}"

OPS_ALARM_ARN=$($AWS sns create-topic \
  --name t20-ops-alarms \
  --tags Key=Environment,Value=local \
  --query 'TopicArn' \
  --output text 2>/dev/null)
echo "    ✓ t20-ops-alarms: ${OPS_ALARM_ARN}"

# Save ARNs to a temp file so application-local.yml can reference them
# (or use fixed LocalStack ARN pattern: arn:aws:sns:ap-southeast-2:000000000000:<name>)
echo "SNS_DLT_ALERT_TOPIC_ARN=${DLT_ALERT_ARN}" > /tmp/t20-sns-arns.env
echo "SNS_OPS_ALARM_TOPIC_ARN=${OPS_ALARM_ARN}" >> /tmp/t20-sns-arns.env

echo "==> [03-sns] Done."
