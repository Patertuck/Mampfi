# Mampfi

Privater Mahlzeitenplaner für zwei Nutzer. Das Repository besteht aus einer Android-Compose-App und einem Ktor-Service mit SQLite.

## Lokal starten

Server: `./gradlew :server:run` (Windows: `./gradlew.bat :server:run`). Standardmäßig läuft er auf `http://localhost:8080`.

## Homeserver mit Cloudflare Tunnel

Der produktive Server wird ausschließlich über einen Cloudflare Tunnel erreicht. Cloudflare Access schützt API und Bilder mit einem eigenen Service Token pro Telefon. Es ist keine Portweiterleitung im Router und weder Tailscale noch Cloudflare One Agent auf den Telefonen erforderlich.

1. Richte Tunnel, Hostname, Access-Anwendung und Telefon-Tokens gemäß [Backend-Deployment](docs/backend-deployment.md#cloudflare-tunnel-and-access) ein.
2. Kopiere `.env.example` auf dem Homeserver nach `.env` und trage den Tunnel-Token ein.
3. Starte den Stack:

   ```bash
   docker compose pull
   docker compose up -d
   curl http://127.0.0.1:8080/api/mahlzeiten
   ```

   Datenbank, Bilder und Backups liegen dauerhaft in `data/`. Port 8080 ist nur an die Loopback-Adresse des Homeservers gebunden.
4. Baue die Android-App mit `./gradlew :androidApp:assembleDebug` (Windows: `.\gradlew.bat :androidApp:assembleDebug`). Beim ersten Start werden der HTTPS-Hostname sowie Client-ID und Client-Secret des jeweiligen Telefon-Tokens eingegeben.

## Android-Releases

Jeder Merge nach `main` startet die GitHub-Actions-Workflow-Datei `.github/workflows/android-release.yml`. Nach erfolgreichen Tests erstellt sie eine signierte APK und veröffentlicht sie zusammen mit dem Update-Manifest in einem GitHub Release.

Die erste Release-APK wird manuell auf jedem Telefon installiert. Spätere App-Starts erkennen einen neuen GitHub Release, zeigen dessen Version und laden ihn erst nach Bestätigung herunter. Android zeigt danach immer die Systembestätigung für die Installation.

Die einmalige Keystore- und GitHub-Secrets-Einrichtung steht in [docs/android-releases.md](docs/android-releases.md).

## Automatische Backend-Deployments

GitHub Actions veröffentlicht Backend-Images in GHCR. Der Homeserver aktualisiert den markierten Mampfi-Container mit Watchtower und erstellt davor ein geprüftes SQLite-Backup. Einrichtung, Plattformkonfiguration und Rollback sind in [docs/backend-deployment.md](docs/backend-deployment.md) beschrieben.
