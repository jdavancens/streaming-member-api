FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY services/billing-service/pom.xml services/billing-service/
COPY schema/billing/ schema/billing/
RUN mvn dependency:go-offline -pl services/billing-service -am -q
COPY services/billing-service/src/ services/billing-service/src/
RUN mvn clean package -pl services/billing-service -am -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/services/billing-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
