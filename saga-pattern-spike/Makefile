# Makefile for Saga Pattern Spike Project
# Provides convenient targets for building, testing, and running load tests

.PHONY: help build test run clean \
        infra-up infra-down infra-logs infra-reset \
        load-test-smoke load-test-load load-test-stress load-test-soak \
        load-test-all load-test-docker

# Default target
.DEFAULT_GOAL := help

# Variables
GRADLE := ./gradlew
K6 := k6
DOCKER_COMPOSE := docker compose
K6_SCRIPTS_DIR := load-tests/scripts/scenarios
K6_RESULTS_DIR := load-tests/results
BASE_URL ?= http://localhost:8080

# Colors for output
CYAN := \033[36m
GREEN := \033[32m
YELLOW := \033[33m
RESET := \033[0m

#------------------------------------------------------------------------------
# Help
#------------------------------------------------------------------------------

help: ## Show this help message
	@echo "$(CYAN)Saga Pattern Spike - Available Commands$(RESET)"
	@echo ""
	@echo "$(GREEN)Build & Test:$(RESET)"
	@grep -E '^(build|test|run|clean):.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(CYAN)%-20s$(RESET) %s\n", $$1, $$2}'
	@echo ""
	@echo "$(GREEN)Infrastructure:$(RESET)"
	@grep -E '^infra-.*:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(CYAN)%-20s$(RESET) %s\n", $$1, $$2}'
	@echo ""
	@echo "$(GREEN)Load Testing:$(RESET)"
	@grep -E '^load-test-.*:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(CYAN)%-20s$(RESET) %s\n", $$1, $$2}'
	@echo ""
	@echo "$(YELLOW)Note: Load tests require the application to be running (make run)$(RESET)"

#------------------------------------------------------------------------------
# Build & Test
#------------------------------------------------------------------------------

build: ## Build the project
	$(GRADLE) build

test: ## Run all tests
	$(GRADLE) test

test-acceptance: ## Run Cucumber acceptance tests
	$(GRADLE) test --tests "*.CucumberTestRunner"

run: ## Run the Spring Boot application
	$(GRADLE) bootRun

clean: ## Clean build artifacts
	$(GRADLE) clean

#------------------------------------------------------------------------------
# Infrastructure
#------------------------------------------------------------------------------

infra-up: ## Start all infrastructure services
	$(DOCKER_COMPOSE) up -d
	@echo "$(GREEN)Infrastructure started. Services:$(RESET)"
	@echo "  - PostgreSQL:  localhost:5432"
	@echo "  - Vault:       localhost:8200"
	@echo "  - WireMock:    localhost:8081"
	@echo "  - Jaeger:      localhost:16686"
	@echo "  - Prometheus:  localhost:9090"
	@echo "  - Pushgateway: localhost:9091"
	@echo "  - Loki:        localhost:3100"
	@echo "  - Grafana:     localhost:3000 (admin/admin)"

infra-down: ## Stop all infrastructure services
	$(DOCKER_COMPOSE) down

infra-logs: ## Show infrastructure logs
	$(DOCKER_COMPOSE) logs -f

infra-reset: ## Reset infrastructure (destroys all data)
	$(DOCKER_COMPOSE) down -v
	$(DOCKER_COMPOSE) up -d
	@echo "$(YELLOW)Infrastructure reset complete$(RESET)"

#------------------------------------------------------------------------------
# Load Testing with k6
#------------------------------------------------------------------------------

# Create results directory if it doesn't exist
$(K6_RESULTS_DIR):
	mkdir -p $(K6_RESULTS_DIR)

load-test-smoke: $(K6_RESULTS_DIR) ## Run smoke test (1-2 VUs, 1 min)
	@echo "$(CYAN)Running smoke test...$(RESET)"
	$(K6) run \
		--env BASE_URL=$(BASE_URL) \
		--out json=$(K6_RESULTS_DIR)/smoke-$$(date +%Y%m%d-%H%M%S).json \
		$(K6_SCRIPTS_DIR)/smoke.js
	@echo "$(GREEN)Smoke test complete$(RESET)"

load-test-load: $(K6_RESULTS_DIR) ## Run load test (10-50 VUs, 5 min)
	@echo "$(CYAN)Running load test...$(RESET)"
	$(K6) run \
		--env BASE_URL=$(BASE_URL) \
		--out json=$(K6_RESULTS_DIR)/load-$$(date +%Y%m%d-%H%M%S).json \
		$(K6_SCRIPTS_DIR)/load.js
	@echo "$(GREEN)Load test complete$(RESET)"

load-test-stress: $(K6_RESULTS_DIR) ## Run stress test (100-200 VUs, 10 min)
	@echo "$(CYAN)Running stress test...$(RESET)"
	@echo "$(YELLOW)WARNING: This test will push the system to its limits!$(RESET)"
	$(K6) run \
		--env BASE_URL=$(BASE_URL) \
		--out json=$(K6_RESULTS_DIR)/stress-$$(date +%Y%m%d-%H%M%S).json \
		$(K6_SCRIPTS_DIR)/stress.js
	@echo "$(GREEN)Stress test complete$(RESET)"

load-test-soak: $(K6_RESULTS_DIR) ## Run soak test (30 VUs, 30 min)
	@echo "$(CYAN)Running soak test...$(RESET)"
	@echo "$(YELLOW)This test will run for approximately 30 minutes$(RESET)"
	$(K6) run \
		--env BASE_URL=$(BASE_URL) \
		--out json=$(K6_RESULTS_DIR)/soak-$$(date +%Y%m%d-%H%M%S).json \
		$(K6_SCRIPTS_DIR)/soak.js
	@echo "$(GREEN)Soak test complete$(RESET)"

load-test-all: load-test-smoke load-test-load ## Run smoke and load tests sequentially
	@echo "$(GREEN)All basic load tests complete$(RESET)"

load-test-docker: $(K6_RESULTS_DIR) ## Run smoke test using Docker (no k6 install required)
	@echo "$(CYAN)Running smoke test with Docker...$(RESET)"
	@echo "$(YELLOW)Note: On macOS, ensure the app is running on localhost:8080$(RESET)"
	docker run --rm -i \
		--add-host=host.docker.internal:host-gateway \
		-v $(PWD)/load-tests:/load-tests \
		-e BASE_URL=http://host.docker.internal:8080 \
		grafana/k6:latest run \
		--out json=/load-tests/results/smoke-docker-$$(date +%Y%m%d-%H%M%S).json \
		/load-tests/scripts/scenarios/smoke.js
	@echo "$(GREEN)Docker smoke test complete$(RESET)"

load-test-clean: ## Clean load test results
	rm -rf $(K6_RESULTS_DIR)/*.json
	@echo "$(GREEN)Load test results cleaned$(RESET)"
