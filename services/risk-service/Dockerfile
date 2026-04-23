FROM maven:3.9-amazoncorretto-21 AS builder
WORKDIR /build

# Install parent POM into local Maven repo (non-recursive — no child modules)
COPY pom.xml .
RUN mvn -N install -B

# Download service dependencies offline
WORKDIR /build/services/risk-service
COPY services/risk-service/pom.xml .
RUN mvn dependency:go-offline -B

# Build service JAR
COPY services/risk-service/src ./src
RUN mvn clean package -DskipTests -B

FROM amazoncorretto:21-alpine
WORKDIR /app
COPY --from=builder /build/services/risk-service/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
