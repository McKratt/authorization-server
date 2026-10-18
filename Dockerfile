# syntax=docker/dockerfile:1

ARG ALPINE_VERSION=3.23

# --- Stage 1: build the application jar and its dependency libs ---
FROM maven:3-eclipse-temurin-25-alpine AS build
WORKDIR /build
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

# --- Stage 2: custom JRE with jlink, containing only the modules the app needs ---
FROM eclipse-temurin:25-jdk-alpine AS jre
WORKDIR /build
COPY --from=build /build/target/authorization-server.jar .
COPY --from=build /build/target/libs libs
# jdk.unsupported is used reflectively (sun.misc.Unsafe) and is not detected by jdeps.
RUN modules="$(jdeps --ignore-missing-deps --print-module-deps --multi-release 25 \
            --class-path 'libs/*' authorization-server.jar libs/*.jar 2>/dev/null | tail -1),jdk.unsupported" \
    && echo "jlink modules: ${modules}" \
    && jlink --add-modules "${modules}" \
            --strip-debug --no-man-pages --no-header-files \
            --compress=zip-6 \
            --output /opt/jre

# --- Stage 3: Leyden AOT training run (JEP 483/514/515) ---
# Must run on the exact same JRE and class path as the final image: the AOT cache
# is only accepted when the JVM build and the jar paths/timestamps match.
FROM alpine:${ALPINE_VERSION} AS aot
ENV JAVA_HOME=/opt/jre PATH=/opt/jre/bin:$PATH
COPY --from=jre /opt/jre /opt/jre
WORKDIR /app
COPY --from=build /build/target/authorization-server.jar .
COPY --from=build /build/target/libs libs
# Start the server, exercise it, then SIGTERM it: the JVM writes the cache on exit.
# No database or identity provider exists during the build: migrations are disabled and the JWT
# settings are placeholders (only unauthenticated requests are exercised, no JWKS is fetched).
RUN <<EOF
set -eu
java -XX:AOTCacheOutput=app.aot -Ddb.migrate=false \
    -Djwt.issuer=https://training.invalid -Djwt.jwks-uri=https://training.invalid/jwks -Djwt.audiences=training \
    -jar authorization-server.jar &
pid=$!
for i in $(seq 1 30); do
    wget -q -O /dev/null http://localhost:8080/observe/health && break
    sleep 1
done
for i in $(seq 1 50); do
    wget -q -O /dev/null http://localhost:8080/observe/health
    wget -q -O /dev/null http://localhost:8080/api/v1/authorizations?application=training || true
    wget -q -O /dev/null --header 'Authorization: Bearer not-a-jwt' \
        http://localhost:8080/api/v1/authorizations?application=training || true
done
kill -TERM "$pid"
wait "$pid" || true
test -s app.aot
EOF

# --- Stage 4: runtime image ---
FROM alpine:${ALPINE_VERSION}
ENV JAVA_HOME=/opt/jre PATH=/opt/jre/bin:$PATH
RUN addgroup -S app && adduser -S -G app app
COPY --from=aot /opt/jre /opt/jre
WORKDIR /app
COPY --from=aot /app/authorization-server.jar /app/app.aot ./
COPY --from=aot /app/libs libs
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:AOTCache=app.aot", "-jar", "authorization-server.jar"]
