#!/bin/bash
# TCL Smart Comfort: read-only device check for the TCL 55P8L (Google TV).
#
# Runs on the Mac and talks to the TV over wireless adb. It ONLY reads: getprop, settings get/list,
# dumpsys, ls, sha256sum, logcat -d, adb pull, and tcl_tv_display calls to methods whose name starts
# with get/is and whose arguments are all ints, taken from the TV's own decompiled jar, never guessed.
# No settings put, no install, no set* transaction, no reboot, no logcat -c.
#
# Usage:  TV=<ip:port> tools/tv-probe.sh <step>
#   info      device, firmware, services, SELinux, time zone, standby, HDMI/CEC, jar hash; pulls the jar
#   codes     decompile com.tcl.tv.display.jar with jadx and list the transaction codes
#   read      call the allow-listed getters (backlight, low blue ray, picture mode, HDR, ...)
#   eyecare   before/after test: you toggle Eye Care in the TV menu, the script diffs everything
#   all       info + codes + read
# Output lands in ./probe-out/ (report.txt plus raw files).
#
# Needs: adb (brew install --cask android-platform-tools); jadx for `codes` (brew install jadx).
# Written for macOS's bash 3.2 and BSD sed/grep.
set -u
OUT=${OUT:-probe-out}
mkdir -p "$OUT"
REPORT="$OUT/report.txt"
JAR=/system_ext/framework/com.tcl.tv.display.jar
SVC=tcl_tv_display

die() { echo "error: $*" >&2; exit 1; }
log() { echo "$*" | tee -a "$REPORT"; }
section() { log ""; log "===== $* ====="; }

[ -n "${TV:-}" ] || die "set TV=<ip:port> (Wireless debugging screen on the TV), e.g. TV=192.168.1.50:37123"
command -v adb >/dev/null || die "adb not found: brew install --cask android-platform-tools"
adb connect "$TV" >/dev/null 2>&1
[ "$(adb -s "$TV" get-state 2>/dev/null)" = "device" ] || die "TV $TV not connected (pair first: adb pair <ip>:<pairing-port>)"

# One remote command line, run by the TV's shell (so pipes and quotes inside the string work there).
s() { adb -s "$TV" shell "$1" 2>&1 | tr -d '\r'; }
run() { log "\$ $1"; s "$1" | tee -a "$REPORT"; }

info() {
  section "device $(date '+%Y-%m-%d %H:%M:%S %Z')"
  for p in ro.product.model ro.product.name ro.product.device ro.product.manufacturer ro.board.platform \
           ro.build.version.release ro.build.version.sdk ro.build.version.security_patch \
           ro.build.display.id ro.build.fingerprint ro.tcl.version ro.software.version_id; do
    log "$p = $(s "getprop $p")"
  done
  section "TCL services"
  run 'service list | grep -i -E "tcl|tv|pq|display"'
  run "service check $SVC"
  section "display jar"
  run "ls -lZ $JAR"
  run "sha256sum $JAR"
  adb -s "$TV" pull "$JAR" "$OUT/com.tcl.tv.display.jar" >/dev/null 2>&1 && log "pulled -> $OUT/com.tcl.tv.display.jar"
  section "SELinux: who may look up $SVC"
  run "getenforce"
  CTX="/system/etc/selinux/plat_service_contexts /system_ext/etc/selinux/system_ext_service_contexts /vendor/etc/selinux/vendor_service_contexts /product/etc/selinux/product_service_contexts"
  T=$(s "cat $CTX 2>/dev/null" | awk -v n="$SVC" '$1==n {print $2}' | head -1)
  log "service_contexts: $SVC -> ${T:-<not found>}"
  if [ -n "$T" ]; then
    TT=$(echo "$T" | cut -d: -f3)
    # The type's attributes (e.g. app_api_service = ordinary apps may find it) and explicit rules.
    run "cat /system/etc/selinux/*.cil /system_ext/etc/selinux/*.cil /vendor/etc/selinux/*.cil /product/etc/selinux/*.cil /odm/etc/selinux/*.cil 2>/dev/null | grep -w $TT | head -40"
  fi
  section "packages that may host or front the service"
  run 'pm list packages -f | grep -i -E "tcl|tvsettings|android.tv.settings|pq"'
  SET_APK=$(s "pm path com.android.tv.settings" | head -1 | sed 's/^package://')
  if [ -n "$SET_APK" ]; then
    adb -s "$TV" pull "$SET_APK" "$OUT/TvSettings.apk" >/dev/null 2>&1 && log "pulled -> $OUT/TvSettings.apk ($SET_APK)"
  fi
  section "time"
  run "date"
  for k in "global time_zone" "global auto_time_zone" "global auto_time"; do log "$k = $(s "settings get $k")"; done
  log "persist.sys.timezone = $(s 'getprop persist.sys.timezone')"
  section "power / standby"
  run 'dumpsys power | grep -E "mWakefulness=|mIsPowered|mStayOn"'
  for k in "system tcl_sys_network_standby_enabled" "secure attentive_timeout" "system tcl_app_settings_autoshopmode_enabled"; do
    log "$k = $(s "settings get $k")"
  done
  section "inputs / HDMI / CEC"
  run 'dumpsys tv_input | head -80'
  run 'dumpsys hdmi_control | head -60'
  section "background limits TCL may apply to apps"
  run 'dumpsys deviceidle | head -15'
  run 'getprop | grep -i -E "autostart|auto_start|whitelist|bg_restrict|lowmem|lmk" | head -30'
}

codes() {
  command -v jadx >/dev/null || die "jadx not found: brew install jadx"
  [ -f "$OUT/com.tcl.tv.display.jar" ] || die "run 'info' first (it pulls the jar)"
  rm -rf "$OUT/jadx-display"
  jadx -q -d "$OUT/jadx-display" "$OUT/com.tcl.tv.display.jar" >/dev/null 2>&1
  IF=$(grep -rl "interface ITvDisplayManagerService" "$OUT/jadx-display" | head -1)
  [ -n "$IF" ] || die "ITvDisplayManagerService not found in the jar"
  # codes.tsv: code<TAB>name, by code
  grep -o -E "TRANSACTION_[A-Za-z0-9_]+ = [0-9]+" "$IF" \
    | awk '{sub(/^TRANSACTION_/, "", $1); print $3 "\t" $1}' | sort -n -u > "$OUT/codes.tsv"
  # signatures.tsv: name<TAB>return type<TAB>params, from the interface declarations
  grep -E "^[[:space:]]+[A-Za-z0-9_.<>]+(\[\])? [A-Za-z0-9_]+\([^)]*\) throws RemoteException;" "$IF" \
    | awk '{ line=$0; sub(/^[ \t]+/, "", line); ret=line; sub(/ .*/, "", ret);
             name=line; sub(/^[^ ]+ /, "", name); sub(/\(.*/, "", name);
             par=line; sub(/^[^(]*\(/, "", par); sub(/\).*/, "", par);
             print name "\t" ret "\t" par }' | sort -u > "$OUT/signatures.tsv"
  section "transaction codes: $(wc -l < "$OUT/codes.tsv" | tr -d ' ') methods (all in $OUT/codes.tsv)"
  grep -i -E "backlight|blue|eye|hdr|picturemode|brightness|source|game|allm|vrr" "$OUT/codes.tsv" | tee -a "$REPORT"
  section "signatures of those methods"
  grep -i -E "backlight|blue|eye|hdr|picturemode" "$OUT/signatures.tsv" | tee -a "$REPORT"
  if [ -f "$OUT/TvSettings.apk" ]; then
    section "TvSettings strings for eye care / blue light"
    unzip -p "$OUT/TvSettings.apk" resources.arsc 2>/dev/null | strings | grep -i -E "eye ?care|eye health|blue ?light|low ?blue" | sort -u | head -40 | tee -a "$REPORT"
  fi
}

# Call one getter by NAME: only get*/is*, only int arguments (all passed as 0 = window 0 /
# current mode, as the menu and both reference projects do), only int/boolean results.
getter() {
  name=$1
  case "$name" in get*|is*) ;; *) echo "refused: $name is not a getter"; return 1;; esac
  code=$(awk -F'\t' -v n="$name" '$2==n {print $1; exit}' "$OUT/codes.tsv")
  ret=$(awk -F'\t' -v n="$name" '$1==n {print $2; exit}' "$OUT/signatures.tsv")
  params=$(awk -F'\t' -v n="$name" '$1==n {print $3; exit}' "$OUT/signatures.tsv")
  [ -n "$code" ] && [ -n "$ret" ] || { echo "$name: not in this firmware"; return 0; }
  case "$ret" in int|boolean) ;; *) echo "$name: returns $ret, skipped"; return 0;; esac
  args=""
  if [ -n "$params" ]; then
    rest=$params
    while [ -n "$rest" ]; do
      p=${rest%%,*}; [ "$p" = "$rest" ] && rest="" || rest=${rest#*,}
      p=$(echo "$p" | sed 's/^ *//')
      case "$p" in "int "*) args="$args i32 0" ;; *) echo "$name($params): non-int argument, skipped"; return 0 ;; esac
    done
  fi
  r=$(s "service call $SVC $code$args")
  w=$(echo "$r" | sed -n 's/.*Parcel(\([^)]*\)).*/\1/p' | grep -o -E "[0-9a-f]{8}" | head -2 | tr '\n' ' ')
  st=${w%% *}; val=$(echo "$w" | cut -d' ' -f2)
  if [ "$st" = "00000000" ] && [ -n "$val" ]; then
    echo "$name [tx $code] = $((16#$val))"
  else
    echo "$name [tx $code]: $r"
  fi
}

GETTERS="getBacklight getLowBlueRay getPictureMode getHdrMode getBrightness getContrast getColorTemperature getDynamicContrast"
read_all() {
  [ -f "$OUT/codes.tsv" ] || die "run 'codes' first"
  section "getters (int arguments 0)"
  extra=$(cut -f2 "$OUT/codes.tsv" | grep -E "^(get|is)" | grep -i -E "eye|blue|hdr|game|allm|source|input" || true)
  for g in $GETTERS $extra; do getter "$g"; done | sort -u | tee -a "$REPORT"
}

snapshot() {
  d="$OUT/snap-$1"; mkdir -p "$d"
  for t in system secure global; do s "settings list $t" | sort > "$d/settings-$t.txt"; done
  # every all-int getter of this firmware, so a change outside LowBlueRay shows too
  for g in $(cut -f2 "$OUT/codes.tsv" | grep -E "^(get|is)"); do getter "$g"; done > "$d/getters.txt" 2>&1
}

eyecare() {
  [ -f "$OUT/codes.tsv" ] || die "run 'codes' first"
  T0=$(s "date '+%m-%d %H:%M:%S.000'")
  echo "Snapshot 1 (Eye Care as it is now) ..."; snapshot before
  echo
  echo ">>> Switch Eye Care in the TV's picture menu to the OTHER state (nothing else), then press Enter here."
  read -r _
  echo "Snapshot 2 ..."; snapshot after
  section "Eye Care toggle: what changed"
  for f in settings-system settings-secure settings-global getters; do
    log "--- $f"; diff "$OUT/snap-before/$f.txt" "$OUT/snap-after/$f.txt" | tee -a "$REPORT"
  done
  section "logcat since snapshot 1 (filtered)"
  adb -s "$TV" logcat -d -T "$T0" 2>/dev/null | tr -d '\r' \
    | grep -i -E "blue|eye|pq|tcl_tv_display|TvDisplay|picture" | tail -80 | tee -a "$REPORT"
  echo
  echo "Done. Please set Eye Care back in the menu to how it was."
}

case "${1:-}" in
  info) info ;;
  codes) codes ;;
  read) read_all ;;
  eyecare) eyecare ;;
  all) info; codes; read_all ;;
  *) sed -n '2,19p' "$0"; exit 1 ;;
esac
echo; echo "Report: $REPORT"
