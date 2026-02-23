# ─────────────────────────────────────────────────────────────────────────────
# T20 Live Scoring – Developer Makefile
#
# All targets are designed to be run from the project root directory.
# Prerequisites: Docker Desktop ≥ 4.x, Java 24, AWS CLI v2
#
# Usage:
#   make up          – start the full local stack (Kafka + LocalStack)
#   make up-minimal  – start only Kafka broker (no Control Center, faster)
#   make down        – stop all containers gracefully
#   make reset       – full teardown including Docker volumes (clean slate)
#   make logs        – tail all container logs
#   make logs-kafka  – tail only Kafka broker logs
#   make logs-ls     – tail only LocalStack logs
#   make topics      – list all Kafka topics
#   make topic-desc  – describe the main score-events topic
#   make producer    – run producer app on local profile
#   make health      – check health of all services
#   make dynamo-list – list DynamoDB tables in LocalStack
#   make secrets     – list secrets in LocalStack Secrets Manager
# ─────────────────────────────────────────────────────────────────────────────

.PHONY: up up-minimal down reset logs logs-kafka logs-ls topics topic-desc \
        producer health dynamo-list dynamo-scan-events dynamo-scan-scores \
        secrets kafka-produce kafka-consume help

COMPOSE        := docker compose
KAFKA_BROKER   := localhost:9092
AWS_CLI        := aws --endpoint-url=http://localhost:4566 --region=ap-southeast-2\
                      --no-sign-request
TOPIC_MAIN     := t20-match-scores
KAFKA_IMG      := confluentinc/cp-kafka:7.7.1
PRODUCER_DIR   := t20-score-producer
CONSUMER_DIR   := t20-score-consumer

# ── Colour helpers ────────────────────────────────────────────────────────────
BOLD  := \033[1m
GREEN := \033[32m
CYAN  := \033[36m
RESET := \033[0m

## help: Show this help message
help:
	@echo ""
	@echo "  $(BOLD)T20 Live Scoring – Local Dev Commands$(RESET)"
	@echo ""
	@awk 'BEGIN {FS = ":.*##"; printf ""} /^[a-zA-Z_-]+:.*?##/ { printf "  $(CYAN)%-20s$(RESET) %s\n", $$1, $$2 }' $(MAKEFILE_LIST)
	@echo ""

# ── Stack lifecycle ───────────────────────────────────────────────────────────

## up: Start the full local stack (broker + schema-registry + control-center + localstack)
up:
	@echo "$(GREEN)▶ Starting full T20 local stack...$(RESET)"
	$(COMPOSE) up -d
	@echo ""
	@echo "$(BOLD)Services:$(RESET)"
	@echo "  Kafka broker        → localhost:9092"
	@echo "  Schema Registry     → http://localhost:8081"
	@echo "  Control Center UI   → http://localhost:9021  (may take ~60s to become ready)"
	@echo "  LocalStack AWS      → http://localhost:4566"
	@echo ""
	@echo "Run $(CYAN)make health$(RESET) to check service readiness."

## up-minimal: Start only broker + localstack (faster, no UI)
up-minimal:
	@echo "$(GREEN)▶ Starting minimal stack (broker + localstack)...$(RESET)"
	$(COMPOSE) up -d broker localstack
	@echo "  Kafka broker   → localhost:9092"
	@echo "  LocalStack AWS → http://localhost:4566"

## down: Stop all containers (preserves volumes)
down:
	@echo "$(GREEN)▶ Stopping T20 local stack...$(RESET)"
	$(COMPOSE) down

## reset: Full teardown — removes containers AND volumes (clean slate)
reset:
	@echo "$(GREEN)▶ Resetting T20 local stack (removing volumes)...$(RESET)"
	$(COMPOSE) down -v --remove-orphans
	@echo "  ✓ All containers and volumes removed."

# ── Logs ──────────────────────────────────────────────────────────────────────

## logs: Tail all service logs
logs:
	$(COMPOSE) logs -f

## logs-kafka: Tail only Kafka broker logs
logs-kafka:
	$(COMPOSE) logs -f broker

## logs-ls: Tail only LocalStack logs
logs-ls:
	$(COMPOSE) logs -f localstack

# ── Health checks ─────────────────────────────────────────────────────────────

## health: Check readiness of all local services
health:
	@echo "$(BOLD)Checking service health...$(RESET)"
	@echo -n "  Kafka broker      : "
	@docker exec t20-kafka-broker kafka-broker-api-versions --bootstrap-server localhost:9092 \
	  > /dev/null 2>&1 && echo "$(GREEN)UP$(RESET)" || echo "DOWN (not ready)"
	@echo -n "  Schema Registry   : "
	@curl -s -f http://localhost:8081/subjects > /dev/null 2>&1 \
	  && echo "$(GREEN)UP$(RESET)" || echo "DOWN (not ready)"
	@echo -n "  Control Center    : "
	@curl -s -f http://localhost:9021/2.0/health > /dev/null 2>&1 \
	  && echo "$(GREEN)UP$(RESET)" || echo "DOWN (may still be starting)"
	@echo -n "  LocalStack        : "
	@curl -s -f http://localhost:4566/_localstack/health > /dev/null 2>&1 \
	  && echo "$(GREEN)UP$(RESET)" || echo "DOWN (not ready)"

# ── Kafka topic management ─────────────────────────────────────────────────────

## topics: List all Kafka topics
topics:
	@echo "$(BOLD)Kafka topics on $(KAFKA_BROKER):$(RESET)"
	@docker run --rm --network host $(KAFKA_IMG) \
	  kafka-topics --bootstrap-server $(KAFKA_BROKER) --list

## topic-desc: Describe the main score-events topic
topic-desc:
	@echo "$(BOLD)Topic: $(TOPIC_MAIN)$(RESET)"
	@docker run --rm --network host $(KAFKA_IMG) \
	  kafka-topics --bootstrap-server $(KAFKA_BROKER) \
	  --describe --topic $(TOPIC_MAIN)

## kafka-produce: Send a sample ScoreEvent JSON to the main topic (for manual testing)
kafka-produce:
	@echo "$(BOLD)Sending sample ScoreEvent to $(TOPIC_MAIN)...$(RESET)"
	@echo '{"eventId":"test-evt-001","matchId":"IPL-2025-MI-CSK-001","inning":1,"over":3,"ball":2,"team":"MI","runs":4,"extras":0,"wicket":false,"totalRuns":67,"wickets":2,"timestamp":"2025-04-15T14:32:01Z"}' | \
	  docker run --rm -i --network host $(KAFKA_IMG) \
	  kafka-console-producer \
	    --bootstrap-server $(KAFKA_BROKER) \
	    --topic $(TOPIC_MAIN) \
	    --property "parse.key=true" \
	    --property "key.separator=:" \
	    --property "key=IPL-2025-MI-CSK-001" 2>/dev/null || true
	@echo "  ✓ Message sent. Use $(CYAN)make kafka-consume$(RESET) to verify."

## kafka-consume: Consume messages from the main topic (Ctrl+C to stop)
kafka-consume:
	@echo "$(BOLD)Consuming from $(TOPIC_MAIN) (Ctrl+C to stop)...$(RESET)"
	@docker run --rm --network host $(KAFKA_IMG) \
	  kafka-console-consumer \
	    --bootstrap-server $(KAFKA_BROKER) \
	    --topic $(TOPIC_MAIN) \
	    --from-beginning \
	    --property print.key=true \
	    --property key.separator=" → "

# ── DynamoDB helpers ──────────────────────────────────────────────────────────

## dynamo-list: List all DynamoDB tables in LocalStack
dynamo-list:
	@echo "$(BOLD)DynamoDB tables in LocalStack:$(RESET)"
	@$(AWS_CLI) dynamodb list-tables --output table

## dynamo-scan-events: Scan first 10 items from t20-score-events
dynamo-scan-events:
	@echo "$(BOLD)First 10 items in t20-score-events:$(RESET)"
	@$(AWS_CLI) dynamodb scan \
	  --table-name t20-score-events \
	  --max-items 10 \
	  --output table

## dynamo-scan-scores: Scan all live scores
dynamo-scan-scores:
	@echo "$(BOLD)Live scores (t20-live-scores):$(RESET)"
	@$(AWS_CLI) dynamodb scan \
	  --table-name t20-live-scores \
	  --output table

# ── Secrets Manager helpers ────────────────────────────────────────────────────

## secrets: List all secrets in LocalStack Secrets Manager
secrets:
	@echo "$(BOLD)Secrets in LocalStack:$(RESET)"
	@$(AWS_CLI) secretsmanager list-secrets --output table

# ── Application runners ────────────────────────────────────────────────────────

## producer: Run the producer application on local profile
producer:
	@echo "$(GREEN)▶ Starting T20 Score Producer (local profile)...$(RESET)"
	@echo "  Kafka → localhost:9092 | Actuator → http://localhost:8081/actuator/health"
	./gradlew :$(PRODUCER_DIR):bootRun --args='--spring.profiles.active=local'
