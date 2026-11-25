# Stage 1: Build Spring Boot Java 21 JAR with Maven
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Lightweight Java 21 JRE Runtime for Render Web Service
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p /app/data
COPY --from=build /app/target/*.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=render
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-Xmx384m", "-jar", "/app/app.jar"]
