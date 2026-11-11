#!/usr/bin/env python3
"""Publish an honest source preview until a stable APK is released."""
import pathlib
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: prepare_pages_preview.py PAGE")
page = pathlib.Path(sys.argv[1])
html = page.read_text()
old = '<html lang="uk" data-release-ready="true">'
if html.count(old) != 1:
    raise SystemExit("missing release-ready page marker")
html = html.replace(old, '<html lang="uk" data-release-ready="false">')
download_metadata = ',"downloadUrl":"https://github.com/carpe-diem-agency/carpe-tv-launcher/releases/latest/download/carpe-tv.apk"'
if html.count(download_metadata) != 1:
    raise SystemExit("unexpected structured download metadata")
html = html.replace(download_metadata, '')
html = html.replace('Завантаж APK та встанови за три кроки.', 'Ознайомся з проєктом і підготовкою релізу.')
page.write_text(html)
