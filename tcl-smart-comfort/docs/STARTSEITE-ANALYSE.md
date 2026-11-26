# Phase 2: Premium-Startseite – Analyse (26.11.2026)

## Ziel-Apps (TCL 55P8L, ausgelesen per ADB)
| Kachel | Package | Start-Activity |
|---|---|---|
| MediaPlayer IBO | `com.ibostore.meplayerib4k` | `.HomeActivity` |
| PlayStation 5 | `com.tcl.tv` | `com.tcl.player.TVActivity` + HDMI-2-Aufruf (unten) |
| YouTube | `com.google.android.youtube.tv` | `com.google.android.apps.youtube.tv.activity.ShellActivity` |
| Netflix | `com.netflix.ninja` | `.MainActivity` |
| Smart Comfort | `de.smartcomfort.app` | `.MainActivity` |
| TCL Channel | `com.tcl.waterfall.overseas` | `.main.MainActivity` |

## Rote „TV“-App = PS5
- `com.tcl.tv/com.tcl.player.TVActivity` (TCL Live TV 3.15). Normaler Start (`MAIN`/`LEANBACK_LAUNCHER`) öffnet
  die **zuletzt benutzte Quelle** (`tcl_sys_tv_input_last_device_id`), aktuell 16 = HDMI 2. Nicht garantiert PS5.
- **Gezielter Aufruf (getestet 19:22, funktioniert in 57 ms, Quelle 21 → 16):**
  `ACTION_VIEW`, Daten `content://android.media.tv/passthrough/com.tcl.tvinput%2F.TvPassThroughService%2FHW16`
  (= `TvContract.buildChannelUriForPassthroughInput("com.tcl.tvinput/.TvPassThroughService/HW16")`),
  Ziel `com.tcl.tv/com.tcl.player.TVActivity`. HW16 hat beim TV das Label „Playstation5“.

## Home-Taste
- HOME-Kandidaten: `launcherx` (Priorität 2), `setupwraith/.RecoveryActivity` (1), `tv.settings FallbackHome` (-1000).
  Installierte Apps erhalten höchstens Priorität 0.
- Test 19:23: Rolle `android.app.role.HOME` auf eine Test-App gesetzt → Home-Taste ging **trotzdem** zu
  `launcherx`. Rolle sofort auf `com.google.android.tungsten.setupwraith` zurückgesetzt, Test-App entfernt.
- Folgerung: Eine eigene Startseite kann die Home-Taste nicht über die Rolle übernehmen. Möglich bleiben:
  (A) Google TV „Nur Apps“ + eigene Startseite als erste Favoritenkachel, (B) Home-Taste per eigener
  Bedienungshilfe abfangen (wie Projectivy), (C) Google-Launcher deaktivieren (nicht empfohlen).
