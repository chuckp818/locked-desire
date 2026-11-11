# LOCKED DESIRE

Android-App (Kotlin, Jetpack Compose) für Erwachsene ab 18: BDSM-Rollenspiel, freiwillige Keuschheit, Selbstkontrolle, Fitness, Fetischbibliothek, Toy-Verwaltung, Statistiken und ein KI-Dungeon-Master.

## Installation (ein Klick)

1. Auf dem Handy die Seite **Releases → latest** dieses Repos öffnen.
2. `LockedDesire.apk` herunterladen und antippen.
3. Beim ersten Mal „Installation aus unbekannten Quellen“ für den Browser bzw. die Dateien-App erlauben.

Updates werden mit demselben Schlüssel signiert und lassen sich direkt über die alte Version installieren, deine Daten bleiben erhalten.

## Automatischer Build

Jeder Push auf `main` startet `.github/workflows/build.yml`: Unit-Tests, APK-Build, Veröffentlichung als Release `latest`.

Lokal (Android Studio oder Kommandozeile mit Android SDK, JDK 17, Gradle 8.9):

```
gradle testDebugUnitTest assembleDebug
```

Die APK liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`.

## Architektur

| Bereich | Ort |
|---|---|
| Room-Datenbank (31 Entitäten, SQLCipher-verschlüsselt, Schema unter `app/schemas`) | `data/Entities.kt`, `data/Daos.kt`, `data/AppDatabase.kt` |
| Startdaten: 200 Aufgaben, Fetischbibliothek, Personas, Erfolge | `assets/tasks.txt`, `data/Seed.kt` |
| Repository mit gesamter Logik | `data/Repo.kt` |
| Challenge-Engine, Level, Dungeon-Planer, Roulette | `engine/Engines.kt` |
| KI-Schnittstelle (offline regelbasiert, lokal, Cloud) | `ai/AiEngine.kt` |
| Verschlüsselung (Android Keystore, AES-GCM) | `security/Crypto.kt` |
| Hintergrund-Benachrichtigungen (WorkManager) | `notify/Notifier.kt` |
| Oberflächen (30 Screens) und Navigation | `ui/` |

### Bewusste Abweichungen von der Spezifikation

- **Hilt** ist durch einen einfachen Service-Locator (`LockedDesireApp.repo`) ersetzt. Gleiche Trennung von ViewModel und Repository, aber weniger Abhängigkeiten und kürzere Build-Zeiten.
- **Lokales Sprachmodell**: Ein Modell direkt in der APK würde sie um mehrere Gigabyte vergrößern. Stattdessen wird jedes lokale Modell angebunden, das eine OpenAI-kompatible Schnittstelle anbietet (z. B. llama.cpp oder Ollama in Termux unter `http://127.0.0.1:8080/v1`). Ohne Modell übernimmt der vollständig offline arbeitende regelbasierte Dungeon-Master.
- **APK-Signatur**: Der Signaturschlüssel liegt im Repo (`keystore/`), damit jeder Build updatefähig bleibt. Das Repo muss deshalb privat bleiben.

### Hardcore-Dungeon

Eigener Modus mit 304 Prüfungen in 12 Kategorien (D/s, Humiliation, Chastity, Orgasm Control, Anal Play, Toy Play, CBT, Pussy Play, Nipple Play, Bondage, Impact, Petplay) und sechs Stufen von *Streng* bis *Albtraum*.

| Bereich | Ort |
|---|---|
| Aufgabenbibliothek (Generator: `tools/hcgen.py`) | `assets/hardcore_tasks.txt` |
| Tabellen `HardcoreTask`, `HardcoreRun`, `HardcoreSettings`, Migration 1→2 | `data/HardcoreData.kt`, `data/AppDatabase.kt` |
| Prüfregeln, Serien, Statistik | `engine/Hardcore.kt` |
| Repository | `data/HardcoreRepo.kt` |
| Hardcore-Personas (Eiskalt, Gnadenlos, Sadistisch, Spöttisch, Neutral) | `ai/HardcoreVoice.kt` |
| Oberfläche und ViewModel | `ui/screens/HardcoreScreens.kt` |

- Zustände: VERFÜGBAR → ANGENOMMEN → AKTIV → BESTANDEN / NICHT BESTANDEN / ABGEBROCHEN.
- Nur BESTANDEN bringt XP. Nicht bestanden und Abgebrochen: 0 XP, kein Abzug, keine Ersatzstrafe.
- Höhere Stufen: mehr Teilaufgaben, Kombinationen aus mehreren Kategorien, längere nichtkörperliche Phasen, mehr XP. Körperliche Dauer und Intensität steigen nicht.
- CBT und Pussy Play sind nur Session-Rahmen nach der eigenen Liste des Nutzers und erscheinen nur mit freiwilliger Anatomie-Angabe, nie abgeleitet aus dem Geschlecht.
- Echte Fesselung nur mit anwesender Partnerperson.
- Eingebunden in Dashboard, Chat, Personas, Fetish Universe, Roulette, 24-Stunden-Dungeon, Toy-Galerie, Profil, Statistiken und Benachrichtigungen.

### Sicherheitsprinzipien

- Jede Aufgabe, auch jeder KI-Vorschlag, läuft durch die Challenge-Engine: Kategorie, persönliche Ausschlüsse, verbotene Begriffe, Belastungsgrenzen, Tageslimit, Ruhezeiten und eine feste Sperrliste gefährlicher Praktiken.
- Safeword und Beschwerden werden lokal erkannt, nie von einem KI-Modell entschieden.
- Jede Aufgabe muss bestätigt werden; Ablehnen, Pausieren und Abbrechen kosten nie etwas.
- Keuschheitsziele verlängert nur der Nutzer selbst.
- Externe KI nur nach ausdrücklicher Einwilligung und nur mit freigegebenen Daten.
