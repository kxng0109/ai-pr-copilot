# ============================================
# Build Stage (pinned Temurin 25 LTS)
# ============================================
FROM eclipse-temurin:25.0.4_1-jdk-alpine AS builder

WORKDIR /build

# Copy Maven wrapper and pom.xml first (layer caching)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN ./mvnw clean package -DskipTests -B

# Train the AOT cache (JEP 483, Java 25+): faster startup + lower RSS on the fat jar.
# Set AOTCACHE_ENABLED=0 at build to skip. Same OS/arch/classpath required at runtime.
ARG AOTCACHE_ENABLED=1
RUN if [ "$AOTCACHE_ENABLED" = "1" ]; then \
      java -XX:AOTCacheOutput=app.aot -Dspring.context.exit=onRefresh -jar target/ai-pr-copilot-*.jar \
        || { echo "AOT cache training skipped (non-fatal)"; touch app.aot; }; \
    else touch app.aot; fi

# Extract Spring Boot layers (Boot 4.1: jarmode=tools replaces removed layertools)
RUN java -Djarmode=tools -jar target/ai-pr-copilot-*.jar extract --layers --destination extracted

# ============================================
# Runtime Stage
# ============================================
FROM eclipse-temurin:25.0.4_1-jre-alpine

LABEL org.opencontainers.image.title="AI PR Copilot"
LABEL org.opencontainers.image.description="Self-hosted AI-powered code audit and PR analysis service"
LABEL org.opencontainers.image.vendor="kxng0109"
ARG APP_VERSION=1.0.0-rc.4
LABEL org.opencontainers.image.version="${APP_VERSION}"
LABEL org.opencontainers.image.source="https://github.com/kxng0109/ai-pr-copilot"

WORKDIR /app

# Install required packages for health checks
RUN apk add --no-cache wget

# Create non-root user for security
RUN addgroup -S appgroup && \
    adduser -S appuser -G appgroup && \
    chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Copy Spring Boot layers (in order of change frequency)
COPY --from=builder --chown=appuser:appgroup /build/extracted/dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=appuser:appgroup /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /build/extracted/application/ ./
# AOT cache (may be absent when AOTCACHE_ENABLED=0; entrypoint probes for it)
COPY --from=builder --chown=appuser:appgroup /build/app.aot ./app.aot

# Expose application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# JVM configuration for containerized environments
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:+TieredCompilation \
               -XX:TieredStopAtLevel=1 \
               -Djava.security.egd=file:/dev/./urandom"
ENV JAVA_MAX_RAM_PERCENTAGE=75.0

# Run the application (uses the AOT cache when training produced one)
ENTRYPOINT ["sh", "-c", "if [ -s /app/app.aot ]; then AOT_OPT=\"-XX:AOTCache=/app/app.aot\"; else AOT_OPT=\"\"; fi; java $AOT_OPT -XX:MaxRAMPercentage=${JAVA_MAX_RAM_PERCENTAGE:-75.0} $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
