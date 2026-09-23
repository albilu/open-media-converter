# Simple Makefile for Open Media Converter Docker Development

.PHONY: help build dev test compile run debug package clean

.DEFAULT_GOAL := help

help: ## Show this help message
	@echo "Open Media Converter - Simple Docker Development"
	@echo ""
	@echo "Available targets:"
	@echo "  build     Build Docker image"
	@echo "  dev       Start development container"
	@echo "  test      Run the test suite under Xvfb"
	@echo "  compile   Build application"
	@echo "  run       Run application in Docker with GUI support"
	@echo "  debug     Run application in debug mode (port 5005)"
	@echo "  package   Create distribution packages"
	@echo "  clean     Clean up Docker resources"
	@echo ""
	@echo "Examples:"
	@echo "  make build    # Build the Docker image"
	@echo "  make dev      # Start development environment"
	@echo "  make test     # Run the test suite"
	@echo "  make run      # Run application with GUI support"
	@echo "  make debug    # Run application in debug mode"
	@echo "  make package  # Create .deb, .rpm, .pkg.tar.zst and .AppImage packages"

build: ## Build Docker image
	./docker-build.sh build

dev: ## Start development container
	./docker-build.sh dev

test: ## Run the test suite under Xvfb
	./docker-build.sh test

compile: ## Build application
	./docker-build.sh compile

run: ## Run application with GUI support
	./docker-build.sh run

debug: ## Run application in debug mode (port 5005)
	./docker-build.sh debug

package: ## Create distribution packages
	./docker-build.sh package

clean: ## Clean up Docker resources
	./docker-build.sh clean
