FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY services/member-service/pom.xml services/member-service/
COPY schema/member/ schema/member/
RUN mvn dependency:go-offline -pl services/member-service -am -q
COPY services/member-service/src/ services/member-service/src/
RUN mvn clean package -pl services/member-service -am -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/services/member-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
