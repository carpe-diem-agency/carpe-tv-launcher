#!/usr/bin/env python3
"""Enable the Pages download UI only after release assets are verified."""
import json
import pathlib
import re
import sys

if len(sys.argv) != 3:
    raise SystemExit('usage: prepare_pages_release.py PAGE VERIFIED_UPDATE_JSON')
page = pathlib.Path(sys.argv[1])
manifest = json.loads(pathlib.Path(sys.argv[2]).read_text())
version = manifest.get('versionName')
if not isinstance(version, str) or not re.fullmatch(r'[0-9]+(?:\.[0-9]+){2}', version):
    raise SystemExit('invalid release versionName')
html = page.read_text()
old = '<html lang="uk" data-release-ready="false">'
if html.count(old) != 1:
    raise SystemExit('missing preview page marker')
html = html.replace(old, '<html lang="uk" data-release-ready="true">')
metadata_end = ',"url":"https://carpe-diem-agency.github.io/carpe-tv-launcher/"}'
download = ',"downloadUrl":"https://github.com/carpe-diem-agency/carpe-tv-launcher/releases/latest/download/carpe-tv.apk"'
if html.count(metadata_end) != 1 or download in html:
    raise SystemExit('unexpected structured download metadata')
html = html.replace(metadata_end, download + metadata_end)
old_description = 'Ознайомся з проєктом і підготовкою релізу.'
if html.count(old_description) != 1:
    raise SystemExit('unexpected preview description')
html = html.replace(old_description, 'Завантаж APK та встанови за три кроки.')
release_version = re.compile(r'(<span class="download-version" data-release-version>)v[^<]+(</span>)')
html, count = release_version.subn(lambda match: match.group(1) + 'v' + version + match.group(2), html)
if count != 2:
    raise SystemExit('expected Ukrainian and English release version labels')
page.write_text(html)
