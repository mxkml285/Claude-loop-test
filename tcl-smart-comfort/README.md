# TCL Smart Comfort

Zeitgesteuerte Helligkeit und Eye Care für den TCL 55P8L (Google TV), als eigenständige App auf dem
Fernseher. Kein Root, kein Bootloader-Unlock, keine Firmware- oder Factory-Menu-Änderung.

**Stand: App 1.0 läuft auf dem TV** (installiert am 26.11.2026). Prüfergebnisse:
[docs/PRUEFERGEBNIS.md](docs/PRUEFERGEBNIS.md).

## Plan
| Zeit | Helligkeit | Eye Care |
|---|---|---|
| 07–17 Uhr | 60 | aus |
| 17–18 Uhr | 20 | aus |
| 18–07 Uhr | 20 | an |

Immer in `Europe/Berlin`. Nur bei Quelle **Google-TV-Apps** (TCL-ID 21) und **Tuner** (1/2). Bei HDMI 1–4
(PS5 = HDMI 2), AV oder unbekannter Quelle ändert die App nichts. Soundbar/CEC/Audio fasst sie nie an.

## So arbeitet die App
- Jede Minute ein nicht weckender Takt (`AlarmManager.RTC`). Er weckt den TV also nie aus dem Standby.
- Angewendet wird nur bei einem **Ereignis**: Planwechsel (07/17/18 Uhr), Quellen- oder Bildmoduswechsel,
  Aufwachen aus Standby, Neustart, „Jetzt anwenden“. Dazwischen bleibt eine Änderung mit der Fernbedienung stehen.
- Werte werden mit `act = EXEC` gesetzt, also nicht in die Bild-Voreinstellungen des TV gespeichert.
- **Firmware-Sperre:** Nur bei Fingerprint `…UTT2.250416.001/AU02…` und SHA-256 der TCL-jar wie geprüft.
  Nach einem TCL-Update zeigt die App „Gesperrt“ und tut nichts, bis `tools/tv-probe.sh` neu gelaufen ist.
- Bedienung in der App: **Pausieren/Einschalten**, **Jetzt anwenden**, Status und letzte Aktionen.

## Bauen und installieren (Mac)
```bash
app/build.sh                                   # testet den Plan, baut app/build/smartcomfort.apk
adb connect <tv-ip>:5555
adb install -r app/build/smartcomfort.apk
adb uninstall de.smartcomfort.app              # restlos entfernen
```

**Einmalig nach der ersten Installation (TCL-Autostart):** TCLs „Sicherheitsschutz“ (TGuard) verhindert, dass
die App nach einem Neustart oder nach dem Beenden wieder anläuft. Dann fallen die Takte und die Home-Taste aus.
Diese Firmware zeigt dafür keinen Schalter, deshalb wird der Autostart per ADB erlaubt, nur für diese App:
```bash
adb shell cmd appops set de.smartcomfort.app AUTO_START allow
adb shell cmd appops set de.smartcomfort.app ASSOC_START allow
# rückgängig: dieselben Befehle mit "default" statt "allow" (oder App deinstallieren)
```
Startseite per Home-Taste: Einstellungen → System → Bedienungshilfen → „Smart Comfort Startseite“ an.
Derselbe Dienst belegt zwei Fernbedienungstasten neu: **Videotaste** (weiß, unter Netflix, Code 4057) →
MediaPlayer IBO, **TV-Taste** (schwarz, unter Home, Code 4001) → HDMI 2 (PS5). Ausschalten macht Home-,
Video- und TV-Taste wieder zu den normalen TCL-/Google-TV-Tasten.

**Ab App 1.3** (ebenfalls über diesen Dienst):
- **123-Taste** (Code 4056): kurz = Eye Care an/aus mit Hinweis oben im Bild (beim Spielen gesperrt),
  lang (0,6 s) = Control Center. Der Nummernblock ist über diese Taste nicht mehr erreichbar.
- **Control Center:** Helligkeit 0–100, Eye Care, Automatik, Sleep-Timer 15/30/60/90 Min, „Bildschirm aus,
  Ton läuft weiter“ (startet TCLs eigenes „Nur Ton“). Zeigt die echten Werte des Fernsehers. Manuelle
  Änderungen bleiben bis zum nächsten Planwechsel, Quellenwechsel oder Aufwachen.
- **PS5 automatisch:** PS5 an → HDMI 2 (nach 2 s stabil), PS5 Ruhemodus/aus → Startseite (nach 6 s stabil),
  danach gilt wieder der Zeitplan. Erkannt über Androids HDMI-Eingangsstatus, es werden keine CEC-Befehle gesendet.

Der Sleep-Timer braucht einmalig eine Freigabe (ohne sie zeigt das Control Center „keine Berechtigung“):
```bash
adb shell pm grant de.smartcomfort.app android.permission.WRITE_SECURE_SETTINGS
# rückgängig: adb shell pm revoke de.smartcomfort.app android.permission.WRITE_SECURE_SETTINGS
```
Braucht JDK 17 (`brew install openjdk@17`) und das Android-SDK mit `platforms;android-34` und
`build-tools;34.0.0`.

| Pfad | Inhalt |
|---|---|
| `app/` | die TV-App (Quellcode, Plan-Tests, Build-Skript, Banner) |
| `probe-app/` | Prüf-App, die den App-Zugriff auf den TCL-Dienst bestätigt hat |
| `tools/tv-probe.sh` | Prüfskript für den Mac, ändert am TV nichts |
| `docs/GERAETEPRUEFUNG.md` | Wireless ADB einrichten, Prüfablauf |
| `docs/PRUEFERGEBNIS.md` | Ergebnisse am echten TV |
