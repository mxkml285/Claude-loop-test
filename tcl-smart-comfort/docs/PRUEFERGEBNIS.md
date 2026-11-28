# Prüfergebnis TCL 55P8L (26.11.2026, nur lesend)

Verbindung: ADB über WLAN, Port 5555 (nach Freigabe am TV). Keine schreibenden Befehle ausgeführt.

## Firmware
| Eigenschaft | Wert |
|---|---|
| Build | `tcl9618-user 14 UTT2.250416.001 AU02 release-keys` |
| Android / SDK | 14 / 34, Sicherheitspatch 2025-12-05 |
| Software | `V8-T653T01-LF1V643` (Menü zeigt `V8-0012T01-LF1V643.003175`) |
| Plattform | MediaTek `merak`, `MT9653_Overseas`, Gerät `G08` |
| SHA-256 `com.tcl.tv.display.jar` | `c14f5ea2d1374ab5a6abeae0c6bbfa15a6a0129a12aad7908332c62fc44a4600` |

## Dienst `tcl_tv_display`
- Vorhanden (`com.tcl.tv.display.ITvDisplayManagerService`), natives Programm
  `/system_ext/bin/tcl_tv_display_service`, läuft als root in Domäne `tcl_display_service`.
- 197 Methoden. Relevante Codes dieser Firmware:

| Methode | Code | Signatur |
|---|---|---|
| `getBacklight` | 101 | `int ()` |
| `setBacklight` | 100 | `boolean (int type, int value, int act)` |
| `getLowBlueRay` | 109 | `int (int windowId)` |
| `setLowBlueRay` | 108 | `boolean (int windowId, int value, int act)` |
| `getPictureMode` | 27 | `int ()` |

## SELinux (Enforcing)
- `allow untrusted_app tcl_tv_display_service (service_manager (find))`: normale Apps dürfen den Dienst finden.
- `allow untrusted_app tcl_display_service (binder (call transfer))`: normale Apps dürfen ihn aufrufen.
- **Prüf-App bestätigt Lesen (18:54 Uhr):** `de.smartcomfort.probe` (`probe-app/`), Domäne `untrusted_app`,
  uid 10128, keine Berechtigungen: `ServiceManager.getService` OK, `getBacklight = 20`, `getLowBlueRay = 1`.
  Auch TCLs Bibliothek `com.tcl.tv.display` lädt per `uses-library`.
- **Schreibtest bestätigt (18:56 Uhr, freigegeben):** Die App setzte `setBacklight(type 0, value 21, act 0)` →
  `true`, `getBacklight` las 21. Danach `setBacklight(0, 20, 0)` → `true`, final 20 (per Shell nachgeprüft).
  Der Dienst nimmt Setzbefehle einer normalen App an. `act 0` wendet nur an und speichert nicht.
  `setLowBlueRay` wurde nicht als App getestet, hat aber denselben Zugriffsweg.

## Folgerung für die App
Eine normale, eigenständige App (ohne Root, ohne Mac) kann Backlight und Eye Care über `tcl_tv_display`
steuern. Codes gelten nur für diese Firmware → App prüft den SHA-256 der jar und tut sonst nichts.

## Gelesene Werte (18:47 Uhr)
Backlight 20, LowBlueRay 1, Bildmodus 60, Helligkeit 50, Kontrast 100, HDR-Modus 1, ALLM 0.

## Eye Care
**Bestätigt: Eye Care = `LowBlueRay`.** Vorher/Nachher-Test (aus → an, 18:50 Uhr):
- Von 93 Gettern ändert sich nur `getLowBlueRay` (0 → 1); keine Zeile in `settings system/secure/global`.
- Log: Das Menü (`com.android.tv.settings`) ruft `setLowBlueRay(window 0, value 0|1, act 2)` auf. Danach
  setzt der Dienst intern `setGammaDirectly` (gleicher Prozess, kein eigener Aufruf nötig).
- Einschränkung: Das Ausschalten (18:49:01) lag eine Sekunde nach Beginn der Vorher-Aufnahme. Die Getter
  sind sauber; die Einstellungslisten könnten teilweise noch den An-Zustand enthalten. Das Log zeigt aber
  keinen weiteren Setzbefehl.
- Das Menü nutzt `act = 2` (anwenden + speichern). Die App nutzt bewusst `act = 0` (nur anwenden), damit
  die gespeicherten Bildwerte, auch die der PS5, unverändert bleiben.

## Sonstiges
- Zeitzone `Europe/Berlin`, automatische Zeit an.
- PS5 per CEC an HDMI 2 erkannt; HDMI/CEC wird von der App nicht angefasst.
- `tcl_app_settings_autoshopmode_enabled = 1` (Shop-Modus-Automatik, nur notiert).

## App 1.0 auf dem TV (26.11.2026, 19:03 Uhr)
- Installiert als `de.smartcomfort.app`, Minuten-Takt als exakter `RTC`-Alarm angemeldet (`USE_EXACT_ALARM`).
- Quelle: `Settings.Secure tcl_sys_tv_input_current_device_id` ist für die App lesbar (21 = Apps).
- 19:03:39 meldete die PS5 per CEC kurz Quelle 16 (HDMI 2): Die App zeigte „HDMI 2: keine Änderung“, danach
  wieder „Google-TV-Apps · schon richtig“ (Plan 20/an = TV-Zustand).
- Noch am TV zu bestätigen: Tuner-ID (1 oder 2) beim normalen Fernsehen, Verhalten nach Standby und nach
  Neustart (TGuard), erster Planwechsel am 27.11. um 07:00 Uhr.
- Fernsehen läuft bei Maxi über eine IPTV-App (Quelle 21), ein Tuner-Test ist nicht nötig.
- Standby-Test 19:07 Uhr: Die App meldete nach dem Einschalten „Aufwachen · Google-TV-Apps · schon richtig“.
  Sie lief also nach dem Standby ohne Mac weiter. Die Helligkeit stand vor dem Standby noch auf 20 (die
  Abweichung auf 30 kam nicht zustande), deshalb ist das Zurückstellen nach dem Aufwachen noch nicht belegt.
  Nach dem Standby war ADB (Port 5555) aus: Wahrscheinlich startet der TV beim Einschalten neu.

## TCL-Autostart (TGuard, 26.11.2026, 19:44 Uhr)
- `TclAppBoot` (Teil von `com.tcl.guard`, `ro.feature.tguard_app_boot=true`) blockierte das Binden der
  Bedienungshilfe nach einem Update und die Zustellung der Takt-Alarme bei beendetem Prozess, Grund:
  App-Op `AUTO_START` bzw. `ASSOC_START` = `ignore`. Das betrifft alle nachinstallierten Apps (auch IBO).
- Die Firmware zeigt keinen Schalter dafür (Genehmigungsschild nur Audio/Standort, App-Info ohne Eintrag).
- Mit Maxis Freigabe gesetzt: `cmd appops set de.smartcomfort.app AUTO_START allow` und `ASSOC_START allow`.
  Bleibt bei `install -r` erhalten. Danach verband sich der Home-Dienst nach dem Update von selbst wieder.

## Fernbedienungstasten (26.11.2026, 20:46 Uhr, App 1.2)
- Ermittelt über das TV-Log (`InputReader`/`ViewRootImpl TIDD`), Fernbedienung `BT_RC833A_B5`:
  - Weiße Taste mit blauem Videosymbol (unter Netflix): TCL-Keycode **4057** (scanCode 240), öffnet normal
    `com.tcl.ui_mediaCenter`.
  - Schwarze Taste mit Fernsehsymbol (unter Home): TCL-Keycode **4001**, öffnet normal die rote TV-App.
- Neu belegt im vorhandenen `HomeKeyService` (Bedienungshilfe, Key-Filter), gleiche Aufrufe wie die Kacheln
  (`Launch.app` / `Launch.ps5`). Down, Wiederholung und Up werden verschluckt, die Aktion startet beim Drücken.
- Test mit echter Fernbedienung: Videotaste → IBO nach ca. 0,3 s, kein MediaCenter. TV-Taste in IBO →
  Sitzung `TvPassThroughService/HW16` nach ca. 50 ms, keine TV-App-Startseite/EPG. Videotaste auf der PS5 →
  zurück zu IBO. Danach meldete Smart Comfort „Quellenwechsel · Google-TV-Apps · schon richtig“.

## Phase 5: PS5-Automatik, Control Center, 123-Taste (App 1.3)

- **HDMI-Status:** `TvInputManager.getInputState` für `com.tcl.tvinput/.TvPassThroughService/HW16` liefert
  0 = PS5 an, 1 = Ruhemodus. TCL schaltet beim Einschalten selbst nach ca. 10 s auf HDMI 2, beim Ruhemodus
  aber nicht zurück; das übernimmt jetzt die App. Getestet: an → HDMI 2, Ruhemodus → Startseite.
- **Sleep-Timer:** `Settings.Global tcl_app_power_off_time_delay` (ms, -1 = aus); TCL setzt
  `tcl_app_power_off_time_expire` selbst. Braucht WRITE_SECURE_SETTINGS (per `pm grant`). Getestet.
- **Bildschirm aus mit Ton:** TCLs `TPowerManager.setBackLightState` verlangt eine Systemsignatur, nicht nutzbar.
  Stattdessen startet die App TCLs eigenen Dialog: Dienst `com.android.tv.settings/com.tcl.settings.ShowWindowService`,
  Action `com.tcl.settings.SHOW_WINDOW`, Extra `Type=audio_only`. Getestet.
- **123-Taste:** Code 4056 (scanCode 240). Tastenwiederholungen kommen erst hinter dem Bedienungshilfe-Filter,
  daher wird der lange Druck per Timer ab dem ersten Drücken gemessen. Mit `adb shell input keyevent`
  simulierte Tasten erreichen den Filter nicht; getestet mit der echten Fernbedienung.

## Red Mode (App 1.4)

- **TCL-Dienst:** `setRGBMode` (68) `(windowId 0, type 0, mode, act 0)`, `getRGBMode` (69); Modi aus TCLs
  Bildmenü: 0 aus, 1 nur Rot, 2 nur Grün, 3 nur Blau. Intensität über `setContrast` (31) / `getContrast` (32),
  Ausgangswert 100. Weißabgleich-Gain (57/58) wäre nur ±15 Schritte und verlangt eingeschalteten
  benutzerdefinierten Weißabgleich, daher nicht genutzt.
- **Android-Farbfilter** (SurfaceFlinger/ColorDisplayService) verworfen: braucht Systemrechte bzw. wirkt nicht
  auf die Videoebene.
- **Rote Taste:** `KEYCODE_PROG_RED` 183 (scanCode 398), läuft durch den Bedienungshilfe-Filter und wird dort
  vollständig abgefangen.
- Getestet: an/aus per Taste und Control Center, Intensität, Wiederherstellung (RGB 0, Kontrast 100).
