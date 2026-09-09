FROM gradle:8.7-jdk21 AS build
WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY server ./server
RUN sed -i 's/include(":server", ":androidApp")/include(":server")/' settings.gradle.kts
RUN gradle :server:installDist --no-daemon

FROM eclipse-temurin:21-jre
ARG APP_REVISION=unknown
WORKDIR /app
COPY --from=build /workspace/server/build/install/server /app
ENV PORT=8080 \
    DATABASE_URL=/data/mampfi.db \
    UPLOAD_DIR=/data/uploads \
    BACKUP_DIR=/data/backups \
    APP_REVISION=${APP_REVISION}
LABEL org.opencontainers.image.revision=${APP_REVISION}
EXPOSE 8080
ENTRYPOINT ["/app/bin/server"]
