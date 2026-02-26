# Spotify Downloader - Neue Version

Ich habe eine neue, modernisierte Version der App-Struktur im Ordner `newversion/` erstellt. 
Diese Version nutzt aktuelle Komponenten und Best Practices:

## Architektur & Komponenten
- **Jetpack Compose Material 3**: Für ein modernes UI/UX Design.
- **MVVM Architektur**: Trennung von Logik (ViewModel) und UI.
- **Ktor Client 3.x**: Für moderne, coroutine-basierte Netzwerkanfragen.
- **Clean Architecture**: Klare Aufteilung in `data`, `domain` und `ui`.
- **Hilt-ready**: Die Struktur ist für Hilt Dependency Injection vorbereitet.

## Was du tun musst:
Da ich keinen direkten Zugriff auf die Root-Gradle-Dateien habe, musst du folgende Änderungen manuell vornehmen, um die neue Version vollständig zu aktivieren:

### 1. `libs.versions.toml` aktualisieren
Ersetze den Inhalt deiner `gradle/libs.versions.toml` mit den aktuellen Versionen (siehe unten).

### 2. Gradle-Dateien anpassen
Stelle sicher, dass `hilt`, `compose` und `ktor` in deinem `app/build.gradle.kts` korrekt konfiguriert sind.

### 3. Manifest aktualisieren
Ändere im `AndroidManifest.xml` die `MainActivity` auf `com.example.spotifydownloader.newversion.ui.MainActivity`.

## Empfohlene `libs.versions.toml`:
```toml
[versions]
agp = "8.8.0"
kotlin = "2.1.0"
coreKtx = "1.15.0"
composeBom = "2025.02.00"
hilt = "2.54"
ktor = "3.0.3"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
ktor-client-core = { group = "io.ktor", name = "ktor-client-core", version.ref = "ktor" }
```

Viel Erfolg mit der neuen Version!
