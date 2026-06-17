FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY services/discovery-service/pom.xml services/discovery-service/
COPY schema/discovery/ schema/discovery/
RUN cd services/discovery-service && mvn dependency:go-offline -q
COPY services/discovery-service/src/ services/discovery-service/src/
RUN cd services/discovery-service && mvn clean package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/services/discovery-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
