# Open Media Converter - Makefile
# Simple build automation for Docker-based development
#
# Maven Dependency Caching:
#   Maven dependencies are cached in $(HOME)/.m2 and mounted into the container.
#   This prevents re-downloading dependencies on every build.
#   The cache is shared with your host system's Maven installation.

# Configuration
IMAGE_NAME := open-media-converter
DEV_IMAGE := $(IMAGE_NAME):dev
RUNTIME_IMAGE := $(IMAGE_NAME):runtime
ARCH_IMAGE := $(IMAGE_NAME):arch-builder
PROJECT_DIR := $(shell pwd)
MAVEN_CACHE_DIR := $(HOME)/.m2

# Get current user's UID and GID for Docker
USER_ID := $(shell id -u)
GROUP_ID := $(shell id -g)

# Docker build args to match host user permissions
DOCKER_BUILD_ARGS := --build-arg USER_ID=$(USER_ID) --build-arg GROUP_ID=$(GROUP_ID)

# Docker run options (run as current user to avoid permission issues)
# Mount Maven cache directory to persist dependencies between runs
DOCKER_RUN := docker run --rm -v $(PROJECT_DIR):/workspace -v $(MAVEN_CACHE_DIR):/home/builder/.m2 -w /workspace --user $(USER_ID):$(GROUP_ID)
DOCKER_RUN_IT := docker run --rm -it -v $(PROJECT_DIR):/workspace -v $(MAVEN_CACHE_DIR):/home/builder/.m2 -w /workspace --user $(USER_ID):$(GROUP_ID)

# Maven options
MVN := mvn
MVN_OPTS := -DskipTests
MVN_TEST_OPTS := 

.PHONY: help
help: ## Show this help message
	@echo "Open Media Converter - Build Targets"
	@echo ""
	@echo "Usage: make [target]"
	@echo ""
	@echo "Targets:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  %-20s %s\n", $$1, $$2}'

.PHONY: build-image
build-image: ensure-maven-cache ## Build Docker development image
	@echo "Building Docker development image with USER_ID=$(USER_ID) GROUP_ID=$(GROUP_ID)..."
	docker build $(DOCKER_BUILD_ARGS) --target dev -t $(DEV_IMAGE) .

.PHONY: ensure-maven-cache
ensure-maven-cache: ## Ensure Maven cache directory exists
	@mkdir -p $(MAVEN_CACHE_DIR)

.PHONY: build-runtime-image
build-runtime-image: ## Build Docker runtime image
	@echo "Building Docker runtime image..."
	docker build $(DOCKER_BUILD_ARGS) --target runtime -t $(RUNTIME_IMAGE) .

.PHONY: build-arch-image
build-arch-image: ensure-maven-cache ## Build Docker Arch Linux image for package building
	@echo "Building Docker Arch Linux image with USER_ID=$(USER_ID) GROUP_ID=$(GROUP_ID)..."
	docker build $(DOCKER_BUILD_ARGS) --target arch-builder -t $(ARCH_IMAGE) .

.PHONY: compile
compile: build-image ## Compile Java code in Docker
	@echo "Compiling project..."
	$(DOCKER_RUN) $(DEV_IMAGE) mvn clean compile -DskipTests

.PHONY: test
test: build-image ## Run tests in Docker
	@echo "Running tests..."
	$(DOCKER_RUN) $(DEV_IMAGE) mvn test

.PHONY: package
package: build-image ## Build JAR package in Docker
	@echo "Building JAR package..."
	$(DOCKER_RUN) $(DEV_IMAGE) mvn clean package -DskipTests

.PHONY: package-all
package-all: build-image ## Build all distribution packages (DEB, RPM, etc.) in Docker
	@echo "Building all packages..."
	$(DOCKER_RUN) $(DEV_IMAGE) bash -c "chmod +x omc-gtk/scripts/*.sh && omc-gtk/scripts/build-packages.sh --all"

.PHONY: package-deb
package-deb: build-image ## Build DEB package only in Docker
	@echo "Building DEB package..."
	$(DOCKER_RUN) $(DEV_IMAGE) bash -c "chmod +x omc-gtk/scripts/*.sh && omc-gtk/scripts/build-packages.sh --deb"

.PHONY: package-rpm
package-rpm: build-image ## Build RPM package only in Docker
	@echo "Building RPM package..."
	$(DOCKER_RUN) $(DEV_IMAGE) bash -c "chmod +x omc-gtk/scripts/*.sh && omc-gtk/scripts/build-packages.sh --rpm"

.PHONY: package-arch
package-arch: build-arch-image ## Build Arch package only in Docker (Arch Linux container)
	@echo "Building Arch package..."
	$(DOCKER_RUN) $(ARCH_IMAGE) bash -c "chmod +x omc-gtk/scripts/*.sh && omc-gtk/scripts/build-packages.sh --arch"

.PHONY: dev
dev: build-image ## Start interactive development shell in Docker
	@echo "Starting development shell..."
	$(DOCKER_RUN_IT) $(DEV_IMAGE) /bin/bash

.PHONY: run
run: package build-runtime-image ## Build and run the application in Docker (full build)
	@echo "Running application..."
	@echo "Setting up X11/Wayland access for Docker..."
	@if command -v xhost >/dev/null 2>&1; then \
		xhost +local:docker >/dev/null 2>&1 || true; \
	fi
	@if [ -n "$$WAYLAND_DISPLAY" ] && [ -S "$$XDG_RUNTIME_DIR/$$WAYLAND_DISPLAY" ]; then \
		echo "Using Wayland display"; \
		docker run --rm \
			-e DISPLAY=$$DISPLAY \
			-e WAYLAND_DISPLAY=$$WAYLAND_DISPLAY \
			-e XDG_RUNTIME_DIR=$$XDG_RUNTIME_DIR \
			-v /tmp/.X11-unix:/tmp/.X11-unix:rw \
			-v $$XDG_RUNTIME_DIR/$$WAYLAND_DISPLAY:$$XDG_RUNTIME_DIR/$$WAYLAND_DISPLAY:rw \
			--network=host \
			--ipc=host \
			$(RUNTIME_IMAGE); \
	else \
		echo "Using X11 display"; \
		docker run --rm \
			-e DISPLAY=$$DISPLAY \
			-v /tmp/.X11-unix:/tmp/.X11-unix:rw \
			--network=host \
			--ipc=host \
			$(RUNTIME_IMAGE); \
	fi
	@echo "Cleaning up X11/Wayland access..."
	@if command -v xhost >/dev/null 2>&1; then \
		xhost -local:docker > /dev/null 2>&1 || true; \
	fi

.PHONY: run-jar
run-jar: ## Run the application from existing JAR (no rebuild)
	@echo "Running application from existing JAR..."
	@if [ ! -f "omc-gtk/target/open-media-converter-"*".jar" ]; then \
		echo "Error: JAR file not found. Run 'make package' first."; \
		exit 1; \
	fi
	@echo "Setting up X11/Wayland access for Docker..."
	@if command -v xhost >/dev/null 2>&1; then \
		xhost +local:docker >/dev/null 2>&1 || true; \
	fi
	@if [ -n "$$WAYLAND_DISPLAY" ] && [ -S "$$XDG_RUNTIME_DIR/$$WAYLAND_DISPLAY" ]; then \
		echo "Using Wayland display"; \
		docker run --rm \
			-e DISPLAY=$$DISPLAY \
			-e WAYLAND_DISPLAY=$$WAYLAND_DISPLAY \
			-e XDG_RUNTIME_DIR=$$XDG_RUNTIME_DIR \
			-v /tmp/.X11-unix:/tmp/.X11-unix:rw \
			-v $$XDG_RUNTIME_DIR/$$WAYLAND_DISPLAY:$$XDG_RUNTIME_DIR/$$WAYLAND_DISPLAY:rw \
			-v $(PROJECT_DIR):/workspace:ro \
			-w /workspace \
			--network=host \
			--ipc=host \
			$(DEV_IMAGE) \
			java --enable-native-access=ALL-UNNAMED -jar omc-gtk/target/open-media-converter-*.jar; \
	else \
		echo "Using X11 display"; \
		docker run --rm \
			-e DISPLAY=$$DISPLAY \
			-v /tmp/.X11-unix:/tmp/.X11-unix:rw \
			-v $(PROJECT_DIR):/workspace:ro \
			-w /workspace \
			--network=host \
			--ipc=host \
			$(DEV_IMAGE) \
			java --enable-native-access=ALL-UNNAMED -jar omc-gtk/target/open-media-converter-*.jar; \
	fi
	@echo "Cleaning up X11/Wayland access..."
	@if command -v xhost >/dev/null 2>&1; then \
		xhost -local:docker > /dev/null 2>&1 || true; \
	fi

.PHONY: clean
clean: ## Clean build artifacts
	@echo "Cleaning build artifacts..."
	rm -rf target */target
	rm -rf build dist

.PHONY: clean-maven-cache
clean-maven-cache: ## Clean Maven cache (forces re-download of dependencies)
	@echo "Cleaning Maven cache..."
	@echo "WARNING: This will delete $(MAVEN_CACHE_DIR) and force Maven to re-download all dependencies"
	@read -p "Are you sure? [y/N] " -n 1 -r; \
	echo; \
	if [[ $$REPLY =~ ^[Yy]$$ ]]; then \
		rm -rf $(MAVEN_CACHE_DIR); \
		echo "Maven cache cleaned"; \
	else \
		echo "Cancelled"; \
	fi

.PHONY: clean-docker
clean-docker: ## Remove Docker images
	@echo "Removing Docker images..."
	docker rmi $(DEV_IMAGE) $(RUNTIME_IMAGE) $(ARCH_IMAGE) 2>/dev/null || true

.PHONY: clean-all
clean-all: clean clean-docker ## Clean everything including Docker images
	@echo "All cleaned!"

.PHONY: verify
verify: build-image ## Run full verification (compile, test, package)
	@echo "Running full verification..."
	$(DOCKER_RUN) $(DEV_IMAGE) mvn clean verify

# Local development targets (without Docker)
.PHONY: local-compile
local-compile: ## Compile locally (requires Java 23 and Maven)
	$(MVN) clean compile -DskipTests

.PHONY: local-test
local-test: ## Run tests locally
	$(MVN) test

.PHONY: local-package
local-package: ## Build JAR locally
	$(MVN) clean package -DskipTests

.PHONY: local-package-all
local-package-all: ## Build all packages locally
	bash omc-gtk/scripts/build-packages.sh --all

.PHONY: local-verify
local-verify: ## Run full verification locally
	$(MVN) clean verify

# Quick targets for common workflows
.PHONY: quick-test
quick-test: ## Quick test without rebuilding Docker image
	$(DOCKER_RUN) $(DEV_IMAGE) mvn test -Dtest="*Test"

.PHONY: quick-package
quick-package: ## Quick package without cleaning
	$(DOCKER_RUN) $(DEV_IMAGE) mvn package -DskipTests

# CI/CD helper targets
.PHONY: ci-build
ci-build: ## CI build: compile and test
	$(MVN) clean test

.PHONY: ci-package
ci-package: ## CI package: build all distribution packages
	$(MVN) clean package -DskipTests
	chmod +x omc-gtk/scripts/*.sh
	bash omc-gtk/scripts/build-packages.sh --all

.DEFAULT_GOAL := help
