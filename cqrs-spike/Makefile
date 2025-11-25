# Makefile
# CQRS Spike Infrastructure Management

.PHONY: help start stop restart clean logs logs-errors logs-dashboard health ps build test shell-vault shell-postgres \
        monitor resources perf test-docs db-query vault-get reset-vault reset-db init-env \
        db-seed db-seed-standard db-seed-full db-seed-perf db-reset db-reset-data db-scenario

help: ## Show this help message
	@echo "Available commands:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}'

start: ## Start infrastructure services
	@./scripts/start-infrastructure.sh

stop: ## Stop all services
	@./scripts/stop-infrastructure.sh

restart: stop start ## Restart all services

clean: ## Stop services and remove volumes
	@./scripts/stop-infrastructure.sh --clean

logs: ## View all logs
	@if docker compose version > /dev/null 2>&1; then \
		docker compose logs -f; \
	else \
		docker-compose logs -f; \
	fi

logs-vault: ## View Vault logs
	@if docker compose version > /dev/null 2>&1; then \
		docker compose logs -f vault; \
	else \
		docker-compose logs -f vault; \
	fi

logs-postgres: ## View PostgreSQL logs
	@if docker compose version > /dev/null 2>&1; then \
		docker compose logs -f postgres; \
	else \
		docker-compose logs -f postgres; \
	fi

health: ## Check service health
	@./scripts/health-check.sh

ps: ## Show service status
	@if docker compose version > /dev/null 2>&1; then \
		docker compose ps; \
	else \
		docker-compose ps; \
	fi

build: ## Build application
	@./gradlew clean build -x test

test: ## Run tests
	@./gradlew test

shell-vault: ## Open shell in Vault container
	@docker exec -it cqrs-vault sh

shell-postgres: ## Open psql in PostgreSQL container
	@docker exec -it cqrs-postgres psql -U cqrs_user -d cqrs_db

init-env: ## Create .env from .env.example
	@if [ ! -f .env ]; then \
		cp .env.example .env; \
		echo ".env file created from .env.example"; \
	else \
		echo ".env file already exists"; \
	fi

logs-errors: ## View error logs only
	@./scripts/logs.sh errors

logs-dashboard: ## Open multi-pane log dashboard (requires tmux)
	@./scripts/logs-dashboard.sh

monitor: ## Monitor resource consumption
	@./scripts/monitor-resources.sh

resources: monitor ## Alias for monitor

perf: ## Run performance tests
	@./scripts/test-performance.sh

test-docs: ## Test documentation accuracy
	@./scripts/test-documentation.sh

db-query: ## Run a database query (usage: make db-query QUERY="SELECT 1")
	@./scripts/db-query.sh "$(QUERY)"

vault-get: ## Get a Vault secret (usage: make vault-get PATH="secret/cqrs-spike/database")
	@./scripts/vault-get.sh "$(PATH)"

reset-vault: ## Reset Vault service
	@./scripts/reset-service.sh vault

reset-db: ## Reset PostgreSQL database (WARNING: deletes all data)
	@./scripts/reset-service.sh postgres

measure-startup: ## Measure infrastructure startup time
	@./scripts/measure-startup.sh

# Database Seeding Targets
db-seed: ## Seed database with minimal scenario
	@./infrastructure/postgres/seed-data/scripts/seed.sh minimal

db-seed-standard: ## Seed database with standard scenario
	@./infrastructure/postgres/seed-data/scripts/seed.sh standard

db-seed-full: ## Seed database with full scenario
	@./infrastructure/postgres/seed-data/scripts/seed.sh full

db-seed-perf: ## Seed database with performance test data
	@./infrastructure/postgres/seed-data/scripts/seed.sh performance

db-reset: ## Reset database (WARNING: Deletes all data!)
	@./infrastructure/postgres/seed-data/scripts/reset.sh

db-reset-data: ## Reset data only (preserves schema)
	@./infrastructure/postgres/seed-data/scripts/reset-data-only.sh

db-scenario: ## Load a specific scenario (interactive)
	@./infrastructure/postgres/seed-data/scripts/load-scenario.sh
