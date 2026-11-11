#!/usr/bin/env bash
set -euo pipefail

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

runner='agency.carpediem.tv.test/agency.carpediem.tv.prototype.NavigationProbe'
for probe in feedback homefocus languagestate; do
  echo "Running Android TV probe: ${probe}"
  result="$(adb shell am instrument -w -e "${probe}" yes "${runner}")"
  printf '%s\n' "${result}"
  if ! grep -q 'INSTRUMENTATION_CODE: -1' <<< "${result}" || grep -q 'INSTRUMENTATION_RESULT: failure=' <<< "${result}"; then
    echo "Probe failed: ${probe}" >&2
    exit 1
  fi
done
