#!/usr/bin/env bash
set -euo pipefail

# The focus map and safe areas are designed for a landscape TV canvas.
size="$(adb shell wm size)"
case "$size" in
  *1920x1080*|*3840x2160*) ;;
  *) echo "Expected a landscape TV display, got: $size" >&2; exit 1 ;;
esac

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

runner='agency.carpediem.tv.test/agency.carpediem.tv.prototype.NavigationProbe'
for probe in feedback homefocus languagestate; do
  echo "Running Android TV probe: ${probe}"
  # MainActivity is singleTask. Each probe needs a fresh foreground Activity,
  # otherwise the next instrumentation can inherit the previous probe's page.
  adb shell am force-stop agency.carpediem.tv
  adb shell am start -W -n agency.carpediem.tv/.MainActivity >/dev/null
  result="$(adb shell am instrument -w -e "${probe}" yes "${runner}")"
  printf '%s\n' "${result}"
  if ! grep -q 'INSTRUMENTATION_CODE: -1' <<< "${result}" || grep -q 'INSTRUMENTATION_RESULT: failure=' <<< "${result}"; then
    echo "Probe failed: ${probe}" >&2
    echo 'Recent Android crash and focus diagnostics:' >&2
    adb shell dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp|mObscuringWindow' >&2 || true
    adb logcat -d -t 3000 -v brief | grep -E -A 35 -B 3 \
      'FATAL EXCEPTION|Process: agency.carpediem.tv|InputDispatcher|Focus event|E AndroidRuntime' | tail -180 >&2 || true
    exit 1
  fi
done
