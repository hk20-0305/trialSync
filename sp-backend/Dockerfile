# syntax=docker/dockerfile:1
#
# TrialSync backend - production image (Spring Boot 3.3.5 on Java 21)
# ===================================================================
#
# The build context is this sp-backend directory, so the frontend tree and its
# node_modules never reach the daemon:
#
#   docker build --tag trialsync-backend:local sp-backend
#
# Configuration arrives through the runtime environment only. application.yml
# reads ${DATABASE_URL}, ${DATABASE_USERNAME}, ${DATABASE_PASSWORD},
# ${DATABASE_POOL_SIZE}, ${PORT} and the TRIALSYNC_* variables; no credential is
# baked into a layer, and no .env file is copied in, so TrialSyncApplication's
# loadDotenv() finds nothing on disk and the environment is authoritative.

# ---------------------------------------------------------------------------
# Stage 1 - resolve dependencies and package the executable jar
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# The POM is copied on its own so the dependency layer survives source edits.
# The BuildKit cache mount keeps ~/.m2 between builds without leaving it in the
# resulting layer.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY src ./src

# Tests are the verification gate that runs outside the image; this stage only
# produces the artefact. The version is read from target/ rather than hardcoded
# here, and *.jar.original (the pre-repackage jar the Spring Boot plugin leaves
# behind) is skipped.
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress --define skipTests=true package \
 && jar_file="$(find target -maxdepth 1 -type f -name '*.jar' ! -name '*.jar.original' | head -n 1)" \
 && test -n "$jar_file" \
 && cp "$jar_file" /workspace/app.jar

# ---------------------------------------------------------------------------
# Stage 2 - Java 21 JRE runtime
# ---------------------------------------------------------------------------
# Debian (glibc) rather than Alpine on purpose: the LangChain4j embedding model
# (all-MiniLM-L6-v2) loads a native ONNX Runtime library, and the JVM default
# is glibc.
FROM eclipse-temurin:21-jre-jammy AS runtime

# The import pipeline shells out to Poppler's pdftoppm and to tesseract for its
# OCR fallback (see imports/TesseractOcrRunner), so both ship with the image -
# the same two packages CI installs for the test suite. curl backs the health
# check below. Drop poppler-utils and tesseract-ocr if that fallback is not
# wanted; the list is cleaned in the same layer either way.
RUN apt-get update \
 && apt-get install --no-install-recommends -y \
        curl \
        poppler-utils \
        tesseract-ocr \
        tesseract-ocr-eng \
 && rm -rf /var/lib/apt/lists/*

# Non-root runtime user. HOME points at its own writable directory because the
# DJL/ONNX runtime and the JVM keep caches under $HOME; Java's temporary
# directory stays at /tmp, which the OCR scratch files use.
RUN groupadd --gid 1001 trialsync \
 && useradd --uid 1001 --gid trialsync --create-home --home-dir /home/trialsync \
        --shell /usr/sbin/nologin trialsync

WORKDIR /app

COPY --from=build --chown=trialsync:trialsync /workspace/app.jar /app/app.jar

USER trialsync

LABEL org.opencontainers.image.title="trialsync-backend" \
      org.opencontainers.image.description="TrialSync backend API (Spring Boot 3.3.5 on Java 21)" \
      org.opencontainers.image.source="https://github.com/hk20-0305/TrialSync.git"

# PORT defaults to 8000, matching server.port in application.yml. The pool size
# has no default there (${DATABASE_POOL_SIZE} without a fallback), so a modest
# value is supplied here to keep the image runnable on its own; a deployment
# overrides both with -e / the compose environment.
ENV HOME=/home/trialsync \
    PORT=8000 \
    DATABASE_POOL_SIZE=10 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8000

# /health/live is the process liveness probe: it answers as soon as the HTTP
# listener is up and needs no database. /health/ready is deliberately not used
# here - it also requires an exact Flyway head version, which makes it a
# deployment gate (docker compose --wait, ingress checks) rather than a
# container liveness signal. The 120s start period covers a cold start: Flyway
# on an empty database plus the local all-MiniLM embedding model; once the
# period elapses, three further failed probes (90s) are needed before Docker
# reports the container unhealthy.
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
    CMD curl --fail --silent --show-error --max-time 4 "http://127.0.0.1:${PORT}/health/live" >/dev/null || exit 1

# No default arguments: the Temurin base declares none either, so nothing is
# inherited into the entrypoint's argument list below.
CMD []

# JAVA_OPTS stays overridable and extra arguments (`docker run <image>
# --server.port=9000`) are forwarded to the application; `exec` keeps the JVM as
# PID 1 so SIGTERM reaches Spring Boot's shutdown hooks. This replaces the base
# image's /__cacert_entrypoint.sh wrapper, which is a no-op unless
# USE_SYSTEM_CA_CERTS is set - do not set it without re-chaining that wrapper.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar \"$@\"", "trialsync-backend"]
