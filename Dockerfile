# Runtime stage
FROM eclipse-temurin:21-jre-alpine

ARG VERSION

# Create non-root user for security
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -G appgroup -s /sbin/nologin -S appuser

ADD target/onms-alertmanager-bridge-${VERSION}.jar /app/onms-alertmanager-bridge.jar

WORKDIR /app

# Set ownership
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# JVM options for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

# Expose port
EXPOSE 8080

# Entry point
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar onms-alertmanager-bridge.jar"]

ARG DATE
ARG GIT_SHORT_HASH

LABEL org.opencontainers.image.created="${DATE}" \
      org.opencontainers.image.authors="info@beaconstrategists.com" \
      org.opencontainers.image.url="TBD" \
      org.opencontainers.image.source="ttps://github.com/pbrane/onms-alertmanager-bridge" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${GIT_SHORT_HASH}" \
      org.opencontainers.image.vendor="pbrane" \
      org.opencontainers.image.licenses="Apache-2.0"
