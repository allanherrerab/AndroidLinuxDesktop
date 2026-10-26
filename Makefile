# GNU Make entry points. Dependency/source caches survive `make clean`.
SHELL := /bin/bash
.SHELLFLAGS := -eu -o pipefail -c
.ONESHELL:
.DEFAULT_GOAL := app

ROOTFS_FLAGS ?=
APP_FLAGS ?=
TOOLS_FLAGS ?=
BUILD_TYPE ?= debug

ifneq ($(strip $(DEBUG)),)
ifneq ($(DEBUG),1)
$(error DEBUG must be 1 when supplied; use BUILD_TYPE=release for a release build)
endif
ifneq ($(strip $(RELEASE)),)
$(error DEBUG and RELEASE are mutually exclusive)
endif
override BUILD_TYPE := debug
endif
ifneq ($(strip $(RELEASE)),)
ifneq ($(RELEASE),1)
$(error RELEASE must be 1 when supplied; use BUILD_TYPE=debug for a debug build)
endif
override BUILD_TYPE := release
endif
ifeq ($(filter $(BUILD_TYPE),debug release),)
$(error BUILD_TYPE must be debug or release)
endif
ifneq ($(words $(BUILD_TYPE)),1)
$(error Choose one BUILD_TYPE: debug or release)
endif

BUILD_OUTPUTS := build/app build/native build/generated build/host-tests \
	build/reports build/smoke build/audio-smoke
DIST_OUTPUTS := dist/debian-noroot-debug.apk dist/debian-noroot-debug.apk.installing \
	dist/debian-noroot-release-unsigned.apk dist/debian-noroot-release-unsigned.apk.installing \
	dist/debian-rootfs.tar.xz dist/debian-rootfs.tar.xz.installing \
	dist/main.2.org.debiannoroot.obb \
	dist/main.2.org.debiannoroot.obb.installing dist/rootfs.manifest.json \
	dist/runtime dist/runtime.manifest.json dist/x11.manifest.json

.PHONY: app debug release rootfs tools install-tools clean test help

app: ## Build and verify the APK and its paired expansion image.
	. tools/env.sh
	./tools/build-apk.sh --$(BUILD_TYPE) $(APP_FLAGS)

debug: ## Build the signed development APK.
	$(MAKE) app BUILD_TYPE=debug DEBUG= RELEASE=

release: ## Build the unsigned release APK.
	$(MAKE) app BUILD_TYPE=release DEBUG= RELEASE=

rootfs: ## Build the Debian desktop system tarball and image metadata.
	. tools/env.sh
	./tools/build-rootfs.sh --desktop $(ROOTFS_FLAGS)

test: ## Run the host regression checks.
	. tools/env.sh
	./tools/test-host.sh

tools install-tools: ## Install the pinned project-local build toolchain.
	python3 tools/bootstrap/install-project-tools.py $(TOOLS_FLAGS)


clean: ## Remove generated build outputs and deliverables; retain dependency caches.
	rm -rf -- $(BUILD_OUTPUTS) $(DIST_OUTPUTS)

help: ## Show targets and rebuild options.
	@printf '%s\n' \
	  'make app     Build/lint/verify the APK and matching OBB (default).' \
	  'make debug / make app DEBUG=1 / make app BUILD_TYPE=debug: signed debug APK.' \
	  'make release / make app RELEASE=1 / make app BUILD_TYPE=release: unsigned release APK.' \
	  'make rootfs  Build the Debian desktop tarball and image metadata.' \
	  'make test    Run host regression checks.' \
	  'make tools   Download/install the project-local JDK, SDK, NDK, CMake, Gradle and host libraries.' \
	  'make install-tools  Alias for make tools.' \
	  'make clean   Remove generated outputs; retain tools, source/image caches and local evidence.' \
	  'ROOTFS_FLAGS="--reuse-rootfs" refreshes packages in the cached rootfs.' \
	  'ROOTFS_FLAGS="--reuse-rootfs --overlay-only" refreshes guest assets only.' \
	  'APP_FLAGS="--rebuild-rootfs" rebuilds the image with the APK.'
