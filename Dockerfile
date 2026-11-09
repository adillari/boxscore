# Build stage: the jar is built inside Docker, so no local Java or Maven is needed.
FROM maven:3.9-eclipse-temurin-8 AS build
WORKDIR /app

# Resolve dependencies first so this layer is cached until pom.xml changes.
COPY pom.xml .
RUN mvn -B -q package -Dmaven.test.skip=true -Dspring-boot.repackage.skip=true

COPY src src
RUN mvn -B -q -o package -Dmaven.test.skip=true

# Run stage: a smaller image with only the Java runtime and the jar.
FROM eclipse-temurin:8-jre
COPY --from=build /app/target/boxscore-0.1.0.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
