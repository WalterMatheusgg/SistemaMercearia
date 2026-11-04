FROM node:24.22.2-alpine AS frontend
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk AS backend
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY backend/ backend/
COPY --from=frontend /src/frontend/dist/frontend/browser/ backend/src/main/resources/static/
RUN sh mvnw --batch-mode -Dmaven.test.skip=true -pl backend package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend --chown=10001:10001 /src/backend/target/backend-0.1.0-SNAPSHOT.jar /app/application.jar
USER 10001:10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["java","-jar","/app/application.jar"]
