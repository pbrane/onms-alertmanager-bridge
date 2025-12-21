# Makefile to build the application from source
.DEFAULT_GOAL       := build
VERSION             ?= $(shell mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
GIT_BRANCH          := $(shell git branch --show-current)
GIT_SHORT_HASH      := $(shell git rev-parse --short HEAD)
DATE                := $(shell date -u +"%Y-%m-%dT%H:%M:%SZ") # Date format RFC3339
REQUIRED_BINS       := java mvn
SHELL               := bash
JAVA_MAJOR_VERSION  := 21
OK                  := [ 👍 ]
MVN_BUILD_OPTS      := --batch-mode -Dstyle.color=always
MVN_TEST_OPTS       := -DskipTests=false
MVN_OPTS            := $(MVN_BUILD_OPTS) $(MVN_TEST_OPTS)
OCI_TAG             := onms-alertmanager-bridge:latest

ARTIFACTS_DIR       := ./target
RELEASE_VERSION     := UNSET.0.0
RELEASE_BRANCH      := main
MAJOR_VERSION       := $(shell echo $(RELEASE_VERSION) | cut -d. -f1)
MINOR_VERSION       := $(shell echo $(RELEASE_VERSION) | cut -d. -f2)
PATCH_VERSION       := $(shell echo $(RELEASE_VERSION) | cut -d. -f3)
SNAPSHOT_VERSION    := $(MAJOR_VERSION).$(MINOR_VERSION).$(shell expr $(PATCH_VERSION) + 1)-SNAPSHOT
RELEASE_LOG         := $(ARTIFACTS_DIR)/release.log

.PHONY: deps
deps:
	$(foreach bin,$(REQUIRED_BINS),\
		$(if $(shell command -v $(bin) 2> /dev/null),$(info 👮 $(bin) 🌈 ),$(error Please install `$(bin)`)))
	@echo -n "👮 Check Java version $(JAVA_MAJOR_VERSION):       "
	@java --version | grep '$(JAVA_MAJOR_VERSION)\.[[:digit:]]*\.[[:digit:]]*' >/dev/null
	@echo $(OK)
	@echo -n "👮 Check mvn java version $(JAVA_MAJOR_VERSION):   "
	@mvn -version | grep 'Java version: $(JAVA_MAJOR_VERSION)\.[[:digit:]]*\.[[:digit:]]*' >/dev/null
	@echo $(OK)

.PHONY: deps-oci
deps-oci:
	@echo -n "👮 Check Docker installation: "
	@command -v docker || echo "Docker binary not found"

.PHONY: build
build: deps
	mvn $(MVN_OPTS) package

.PHONY: clean
clean: deps
	mvn clean

.PHONY: oci
oci: deps-oci build
	@echo -n "👩‍🔧 Building Docker image ..."
	@docker build -t $(OCI_TAG) \
       --build-arg DATE=$(DATE) \
       --build-arg VERSION=$(VERSION) \
       --build-arg GIT_SHORT_HASH=$(GIT_SHORT_HASH) \
       .
	@echo $(OK)

.PHONY: release
release: deps
	@mkdir -p target
	@echo ""
	@echo "Release version:                $(RELEASE_VERSION)"
	@echo "New snapshot version:           $(SNAPSHOT_VERSION)"
	@echo "Git version tag:                v$(RELEASE_VERSION)"
	@echo "Current branch:                 $(GIT_BRANCH)"
	@echo "Release branch:                 $(RELEASE_BRANCH)"
	@echo "Release log file:               $(RELEASE_LOG)"
	@echo ""
	@echo -n "👮 Check release branch:        "
	@if [ "$(GIT_BRANCH)" != "$(RELEASE_BRANCH)" ]; then echo "Releases are made from the $(RELEASE_BRANCH) branch, your branch is $(GIT_BRANCH)."; exit 1; fi
	@echo "$(OK)"
	@echo -n "👮 Check uncommited changes     "
	@if git status --porcelain | grep -q .; then echo "There are uncommited changes in your repository."; exit 1; fi
	@echo "$(OK)"
	@echo -n "👮 Check branch in sync         "
	@if [ "$(git rev-parse HEAD)" != "$(git rev-parse @{u})" ]; then echo "$(RELEASE_BRANCH) branch not in sync with remote origin."; exit 1; fi
	@echo "$(OK)"
	@echo -n "👮 Check release version:       "
	@if [ "$(RELEASE_VERSION)" = "UNSET.0.0" ]; then echo "Set a release version, e.g. make release RELEASE_VERSION=1.0.0"; exit 1; fi
	@echo "$(OK)"
	@echo -n "👮 Check version tag available: "
	@if git rev-parse v$(RELEASE_VERSION) >$(RELEASE_LOG) 2>&1; then echo "Tag v$(RELEASE_VERSION) already exists"; exit 1; fi
	@echo "$(OK)"
	@echo -n "💅 Set Maven release version:   "
	@mvn versions:set -DnewVersion=$(RELEASE_VERSION) >>$(RELEASE_LOG) 2>&1
	@echo "$(OK)"
	@echo -n "👮 Validate:                    "
	@$(MAKE) build >>$(RELEASE_LOG) 2>&1
	@echo "$(OK)"
	@echo -n "🎁 Git commit new release       "
	@git commit --signoff -am "release: onms-alertmanager-bridge version $(RELEASE_VERSION)" >>$(RELEASE_LOG) 2>&1
	@echo "$(OK)"
	@echo -n "🦄 Set Git version tag:         "
	@git tag -a "v$(RELEASE_VERSION)" -m "Release OpenNMS Alertmanager Bridge version $(RELEASE_VERSION)" >>$(RELEASE_LOG) 2>&1
	@echo "$(OK)"
	@echo -n "⬆️ Set Maven snapshot version:  "
	@mvn versions:set -DnewVersion=$(SNAPSHOT_VERSION) >>$(RELEASE_LOG) 2>&1
	@echo "$(OK)"
	@echo -n "🎁 Git commit snapshot release: "
	@git commit --signoff -am "release: onms-alertmanager-bridge version $(SNAPSHOT_VERSION)" >>$(RELEASE_LOG) 2>&1
	@echo "$(OK)"
	@echo ""
	@echo "🦄 Congratulations! ✨"
	@echo "You made a release in your local repository."
	@echo "Publish the release by pushing the version tag"
	@echo "and the new snapshot version to the remote repo"
	@echo "with the following commands:"
	@echo ""
	@echo "  git push"
	@echo "  git push origin v$(RELEASE_VERSION)"
	@echo ""
	@echo "Thank you for computing with us."
	@echo ""
