SHELL := /bin/bash
.DEFAULT_GOAL := help
.NOTPARALLEL:

PROJECT_ROOT := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
ENV_FILE := $(PROJECT_ROOT)/.env
SDKMAN_INIT := $(HOME)/.sdkman/bin/sdkman-init.sh

POSTGRES_PORT ?= 5432
RABBITMQ_PORT ?= 5672
RABBITMQ_MANAGEMENT_PORT ?= 15672
REDIS_PORT ?= 6379
IAM_SERVER_PORT ?= 9000
GATEWAY_SERVER_PORT ?= 8080
IAM_ISSUER ?= http://localhost:$(IAM_SERVER_PORT)
IAM_BASE_URL ?= http://localhost:$(IAM_SERVER_PORT)

export POSTGRES_PORT
export RABBITMQ_PORT
export RABBITMQ_MANAGEMENT_PORT
export REDIS_PORT
export IAM_SERVER_PORT
export GATEWAY_SERVER_PORT
export IAM_ISSUER
export IAM_BASE_URL

.PHONY: help doctor init format check test infra-up infra-down down infra-logs status run up

help: ## Show available commands
	@awk 'BEGIN {FS = ":.*## "; printf "CloudForge development commands:\n\n"} /^[a-zA-Z0-9_-]+:.*## / {printf "  %-12s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

doctor: ## Check the local development prerequisites
	@command -v docker >/dev/null || { echo "Docker is required." >&2; exit 1; }
	@docker compose version >/dev/null || { echo "Docker Compose v2 is required." >&2; exit 1; }
	@test -x "$(PROJECT_ROOT)/gradlew" || { echo "The Gradle Wrapper is not executable." >&2; exit 1; }
	@if [[ -s "$(SDKMAN_INIT)" ]]; then \
		echo "SDKMAN is available."; \
	else \
		echo "SDKMAN is not installed; Gradle will provision a compatible Java toolchain."; \
	fi

init: doctor ## Create local config, install the SDKMAN JDK, and initialize Gradle
	@if [[ ! -f "$(ENV_FILE)" ]]; then \
		cp "$(PROJECT_ROOT)/.env.example" "$(ENV_FILE)"; \
		echo "Created .env from .env.example."; \
	else \
		echo "Keeping existing .env."; \
	fi
	@if [[ -s "$(SDKMAN_INIT)" ]]; then \
		source "$(SDKMAN_INIT)"; \
		sdk env install; \
		sdk env; \
	fi; \
	"$(PROJECT_ROOT)/gradlew" help --no-daemon --quiet
	@echo "CloudForge development environment is ready."

format: ## Apply repository and Java source formatting
	@if [[ -s "$(SDKMAN_INIT)" ]]; then source "$(SDKMAN_INIT)" && sdk env >/dev/null; fi; \
	"$(PROJECT_ROOT)/gradlew" formatAll --no-daemon

check: ## Run formatting, static analysis, architecture checks, and tests
	@if [[ -s "$(SDKMAN_INIT)" ]]; then source "$(SDKMAN_INIT)" && sdk env >/dev/null; fi; \
	"$(PROJECT_ROOT)/gradlew" qualityCheck --no-daemon

test: ## Run all module tests
	@if [[ -s "$(SDKMAN_INIT)" ]]; then source "$(SDKMAN_INIT)" && sdk env >/dev/null; fi; \
	"$(PROJECT_ROOT)/gradlew" test --no-daemon

infra-up: init ## Start PostgreSQL, RabbitMQ, and Redis and wait until healthy
	@docker compose --project-directory "$(PROJECT_ROOT)" up -d --wait

infra-down: ## Stop local data services without deleting their volumes
	@docker compose --project-directory "$(PROJECT_ROOT)" down

down: infra-down ## Alias for infra-down

infra-logs: ## Follow logs from the local data services
	@docker compose --project-directory "$(PROJECT_ROOT)" logs --follow

status: ## Show local data service status
	@docker compose --project-directory "$(PROJECT_ROOT)" ps

run: init ## Run IAM and Gateway together in the foreground
	@set -a; \
	source "$(ENV_FILE)"; \
	set +a; \
	if [[ -s "$(SDKMAN_INIT)" ]]; then source "$(SDKMAN_INIT)" && sdk env >/dev/null; fi; \
	export DB_URL="$${DB_URL:-jdbc:postgresql://localhost:$(POSTGRES_PORT)/iam}"; \
	exec "$(PROJECT_ROOT)/gradlew" \
		:services:iam:bootRun \
		:services:gateway:bootRun \
		--parallel \
		--no-daemon

up: infra-up run ## Initialize everything and run the full local stack
