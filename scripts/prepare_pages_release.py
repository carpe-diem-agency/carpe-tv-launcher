#!/usr/bin/env python3
"""Enable the Pages download UI only after release assets are verified."""
import pathlib
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: prepare_pages_release.py PAGE')
page = pathlib.Path(sys.argv[1])
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
page.write_text(html)
