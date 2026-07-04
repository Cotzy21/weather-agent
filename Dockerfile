# Én selvbygd container: React-frontend + Spring-backend -> én kjørbar jar.
# Hosten (Render/Railway/Fly) bygger denne, så du trenger ikke Docker lokalt.

# --- Steg 1: bygg React-frontend inn i Spring sin static-mappe ---
FROM node:20-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
# Vite baker inn disse ved byggetid. Anon-nøkkelen er offentlig og trygg i
# frontend; sett dem som "build args" hos hosten (se DEPLOY.md).
ARG VITE_SUPABASE_URL
ARG VITE_SUPABASE_ANON_KEY
ARG VITE_API_BASE=""
RUN npm run build
# vite.config skriver til ../src/main/resources/static => /app/src/main/resources/static

# --- Steg 2: bygg Spring Boot-jar (inkluderer frontend-bygget) ---
FROM maven:3.9-eclipse-temurin-23 AS backend
WORKDIR /app
COPY pom.xml ./
COPY src ./src
COPY --from=frontend /app/src/main/resources/static ./src/main/resources/static
RUN mvn -B -q -DskipTests package

# --- Steg 3: slank kjøre-image (kun JRE + jar) ---
FROM eclipse-temurin:23-jre
WORKDIR /app
COPY --from=backend /app/target/weather-agent-*.jar app.jar
EXPOSE 8080
# Container-vennlige JVM-flagg (viktig på små hosts som Render free / 512 MB):
#  - MaxRAMPercentage: bruk en ANDEL av containerens RAM, ikke anta en stor maskin
#    (uten dette tar JVM for mye og blir OOM-drept -> "henger"/restart-loop).
#  - SerialGC: lavest minne/CPU-overhead på 1-CPU-maskiner.
#  - ExitOnOutOfMemoryError: krasj hardt ved OOM så hosten restarter rent.
# Kan overstyres ved å sette JAVA_OPTS som env-variabel hos hosten. exec => java
# blir PID 1 og får SIGTERM for ryddig avslutning.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=65.0 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
