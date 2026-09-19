FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml .
RUN mvn --batch-mode -DskipTests dependency:go-offline

COPY src ./src
RUN mvn --batch-mode -DskipTests package

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=build /workspace/target/*.jar /app/app.jar

USER app
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
