
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY . .

RUN mvn -B -pl ewm-stats/stats-server -am clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/ewm-stats/stats-server/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
