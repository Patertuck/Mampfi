FROM gradle:8.7-jdk21 AS build
WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY server ./server
RUN sed -i 's/include(":server", ":androidApp")/include(":server")/' settings.gradle.kts
RUN gradle :server:installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/server/build/install/server /app
ENV PORT=8080 \
    DATABASE_URL=/data/mampfi.db \
    UPLOAD_DIR=/data/uploads
EXPOSE 8080
ENTRYPOINT ["/app/bin/server"]
