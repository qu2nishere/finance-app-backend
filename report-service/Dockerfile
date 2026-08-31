# ---- Build ----
# Dung gradle wrapper san co nen khong phu thuoc image gradle version cu the.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Copy wrapper truoc de Docker cache lop tai Gradle distribution.
COPY gradlew ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --version --no-daemon

# Copy build script de cache lop tai dependency.
COPY settings.gradle build.gradle ./
RUN ./gradlew dependencies --no-daemon || true

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ---- Run ----
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd -r -u 1001 spring
COPY --from=build /app/build/libs/*.jar app.jar
USER spring
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
