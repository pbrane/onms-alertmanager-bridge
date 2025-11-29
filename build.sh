#!/usr/bin/env bash
#
# Build script for OpenNMS Alertmanager Bridge
#
set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_NAME="opennms-alertmanager-bridge"
readonly DEFAULT_IMAGE_NAME="opennms-alertmanager-bridge"
readonly DEFAULT_IMAGE_TAG="latest"

readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly NC='\033[0m'

SKIP_TESTS=true
CLEAN=false
BUILD_DOCKER=false
DOCKER_IMAGE_NAME="${DEFAULT_IMAGE_NAME}"
DOCKER_IMAGE_TAG="${DEFAULT_IMAGE_TAG}"
VERBOSE=false

usage() {
    cat << EOF
Usage: $(basename "$0") [OPTIONS]

Build script for ${PROJECT_NAME}

Options:
    -c, --clean         Clean before building
    -t, --tests         Run tests (disabled by default)
    -d, --docker        Build Docker image after Maven build
    -i, --image NAME    Docker image name (default: ${DEFAULT_IMAGE_NAME})
    -g, --tag TAG       Docker image tag (default: ${DEFAULT_IMAGE_TAG})
    -v, --verbose       Verbose Maven output
    -h, --help          Show this help message

Examples:
    $(basename "$0")                    # Quick build, skip tests
    $(basename "$0") -c                 # Clean build, skip tests
    $(basename "$0") -c -t              # Clean build with tests
    $(basename "$0") -c -d              # Clean build and create Docker image
    $(basename "$0") -d -i myrepo/app -g 1.0.0   # Build with custom Docker tag

EOF
    exit 0
}

log_info() {
    echo -e "${GREEN}[INFO]${NC} $*"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $*"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $*" >&2
}

check_prerequisites() {
    log_info "Checking prerequisites..."
    
    if [[ ! -f "${SCRIPT_DIR}/mvnw" ]]; then
        log_error "Maven wrapper (mvnw) not found. Run from project root."
        exit 1
    fi
    
    if [[ ! -f "${SCRIPT_DIR}/pom.xml" ]]; then
        log_error "pom.xml not found. Run from project root."
        exit 1
    fi
    
    if [[ "${BUILD_DOCKER}" == true ]]; then
        if ! command -v docker &> /dev/null; then
            log_error "Docker is required for --docker option but not installed."
            exit 1
        fi
    fi
}

run_maven() {
    local goals=("$@")
    local mvn_opts=()
    
    if [[ "${SKIP_TESTS}" == true ]]; then
        mvn_opts+=("-DskipTests")
    fi
    
    if [[ "${VERBOSE}" == false ]]; then
        mvn_opts+=("-q")
    fi
    
    log_info "Running: ./mvnw ${mvn_opts[*]} ${goals[*]}"
    "${SCRIPT_DIR}/mvnw" "${mvn_opts[@]}" "${goals[@]}"
}

build_maven() {
    local start_time
    start_time=$(date +%s)
    
    if [[ "${CLEAN}" == true ]]; then
        log_info "Cleaning project..."
        run_maven clean
    fi
    
    log_info "Building project..."
    run_maven package
    
    local end_time
    end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    log_info "Maven build completed in ${duration}s"
    
    local jar_file
    jar_file=$(find "${SCRIPT_DIR}/target" -maxdepth 1 -name "*.jar" -not -name "*-sources.jar" -not -name "*-javadoc.jar" 2>/dev/null | head -1)
    if [[ -n "${jar_file}" ]]; then
        log_info "Built artifact: ${jar_file}"
        log_info "Artifact size: $(du -h "${jar_file}" | cut -f1)"
    fi
}

build_docker() {
    local full_tag="${DOCKER_IMAGE_NAME}:${DOCKER_IMAGE_TAG}"
    
    log_info "Building Docker image: ${full_tag}"
    
    if [[ ! -f "${SCRIPT_DIR}/Dockerfile" ]]; then
        log_error "Dockerfile not found."
        exit 1
    fi
    
    docker build -t "${full_tag}" "${SCRIPT_DIR}"
    
    log_info "Docker image built: ${full_tag}"
    docker images "${DOCKER_IMAGE_NAME}" --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}"
}

while [[ $# -gt 0 ]]; do
    case $1 in
        -c|--clean)
            CLEAN=true
            shift
            ;;
        -t|--tests)
            SKIP_TESTS=false
            shift
            ;;
        -d|--docker)
            BUILD_DOCKER=true
            shift
            ;;
        -i|--image)
            DOCKER_IMAGE_NAME="$2"
            shift 2
            ;;
        -g|--tag)
            DOCKER_IMAGE_TAG="$2"
            shift 2
            ;;
        -v|--verbose)
            VERBOSE=true
            shift
            ;;
        -h|--help)
            usage
            ;;
        *)
            log_error "Unknown option: $1"
            usage
            ;;
    esac
done

main() {
    cd "${SCRIPT_DIR}"
    
    log_info "Building ${PROJECT_NAME}"
    log_info "Options: clean=${CLEAN}, tests=${SKIP_TESTS/true/skip}, docker=${BUILD_DOCKER}"
    
    check_prerequisites
    build_maven
    
    if [[ "${BUILD_DOCKER}" == true ]]; then
        build_docker
    fi
    
    log_info "Build completed successfully!"
}

main
