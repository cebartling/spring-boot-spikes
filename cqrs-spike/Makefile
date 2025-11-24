# Makefile
# CQRS Spike Infrastructure Management

.PHONY: help start stop restart clean logs health ps build test shell-vault shell-postgres

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
