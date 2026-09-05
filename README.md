# Mampfi

Privater Mahlzeitenplaner für zwei Nutzer. Das Repository besteht aus einer Android-Compose-App und einem Ktor-Service mit SQLite.

## Lokal starten

Server: `./gradlew :server:run` (Windows: `./gradlew.bat :server:run`). Standardmäßig läuft er auf `http://localhost:8080`.

## Homeserver im LAN

1. Gib dem Homeserver im Router eine feste DHCP-Reservierung, zum Beispiel `192.168.1.50`.
2. Kopiere das Repository auf den Linux-Homeserver und starte dort:

   ```bash
   docker compose up -d --build
   curl http://192.168.1.50:8080/api/mahlzeiten
   ```

   Docker speichert Datenbank und Bilder dauerhaft in `data/`.
3. Baue die Android-App:

   ```powershell
   .\gradlew.bat :androidApp:assembleDebug
   ```

   Beim ersten Start fragt die App nach der LAN-Adresse. Wenn du Tailscale verwendest, trägst du dort auch die optionale Tailscale-Adresse ein. Die App probiert zuerst LAN und verwendet Tailscale, wenn der LAN-Server nicht erreichbar ist.

Der Server bietet keine Anmeldung und ist deshalb ausschließlich für ein vertrauenswürdiges privates Netzwerk oder Tailnet gedacht.

## Android-Releases

Jeder Merge nach `main` startet die GitHub-Actions-Workflow-Datei `.github/workflows/android-release.yml`. Nach erfolgreichen Tests erstellt sie eine signierte APK und veröffentlicht sie zusammen mit dem Update-Manifest in einem GitHub Release.

Die erste Release-APK wird manuell auf jedem Telefon installiert. Spätere App-Starts erkennen einen neuen GitHub Release, zeigen dessen Version und laden ihn erst nach Bestätigung herunter. Android zeigt danach immer die Systembestätigung für die Installation.

Die einmalige Keystore- und GitHub-Secrets-Einrichtung steht in [docs/android-releases.md](docs/android-releases.md).
