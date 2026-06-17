FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY services/member-service/pom.xml services/member-service/
COPY schema/member/ schema/member/
RUN cd services/member-service && mvn dependency:go-offline -q
COPY services/member-service/src/ services/member-service/src/
RUN cd services/member-service && mvn clean package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/services/member-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
