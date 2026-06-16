FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY services/profile-service/pom.xml services/profile-service/
COPY schema/profile/ schema/profile/
RUN mvn dependency:go-offline -pl services/profile-service -am -q
COPY services/profile-service/src/ services/profile-service/src/
RUN mvn clean package -pl services/profile-service -am -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/services/profile-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
