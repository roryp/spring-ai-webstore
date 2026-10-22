# 1. Build the app with Maven.
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /src
COPY . .
RUN sh mvnw -B -ntp -DskipTests package

# 2. Run it on a small Java runtime, as a non-root user.
FROM eclipse-temurin:25-jre-noble
COPY --from=build /src/target/*.jar /app.jar
USER 1000
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app.jar"]
