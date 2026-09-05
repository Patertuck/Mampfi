# Android releases

The app does not contain LAN or Tailscale addresses. Each phone asks for them at its first launch and stores them privately on that device. They can later be changed through **Einstellungen** in the app.

Every push to `main` runs `.github/workflows/android-release.yml`. A successful run creates a signed APK and a GitHub Release containing an `update.json` manifest. On launch, a release APK checks the latest manifest and offers to download a newer version.

## One-time signing setup

Create one keystore on a trusted computer and keep a backup. Android only accepts future updates signed by the same key.

```bash
keytool -genkeypair -keystore mampfi-release.jks -alias mampfi -keyalg RSA -keysize 4096 -validity 10000
base64 -w 0 mampfi-release.jks
```

For PowerShell, generate the Base64 value with:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('mampfi-release.jks'))
```

Add these repository secrets in GitHub:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`

Install the first GitHub Release APK manually on each phone. Android will ask for permission to allow Mampfi to install updates; future updates remain user-approved and finish in Android's system installer.
