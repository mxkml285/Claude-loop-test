# Geräteprüfung am TCL 55P8L (nur lesend)

Ziel: am echten Fernseher klären, bevor eine Zeile App-Code entsteht,

1. ob es den TCL-Dienst `tcl_tv_display` gibt und welche Transaktionscodes deine Firmware hat,
2. ob **Eye Care** wirklich `LowBlueRay` ist (oder etwas anderes),
3. ob eine **normale App** den Dienst aufrufen darf (SELinux, Rechteprüfung im Dienst),
4. Zeitzone, Standby-Modus, HDMI/CEC-Zustand als Ausgangslage.

Das Skript `tools/tv-probe.sh` führt am TV nur lesende Befehle aus (`getprop`, `settings get/list`,
`dumpsys`, `sha256sum`, `adb pull`, `logcat -d` und `service call` ausschließlich auf `get*`/`is*`-Methoden,
deren Codes und Signaturen aus der Firmware deines TV stammen). Kein `settings put`, keine Installation,
kein `set*`-Aufruf.

## 1. Wireless ADB am TV aktivieren

1. **Einstellungen → System → Info** (bei manchen TCL-Menüs: *Über*), den Eintrag
   **„Android TV OS-Build“** auswählen und **7× OK** drücken, bis „Du bist jetzt Entwickler“ erscheint.
2. **Einstellungen → System → Entwickleroptionen** öffnen.
3. **Wireless-Debugging** (Kabelloses Debugging) einschalten. USB-Debugging ist nicht nötig.
4. In *Wireless-Debugging* stehen **IP-Adresse und Port** (z. B. `192.168.178.50:37123`), das ist die
   *Verbindungs*adresse. Notieren.
5. **„Gerät mit Kopplungscode koppeln“** öffnen: zeigt einen **6-stelligen Code** und eine *zweite*
   Adresse mit *Kopplungs*port. Den Dialog offen lassen.

Mac und TV müssen im selben Netz sein. Achtung im Menü: der Wireless-Debugging-Bildschirm hat einen
Schalter *Deaktiviert*; ihn zu wählen trennt alle Verbindungen.

## 2. Mac vorbereiten

```bash
brew install --cask android-platform-tools   # adb
brew install jadx                            # liest die Transaktionscodes aus der TCL-Firmware
```

Koppeln (einmalig, mit Code und **Kopplungs**port aus Schritt 1.5):

```bash
adb pair 192.168.178.50:<kopplungsport>      # fragt nach dem 6-stelligen Code
adb connect 192.168.178.50:<verbindungsport> # Port aus Schritt 1.4
adb devices                                  # muss "... device" zeigen
```

Der Verbindungsport ändert sich, wenn Wireless-Debugging neu eingeschaltet wird. `adb mdns services`
zeigt den aktuellen.

## 3. Claude Code lokal auf dem Mac mit dem TV arbeiten lassen

Diese Cloud-Sitzung erreicht dein Heimnetz nicht. Zwei Wege:

- **A – Claude Code direkt auf dem Mac** (am einfachsten):
  ```bash
  npm install -g @anthropic-ai/claude-code      # falls noch nicht installiert (oder die Claude Desktop-App)
  git clone https://github.com/mxkml285/claude-loop-test.git
  cd claude-loop-test && git checkout claude/tcl-google-tv-scheduled-display-svubfq
  claude
  ```
  Dann z. B.: „Führe `tcl-smart-comfort/tools/tv-probe.sh all` mit `TV=<ip:port>` aus und werte
  `probe-out/report.txt` aus.“ Claude Code fragt vor jedem Befehl nach Erlaubnis.
- **B – Diese Sitzung an den Mac anbinden:** im Repo-Ordner auf dem Mac `claude remote-control`
  starten (oder in der Claude Desktop-App: Einstellungen → Claude Code → Remote Control, Ordner
  freigeben). Danach kann diese Sitzung die Befehle auf dem Mac ausführen.

## 4. Prüfung ausführen

```bash
cd tcl-smart-comfort
export TV=192.168.178.50:<verbindungsport>
tools/tv-probe.sh info      # Gerät, Firmware, Dienste, SELinux, Zeitzone, Standby, HDMI/CEC; lädt die jar
tools/tv-probe.sh codes     # Transaktionscodes aus deiner Firmware (jadx)
tools/tv-probe.sh read      # liest Backlight, LowBlueRay, Bildmodus, HDR …
tools/tv-probe.sh eyecare   # Vorher/Nachher: du schaltest Eye Care im Menü um, das Skript vergleicht alles
```

Bei `eyecare` schaltest **du** Eye Care im Bildmenü einmal um (der einzige Eingriff, und er geschieht
über die normale Fernbedienung). Das Skript vergleicht danach alle Getter des TCL-Dienstes, alle
Android-Einstellungen und das Log. Danach Eye Care wieder zurückstellen.

Ergebnis: `probe-out/report.txt` (dazu Rohdaten). Die Ordner `probe-out/` enthalten Gerätedaten und
werden nicht eingecheckt (`.gitignore`).

## 5. Was die Ergebnisse bedeuten

| Frage | Woran man es sieht |
|---|---|
| Eye Care = LowBlueRay? | `eyecare`: in `getters` ändert sich **nur** `getLowBlueRay` (0↔1). Ändert sich etwas anderes oder eine `settings`-Zeile, ist Eye Care etwas anderes, und wir nehmen das. |
| Darf eine normale App den Dienst finden? (SELinux) | `info`: `service_contexts` nennt den Typ; in den `.cil`-Zeilen gehört er zu `app_api_service` (oder es gibt eine `allow untrusted_app … find`-Regel). Fehlt das, verweigert Android den Zugriff. |
| Prüft der Dienst selbst Berechtigungen? | im dekompilierten Dienst nach `enforceCallingPermission`/`checkCallingPermission`/`getCallingUid` suchen (das macht Claude nach `codes` an der jar bzw. der Dienst-APK). |
| Firmware-Stand für die SHA-256-Sperre | `info`: `sha256sum` der jar. Die App wird genau auf diesen Hash festgelegt. |
| Zeitzone | `time_zone` sollte `Europe/Berlin` sein; die App rechnet ohnehin fest mit `Europe/Berlin`. |

**Endgültig** bestätigt die App-Berechtigung nur eine echte App. Dafür schlage ich danach eine winzige
Prüf-App vor (ein Knopf, der nur `getBacklight`/`getLowBlueRay` liest und anzeigt, kein Internet,
keine Rechte). Das ist eine Installation, also ein Schreibvorgang: nur mit deiner Freigabe, und mit
`adb uninstall` restlos entfernbar.
