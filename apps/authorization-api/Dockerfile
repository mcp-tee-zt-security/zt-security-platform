FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml ./pom.xml
COPY apps/authorization-api/pom.xml ./apps/authorization-api/pom.xml
COPY apps/authorization-api/src ./apps/authorization-api/src
RUN mvn -q -pl apps/authorization-api -am -DskipTests package
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/apps/authorization-api/target/authorization-api-2.0.0.jar app.jar
RUN groupadd --gid 10001 zt && useradd --uid 10001 --gid 10001 --no-create-home zt
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
