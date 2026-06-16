FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY services/entitlement-service/pom.xml services/entitlement-service/
COPY schema/entitlement/ schema/entitlement/
RUN mvn dependency:go-offline -pl services/entitlement-service -am -q
COPY services/entitlement-service/src/ services/entitlement-service/src/
RUN mvn clean package -pl services/entitlement-service -am -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/services/entitlement-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
