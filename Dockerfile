FROM node:22-bookworm-slim AS frontend
WORKDIR /build/web
COPY web/package*.json ./
RUN npm ci
COPY web/ ./
RUN npm run check && npm run build

FROM eclipse-temurin:21-jdk AS backend
WORKDIR /build
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
COPY core/ core/
COPY api/ api/
COPY benchmarks/ benchmarks/
COPY --from=frontend /build/web/dist/ web/dist/
RUN chmod +x gradlew && ./gradlew --no-daemon :api:bootJar

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=backend /build/api/build/libs/three-color.jar app.jar
USER 10001:10001
EXPOSE 18380
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
