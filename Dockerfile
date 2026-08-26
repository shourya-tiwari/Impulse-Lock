# Multi-stage build - see docs/v2/deployment-plan.md#dockerfiles.
# Stage 1: build the jar with Maven, using the wrapper so the build toolchain matches local dev.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .
RUN chmod +x mvnw && ./mvnw -q -o dependency:go-offline -B || ./mvnw -q dependency:go-offline -B
COPY src/ src/
RUN ./mvnw -B -DskipTests package

# Stage 2: run on a minimal JRE, as a non-root user.
FROM eclipse-temurin:17-jre-alpine AS runtime
RUN addgroup -S impulselock && adduser -S impulselock -G impulselock
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
USER impulselock
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
