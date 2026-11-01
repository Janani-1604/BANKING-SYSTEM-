# ---------- build stage ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- run stage ----------
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/securebank.jar app.jar
ENV JAVA_TOOL_OPTIONS="-Xmx300m -Dorg.slf4j.simpleLogger.defaultLogLevel=warn"
EXPOSE 10000
# Render injects PORT; BankApp reads it and binds to 0.0.0.0
CMD ["java", "-jar", "app.jar"]
