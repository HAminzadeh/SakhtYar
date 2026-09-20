FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY . .

RUN mvn --batch-mode -pl app -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=build /workspace/app/target/sakhtyar-app-*.jar /app/app.jar

USER app
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
