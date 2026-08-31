# Mampfi

Privater Mahlzeitenplaner für zwei Nutzer. Das Repository besteht aus einer Android-Compose-App und einem Ktor-Service mit SQLite.

## Starten

1. Server: `./gradlew :server:run` (Windows: `./gradlew.bat :server:run`; Standard: `http://localhost:8080`, Datenbank `mampfi.db`, Bilder in `uploads/`).
2. Android: `API_BASE_URL` in `androidApp/build.gradle.kts` für ein physisches Gerät auf die LAN-IP des Servers setzen; der Emulator verwendet bereits `10.0.2.2`.

Der Server bietet keine Anmeldung und ist deshalb ausschließlich für ein vertrauenswürdiges privates Netzwerk gedacht.
