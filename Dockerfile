# syntax=docker/dockerfile:1
# ===== Build stage =====
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom + maven wrapper first for better layer caching
COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw mvnw
COPY mvnw.cmd mvnw.cmd
RUN chmod +x mvnw

# Download deps (cached)
RUN ./mvnw -q -DskipTests dependency:go-offline

# Copy source & build
COPY src src
RUN ./mvnw -q -DskipTests package

# ===== Runtime stage =====
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy jar (Spring Boot Maven plugin)
COPY --from=build /app/target/*.jar /app/app.jar

ENV PORT=8080
EXPOSE 8080

# Optional JVM tuning via JAVA_OPTS
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]