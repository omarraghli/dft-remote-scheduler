# ── Build ────────────────────────────────────────────────────────────────────
# A JDK image, used only to produce the jar. Nothing from this stage ships.
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /build

# Dependencies first, in their own layer: these change far less often than the source,
# so an edit to a .java file does not re-download the world.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY src src

# Tests are skipped here on purpose. They run against a real PostgreSQL through
# Testcontainers, which would need a Docker daemon inside this build. CI runs them instead.
RUN ./gradlew --no-daemon bootJar -x test

# ── Run ──────────────────────────────────────────────────────────────────────
# JRE only: no compiler, no build tools, a smaller image and less to attack.
FROM eclipse-temurin:21-jre-alpine

# wget comes from busybox and is what the healthcheck below uses.
RUN addgroup -S app && adduser -S -G app app

WORKDIR /app
COPY --from=build --chown=app:app /build/build/libs/*.jar app.jar

USER app

EXPOSE 8080

# MaxRAMPercentage rather than a fixed -Xmx, so the heap follows whatever the host gives the
# container — free tiers hand out 512 MB and a fixed heap would either waste it or overrun it.
# SerialGC because on one or two cores the parallel collectors cost more in memory and threads
# than they return for this amount of traffic.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"

# /login is public, so this needs no credentials and still proves the app is serving.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/login > /dev/null || exit 1

# exec so the JVM is PID 1 and receives SIGTERM directly — the container stops promptly
# instead of waiting out the ten second kill timeout.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
