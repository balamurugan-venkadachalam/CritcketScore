#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# localstack/init/01-dynamodb.sh
#
# Creates the DynamoDB tables required by the T20 Live Scoring application.
# Executed automatically by LocalStack on first startup (ready.d hook).
#
# Tables:
#   t20-score-events   – event store (PK: matchId, SK: eventSequence)
#   t20-live-scores    – materialized live score view (PK: matchId)
#   t20-replay-state   – replay job tracking (PK: matchId)
#
# All tables use PAY_PER_REQUEST billing (no capacity planning needed locally).
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

ENDPOINT="http://localhost:4566"
REGION="ap-southeast-2"
AWS="aws --endpoint-url=${ENDPOINT} --region=${REGION}"

echo "==> [01-dynamodb] Creating DynamoDB tables..."

# ── t20-score-events ─────────────────────────────────────────────────────────
# PK: matchId (S)    — Kafka partition key; groups all balls of a match
# SK: eventSequence (S) — zero-padded "inning#over#ball" for lexicographic sort
#     e.g. "1#03#2" sorts before "1#03#10" ... wait, we zero-pad ball too
#     The ScoreEvent.eventSequence() method produces: "%d#%02d#%d"
# GSI: eventId-index — for idempotency lookup by eventId
#
$AWS dynamodb create-table \
  --table-name t20-score-events \
  --attribute-definitions \
    AttributeName=matchId,AttributeType=S \
    AttributeName=eventSequence,AttributeType=S \
    AttributeName=eventId,AttributeType=S \
  --key-schema \
    AttributeName=matchId,KeyType=HASH \
    AttributeName=eventSequence,KeyType=RANGE \
  --global-secondary-indexes '[
    {
      "IndexName": "eventId-index",
      "KeySchema": [{"AttributeName":"eventId","KeyType":"HASH"}],
      "Projection": {"ProjectionType":"KEYS_ONLY"}
    }
  ]' \
  --billing-mode PAY_PER_REQUEST \
  --tags Key=Environment,Value=local Key=Project,Value=t20-live-scoring \
  2>/dev/null && echo "    ✓ t20-score-events created" || echo "    ~ t20-score-events already exists"

# ── t20-live-scores ──────────────────────────────────────────────────────────
# PK: matchId (S) — materialized view, one item per active match
# Contains: totalRuns, wickets, currentOver, currentBall, lastUpdated
#
$AWS dynamodb create-table \
  --table-name t20-live-scores \
  --attribute-definitions \
    AttributeName=matchId,AttributeType=S \
  --key-schema \
    AttributeName=matchId,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --tags Key=Environment,Value=local Key=Project,Value=t20-live-scoring \
  2>/dev/null && echo "    ✓ t20-live-scores created" || echo "    ~ t20-live-scores already exists"

# ── t20-replay-state ─────────────────────────────────────────────────────────
# PK: matchId (S) — one replay-state item per match
# Contains: status (STARTED|IN_PROGRESS|COMPLETED|FAILED), startedAt, completedAt
#
$AWS dynamodb create-table \
  --table-name t20-replay-state \
  --attribute-definitions \
    AttributeName=matchId,AttributeType=S \
  --key-schema \
    AttributeName=matchId,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --tags Key=Environment,Value=local Key=Project,Value=t20-live-scoring \
  2>/dev/null && echo "    ✓ t20-replay-state created" || echo "    ~ t20-replay-state already exists"

echo "==> [01-dynamodb] Done."
