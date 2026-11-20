# KopfFrei – Phase 1 (technische Grundlage)

*Dein Kopf. Dein Tempo. Dein Alltag.*

Android-App für Menschen mit ADHS/ADS: Gedanken festhalten, Aufgaben und Termine im eigenen Tempo,
Erinnerungen, die nicht „wegwischbar“ sind – alles lokal auf dem Gerät, ohne Konto, ohne Internet.

Technik: Kotlin · Jetpack Compose · Material 3 · Room · Hilt · DataStore · Glance (Widgets) ·
mehrere Gradle-Module · Offline-first.

---

## 1. Ehrlicher Prüfstatus (bitte zuerst lesen)

**Die Android-Module wurden in der Entstehungsumgebung NICHT kompiliert.** Dort gab es weder ein
Android-SDK noch Zugriff auf Gradle/Google-Maven. Es wurde also **kein einziger `./gradlew`-Lauf**
durchgeführt und **keine APK gebaut**. Rechne beim ersten Build mit kleinen Compilerfehlern
(z. B. ein Icon-Name, eine Funktionssignatur) – siehe Abschnitt 8 „Fehlerbehebung“.

| Was | Ergebnis |
|---|---|
| Kompilierung der Android-Module (`app`, `core:database/designsystem/security/reminders`, alle `feature:*`) | **nicht durchgeführt** |
| APK-Build | **nicht durchgeführt** |
| Reine Kotlin-Logik (`core:common`, ohne Android) | mit `kotlinc` 2.2.20 kompiliert; **89 Tests** über einen selbstgebauten Mini-JUnit-Ersatz ausgeführt: 89 bestanden, 0 fehlgeschlagen |
| Routen & Deep-Link-Muster (`app/…/navigation/Routes.kt`) | kompiliert und **9 Tests** ausgeführt (Ersatz-Runner): 9 bestanden |
| Terminserien-Berechnung (`EventExpander` in `core:database`) | kompiliert (mit Attrappen für die Room-Annotationen) und **12 Tests** ausgeführt (Ersatz-Runner): 12 bestanden |
| Room-DAO-Tests (`core/database/src/androidTest/…/DaoTest.kt`, 19 Tests) | **geschrieben, nicht ausgeführt** (brauchen Emulator/Gerät) |
| Syntax aller Kotlin-Dateien | per Tree-sitter geprüft: keine Syntaxfehler (sagt nichts über Typen/Signaturen) |
| Import-/Modul-/Ressourcen-Konsistenz | `tools/check_consistency.py`: 0 Fehler (Importe zeigen auf vorhandene Deklarationen, Modul- und Bibliotheksabhängigkeiten sind deklariert, `R.…`-Verweise, Manifest-Klassen und `@…`-Ressourcen existieren, XML wohlgeformt) |
| Verhalten auf einem Gerät (Erinnerungen/Doze, Widgets, Biometrie, Spracheingabe, Benachrichtigungen, Oberfläche) | **nicht getestet** |

Wichtig zu den „110 lokal ausgeführten Tests“: Sie liefen **nicht** mit echtem JUnit und **nicht**
mit Gradle, sondern mit einem kleinen eigenen Ersatz (`@Test`, `assertEquals` …). Die Tests sind
normales JUnit 4 und laufen im echten Projekt mit `./gradlew testDebugUnitTest`; dieser Lauf steht
noch aus.

---

## 2. Funktionsumfang Phase 1

Legende: ✅ umgesetzt (ungetestet auf Gerät) · ⚠️ teilweise · ❌ **nicht umgesetzt**

| Bereich | Status | Anmerkung |
|---|---|---|
| Design: Dunkel (Standard), Hell, Reizarm, Hoher Kontrast; Akzentfarbe; Schriftgröße; Text ≥ 16 sp; Touchziele ≥ 48 dp (64 dp mit „größere Schaltflächen“); Animationen reduzieren | ✅ | Reizarm = gedämpfte Farben |
| Navigation Heute · Notizen · Kalender · Fokus · Mehr | ✅ | |
| Konfigurierbare Schnellaktion (schwebender Knopf) | ✅ | Notiz, Einsprechen, Aufgabe, Termin, Kopf ausschütten; sichtbar auf den fünf Hauptseiten und der Aufgabenübersicht |
| Startseite „Mein Kopf heute“ mit anpassbaren Karten (Reihenfolge, ausblenden, kompakt) | ✅ | Schnellaktionen, Stimmung, nächster Termin, drei wichtigste Aufgaben, aktive Erinnerungen, Gedanke festhalten, Fokus-Timer, Schnellzugriffe |
| Notizen: schnell, Spracheingabe, Checklisten, Anhänge (Bild/Datei), Farben, Kategorien, anheften, Suche, Archiv, Papierkorb mit Wiederherstellung | ✅ | Spracheingabe über die Spracherkennung des Systems |
| Notiz → Aufgabe / Termin / Erinnerung / Tagebucheintrag | ✅ | |
| Gedanken-Parkplatz | ✅ | eigener Reiter in „Notizen“, auch als Widget |
| Notizen sperren (PIN/Biometrie) | ⚠️ | **Nur als App-Sperre für die ganze App**, nicht pro Notiz |
| Tagebuch (Text, Stimmung, Tags, Suche, Filter, Export als Textdatei) | ⚠️ | ❌ keine Audio-Originalaufnahme, ❌ keine Kalenderansicht, ❌ keine Anhänge im Tagebuch |
| Aufgaben: Priorität, Fälligkeit, Zeitschätzung, Wiederholung, Teilschritte, Pausieren, Verschieben, Erinnerungen | ✅ | Sortieren der Schritte per Auf/Ab-Knöpfen |
| „Aufgabe zerlegen“ mit KI | ❌ | gehört zu Phase 2 (im Editor als Hinweis gekennzeichnet) |
| Kalender: Tag, Woche, Monat, Agenda | ⚠️ | Wochenansicht **ohne Stundenraster** (Liste je Tag) |
| Termine: Serien, mehrstufige Erinnerungen, Vorbereitungs- und Fahrzeit, Anhänge, Aufgabenverknüpfung | ⚠️ | Serien nur täglich/wöchentlich/monatlich/jährlich mit Intervall und Enddatum; ❌ **keine** mehrfachen Wochentage, ❌ **keine** Ausnahmen für einzelne Termine einer Serie (Serie wird als Ganzes bearbeitet) |
| ICS-Import und -Export | ⚠️ | RRULE nur im oben genannten Umfang; beim Import werden Termine mit bekannter UID übersprungen |
| Erinnerungssystem (mehrstufig, „Gesehen“/„Erledigt“, Später, automatische Wiederholung, Neustart/Zeitumstellung, Berechtigungen) | ✅ | Details in Abschnitt 6. ❌ Nach einem Neustart wird eine bereits angezeigte, noch unbestätigte Erinnerung **nicht** erneut angezeigt |
| 8 Widgets (Schnellnotiz, Spracheingabe, Heutige Termine, Aufgabenliste, Angeheftete Notiz, Fokus-Timer, Tagesübersicht, Gedanken-Parkplatz; Größen 2×1, 2×2, 4×2, 4×4) | ⚠️ | Widgets sind statisch: **kein** live laufender Countdown; sie aktualisieren sich bei Datenänderungen und etwa alle 30 Minuten. Farben: dunkle Palette, außer im Hell-Modus |
| Fokus-Timer | ⚠️ | Timer mit Ende-Erinnerung; ❌ keine Fokus-Übungen, ❌ keine Atemhilfen (Phase 2) |
| „Ich bin überfordert“ (SOS) | ⚠️ | **Nur Basis:** beruhigender Text, Kopf ausschütten, 5-Minuten-Start, eine Aufgabe wählen, Stimmung festhalten. Geführte Übungen = Phase 2 |
| Kopf ausschütten | ✅ | Freitext/Sprache, laufend als Entwurf gesichert; in Gedanken, Aufgaben oder eine Notiz umwandeln |
| Einstellungen | ✅ | Darstellung, Startseite, Erinnerungen/Berechtigungen, App-Sperre, Export, „Alle Daten löschen“ |
| Lokale Datenbank (Room, UUIDs, Zeitstempel, Schema-Export, Migrationsrahmen) | ✅ | Version 1; ❌ noch keine Migration nötig und daher kein Migrationstest |
| Datenexport | ⚠️ | JSON-Export **ohne Anhangsdateien**; ❌ **kein Import** |
| Routinen, KI-Assistent, Statistiken, Community, Partner, Cloud/Sync/Backend | ❌ | **Phasen 2–4, bewusst nicht gebaut.** Unter „Mehr“ als „Noch nicht verfügbar“ gekennzeichnet |

Grundsätze, die im Code beachtet wurden: keine Streaks, keine Strafen, keine Schuldgefühle; die
(spätere) KI stellt keine Diagnosen; Standardtexte sind kurz und ruhig.

---

## 3. Projektstruktur

```
KopfFrei/
├── app/                      Application, MainActivity, Navigation, „Mehr“, SOS, Kopf ausschütten, Sperrbildschirm
├── core/
│   ├── common/               reine Kotlin-Logik ohne Android (Wiederholungen, Erinnerungs-Planung, ICS, Kalender-/Aufgaben-/Widget-Logik …)
│   ├── database/             Room-Datenbank, DAOs, Repositories, DataStore-Einstellungen, Export/Löschen
│   ├── designsystem/         Theme, Farben, wiederverwendbare Compose-Bausteine
│   ├── reminders/            Alarme, Benachrichtigungen, Boot-/Zeit-Empfänger, ReminderCoordinator
│   └── security/             BiometricAuth (App-Sperre über das Betriebssystem)
├── feature/
│   ├── dashboard/  notes/  journal/  tasks/  calendar/  focus/  settings/  widgets/
├── tools/check_consistency.py   statische Konsistenzprüfung (siehe Abschnitt 1)
├── .github/workflows/build-apk.yml   Tests + Debug-APK + Release bei Tag
├── gradle/libs.versions.toml     alle Versionen an einer Stelle
└── settings.gradle.kts, build.gradle.kts, gradle.properties, gradlew …
```

Abhängigkeitsrichtung: `app → feature:* → core:*`. Feature-Module kennen sich gegenseitig nicht; die
`app` verbindet ihre Rückrufe (Navigation) und die Deep Links (`kopffrei://…`).

Versionen: Android Gradle Plugin 8.13.0, Kotlin 2.2.20, Compose BOM 2025.09.01, Room 2.8.0,
Hilt 2.57.1, Glance 1.1.1, Gradle-Wrapper 8.14.3 (Prüfsumme der `gradle-wrapper.jar` stimmt mit der
offiziellen Angabe von gradle.org überein). `minSdk` 26 (Android 8.0), `compileSdk`/`targetSdk` 36, JVM 17.

### Abweichungen vom Auftrag (bewusst)

* Zwei zusätzliche Module: `core:reminders` (Alarm-Technik) und `core:security` (Biometrie).
* Anhänge liegen in **einer** allgemeinen Tabelle `attachments` (Notiz, Tagebuch, Termin) statt nur `NoteAttachments`.
* Einstellungen liegen in DataStore, nicht in Room.
* Die Entitäten für Routinen und Fokus-Sitzungen fehlen im Schema v1 (gehören zu Phase 2); der Fokus-Timer-Zustand liegt in einer kleinen SharedPreferences-Datei.
* Texte sind aktuell fest auf Deutsch im Code; für weitere Sprachen müssten sie nach `strings.xml` ausgelagert werden.

---

## 4. In Android Studio prüfen (Schritt für Schritt)

1. **Voraussetzungen:** eine Android-Studio-Version, die das Android Gradle Plugin 8.13 unterstützt
   (Studio meldet es sonst und bietet ein Update an), JDK 17 (bringt Studio mit), Android-SDK-Plattform 36.
2. **Öffnen:** *File ▸ Open* ▸ Ordner `KopfFrei` wählen. Studio lädt den Gradle-Wrapper und synchronisiert
   das Projekt (*Sync Project with Gradle Files*). Internet wird nur für den Build gebraucht, nicht für die App.
3. **Erster Build:** *Build ▸ Make Project*. Erwartung: ein paar mögliche Fehler (siehe Abschnitt 8) –
   bitte die Meldungen nacheinander beheben; sie sind klein und lokal.
4. **Unit-Tests:** Terminal: `./gradlew :core:common:test testDebugUnitTest`
   (alternativ Rechtsklick auf einen `src/test`-Ordner ▸ *Run Tests*).
5. **Gerätetests (DAOs):** Emulator/Gerät starten, dann `./gradlew :core:database:connectedDebugAndroidTest`.
6. **App starten:** Konfiguration `app` ▸ ▶. Danach durchklicken:
   * Heute: Schnellaktion „Neue Notiz“, Notiz tippen, Zurück → sie erscheint unter „Notizen“.
   * Kalender: Termin mit Erinnerung in 2 Minuten anlegen → Benachrichtigung abwarten; Antippen darf sie
     **nicht** als erledigt markieren, nur „Erledigt“/„Gesehen“.
   * Fokus: 1 Minute starten, App schließen → Ende-Benachrichtigung.
   * Widgets: lange auf den Startbildschirm tippen ▸ Widgets ▸ KopfFrei.
   * Einstellungen ▸ App-Sperre einschalten, App in den Hintergrund legen, nach über 60 s zurückkehren.
7. **Manifest-Prüfung:** *app/src/main/AndroidManifest.xml ▸ Merged Manifest* – dort sind die
   zusammengeführten Berechtigungen und Empfänger sichtbar.

---

## 5. APK bauen

**Lokal:** `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
(Debug-Signatur von Android Studio; direkt installierbar: `adb install -r app-debug.apk`).

**Über GitHub Actions (ohne lokales Android-Setup):**
1. Projekt in ein GitHub-Repository hochladen (Branch `main`).
2. Tab *Actions* ▸ „KopfFrei – Tests und APK“: läuft bei jedem Push. Nach dem Lauf unter
   *Artifacts* **KopfFrei-debug-apk** herunterladen (die ZIP enthält die `.apk`).
3. Für ein Release: Tag `v0.1.0` setzen und pushen (`git tag v0.1.0 && git push origin v0.1.0`) – die APK
   hängt dann automatisch an einem GitHub-Release.
   *Hinweis:* Der Workflow ist **ungetestet** (nicht ausgeführt).

**Release-Signatur:** Der Release-Build ist minifiziert, aber **nicht signiert** – eine unsignierte
Release-APK lässt sich nicht installieren. Für den Play Store bzw. eine eigene Release-APK braucht es
einen eigenen Schlüssel (`signingConfigs`); den bewusst nicht ins Repository legen.

---

## 6. Erinnerungssystem

* Stufen als Abstand vor Termin/Fälligkeit **oder** fester Zeitpunkt; mehrere Stufen je Eintrag.
* Antippen oder Wegwischen der Benachrichtigung bedeutet **nicht** „erledigt“. Erst „Gesehen“ oder
  „Erledigt“ beendet sie. „Später“ verschiebt (Standard 10 Minuten, einstellbar).
* Unbestätigte Erinnerungen wiederholen sich automatisch (höchstens 5 Mal), sofern für die Stufe ein
  Wiederholungsabstand gesetzt ist. Verpasste Erinnerungen (z. B. Gerät war aus) werden bis zu 6 Stunden später nachgeholt.
* Nach Neustart, App-Update, Zeit-/Zeitzonenänderung und bei jedem Prozessstart werden alle offenen
  Erinnerungen neu geplant (`ReminderCoordinator.rescheduleAll`).
* Auf dem Sperrbildschirm steht nur ein neutraler Text („Du hast eine Erinnerung.“).
* Berechtigungen: `POST_NOTIFICATIONS` (Android 13+, Laufzeitabfrage), `USE_EXACT_ALARM` (Android 13+) bzw.
  `SCHEDULE_EXACT_ALARM` (bis Android 12L, in den Systemeinstellungen), `RECEIVE_BOOT_COMPLETED`.
  Die Einstellungen zeigen den Zustand und führen zu den passenden Systemseiten (inkl. Akku-Optimierung).
* **Play-Store-Hinweis:** `USE_EXACT_ALARM` ist bei Google Play nur für Wecker-/Kalender-Apps zulässig. Für
  die Weitergabe als APK ist das unerheblich; für eine Veröffentlichung im Play Store muss das geprüft werden.
* Nicht umgesetzt: erneutes Anzeigen einer schon angezeigten, unbestätigten Erinnerung nach Neustart.

---

## 7. Datenschutz und Sicherheit

* **Kein `INTERNET`-Recht** in den eigenen Manifesten; keine Konten, kein Tracking, keine Cloud.
  (Bibliotheken können eigene Berechtigungen mitbringen, z. B. die Biometrie-Bibliothek – siehe „Merged Manifest“.)
* **Backup:** `allowBackup` ist an, aber die Cloud-Sicherung ist ausgeschlossen. Nur die vom Nutzer
  gestartete direkte Geräteübertragung (Kabel/Nähe, Android 12+) nimmt Datenbank, Dateien und Einstellungen mit.
* **Datenbank nicht verschlüsselt.** Sie liegt im privaten App-Speicher. Verschlüsselung (z. B. SQLCipher)
  ist **nicht** umgesetzt. Auf Geräten mit Root oder in unverschlüsselten Geräte-Backups wäre sie lesbar.
* **App-Sperre:** optional über Fingerabdruck/Gesicht/Geräte-PIN des Betriebssystems; die App speichert
  keine Geheimnisse. Nach 60 s im Hintergrund wird neu gesperrt; danach startet die App wieder auf „Heute“.
  Bei aktiver Sperre ist die Vorschau in der App-Übersicht und das Screenshotten blockiert (`FLAG_SECURE`),
  Widgets zeigen keine Inhalte, Benachrichtigungen auf dem Sperrbildschirm sind neutral.
  Ist auf dem Gerät keine Sperre mehr eingerichtet, bietet der Sperrbildschirm an, die App-Sperre abzuschalten
  (sonst käme man nicht mehr hinein).
* **Export:** JSON-Datei, vom Nutzer an einen selbst gewählten Ort gespeichert; **ohne** Anhangsdateien, nicht verschlüsselt.
* **Alle Daten löschen** entfernt Datenbank, Anhänge, Einstellungen, Entwürfe und geplante Alarme.
* Der Papierkorb leert sich **nicht automatisch**; Notizen bleiben dort, bis der Nutzer sie endgültig löscht.

---

## 8. Fehlerbehebung beim ersten Build

* **„Unresolved reference: Icons.Rounded.X“** – ein Symbolname existiert in der installierten
  `material-icons-extended`-Version nicht. Durch ein vorhandenes Symbol ersetzen (z. B. `Icons.Rounded.Edit`).
  Am ehesten betroffen: `EditNote`, `Checklist`, `AutoStories`, `TaskAlt`, `StickyNote2`.
* **Gradle/AGP-Versionen:** Passt Studio nicht zu AGP 8.13, entweder Studio aktualisieren oder in
  `gradle/libs.versions.toml` `agp` auf die von Studio empfohlene Version setzen (Kotlin/KSP/Compose-Plugin bleiben gleich).
* **KSP-Fehler bei Room/Hilt:** *Build ▸ Clean Project*, dann neu bauen; Room schreibt das Schema nach
  `core/database/schemas` – diese Dateien bitte mit ins Git einchecken (Grundlage für spätere Migrationen).
* **Compose-Compiler:** Er kommt über das Plugin `org.jetbrains.kotlin.plugin.compose` (gleiche Version wie Kotlin); ein
  separates `composeOptions` ist nicht nötig.
* Meldungen über **unbenutzte Importe oder Parameter** sind nur Warnungen.

---

## 9. Bekannte Einschränkungen (Zusammenfassung)

Nicht umgesetzt: Phasen 2–4 (Routinen, KI, Statistiken, Community, Partner, Backend/Sync) · KI-Zerlegung von
Aufgaben · Fokus-Übungen/Atemhilfen · erweiterte SOS-Hilfe · Tagebuch-Audio, -Kalender, -Anhänge ·
JSON-Import · Anhänge im Export · Datenbankverschlüsselung · mehrere Wochentage und Ausnahmen in Serien ·
Wochenansicht mit Stundenraster · Drag-and-drop (stattdessen Auf/Ab-Knöpfe) · Notizsperre pro Notiz ·
live laufender Widget-Countdown · erneutes Anzeigen unbestätigter Erinnerungen nach Neustart · Wiederbeleben
von Erinnerungen beim Wiederherstellen einer Notiz aus dem Papierkorb · automatisches Leeren des Papierkorbs ·
`WorkManager` (ungenutzt).

Weitere Eigenheiten: Eine Serie wird immer als Ganzes bearbeitet. Beim ICS-Import werden Termine mit bereits
bekannter UID übersprungen. Die Oberfläche ist (noch) nur auf Deutsch.

---

## 10. Nächste Schritte

1. Erster Build und Behebung eventueller Compilerfehler (Abschnitt 8).
2. `./gradlew testDebugUnitTest` und `connectedDebugAndroidTest` ausführen; Ergebnisse prüfen.
3. Auf einem echten Gerät die Erinnerungen (inkl. Doze/Akku-Sparmodus, Neustart) und Widgets testen.
4. Erst danach Phase 2 (Fokus-Übungen, SOS ausbauen, Routinen, KI-Assistent ohne Diagnosen) planen.
