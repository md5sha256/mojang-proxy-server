# syntax=docker/dockerfile:1.7
# Builds the mojang-proxy-server jar from source and runs it, so the jar is
# produced automatically as part of `docker compose up --build` (no host build).

# Stage 1: build the Spring Boot fat jar with Maven (the project has no wrapper).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Resolve dependencies in their own layer, keyed only on pom.xml. As long as the
# pom is unchanged this layer is a cache hit, so source-only rebuilds skip
# dependency resolution entirely. The cache mount also persists ~/.m2 across builds.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests dependency:go-offline
# Source changes invalidate from here down; with deps already cached this is
# just a recompile + repackage.
COPY src src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests package

# Stage 2: slim runtime with just the JRE and the repackaged jar.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/mojang-proxy-server.jar app.jar
# proxy.properties is read from the working dir; ship one so defaults apply.
COPY proxy.properties proxy.properties
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "app.jar"]
