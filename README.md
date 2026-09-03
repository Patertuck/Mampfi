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

   Docker speichert Datenbank und Bilder dauerhaft in `data/`. Diese erste Installation startet leer.
3. Richte die lokale Android-Konfiguration ein und trage deine Server-Adresse ein:

   ```powershell
   Copy-Item mampfi.local.properties.example mampfi.local.properties
   ```

   Öffne danach `mampfi.local.properties` und ersetze `192.168.1.50` durch die LAN-IP deines Homeservers.
4. Baue die Android-App:

   ```powershell
   .\gradlew.bat :androidApp:assembleDebug
   ```

   Der Build prüft beim App-Start zuerst diese LAN-Adresse mit einem kurzen HTTP-Timeout. Bilder werden als relative `/uploads/...`-Pfade gespeichert und deshalb mit derselben aktuell gewählten Server-Adresse geladen.

## Tailscale später ergänzen

Installiere Tailscale auf Homeserver und Telefon im selben Tailnet. Ergänze dann in `mampfi.local.properties` die Tailscale-Adresse:

```properties
mampfi.tailscaleBaseUrl=http://mampfi.tailnet-name.ts.net:8080/
```

Nach einem neuen Build probiert die App beim Start zuerst das LAN. Ist der Server dort nicht innerhalb von 1,5 Sekunden erreichbar, verwendet sie automatisch den Tailscale-Endpunkt. Öffne dafür keine Router-Ports ins Internet.

Der Server bietet keine Anmeldung und ist deshalb ausschließlich für ein vertrauenswürdiges privates Netzwerk oder Tailnet gedacht.
