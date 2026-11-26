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
Braucht JDK 17 (`brew install openjdk@17`) und das Android-SDK mit `platforms;android-34` und
`build-tools;34.0.0`.

| Pfad | Inhalt |
|---|---|
| `app/` | die TV-App (Quellcode, Plan-Tests, Build-Skript, Banner) |
| `probe-app/` | Prüf-App, die den App-Zugriff auf den TCL-Dienst bestätigt hat |
| `tools/tv-probe.sh` | Prüfskript für den Mac, ändert am TV nichts |
| `docs/GERAETEPRUEFUNG.md` | Wireless ADB einrichten, Prüfablauf |
| `docs/PRUEFERGEBNIS.md` | Ergebnisse am echten TV |
