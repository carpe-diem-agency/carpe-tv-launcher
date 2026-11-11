#!/usr/bin/env python3
"""Verify stable release assets before enabling the Pages APK download."""
import argparse
import hashlib
import json
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('assets', type=Path)
parser.add_argument('--repository', required=True)
parser.add_argument('--tag', required=True)
args = parser.parse_args()

apk = args.assets / 'carpe-tv.apk'
source = args.assets / 'spake2-java-2.2.1-with-submodule.tar.gz'
checksums = args.assets / 'SHA256SUMS'
manifest = args.assets / 'update.json'
for item in (apk, source, checksums, manifest):
    if not item.is_file() or item.stat().st_size == 0:
        parser.error(f'Missing or empty release asset: {item.name}')

expected_names = {apk.name, source.name}
recorded = {}
for line in checksums.read_text().splitlines():
    pieces = line.split(maxsplit=1)
    if len(pieces) != 2 or len(pieces[0]) != 64:
        parser.error('Invalid SHA256SUMS entry')
    name = pieces[1].lstrip('*')
    if name not in expected_names or name in recorded:
        parser.error(f'Unexpected or repeated checksum entry: {name}')
    recorded[name] = pieces[0].lower()
if set(recorded) != expected_names:
    parser.error('SHA256SUMS does not cover both release files')

for item in (apk, source):
    with item.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    if digest != recorded[item.name]:
        parser.error(f'SHA-256 mismatch: {item.name}')

data = json.loads(manifest.read_text())
expected_url = (f'https://github.com/{args.repository}/releases/download/'
                f'{args.tag}/{apk.name}')
required = {
    'schemaVersion': 1,
    'channel': 'stable',
    'applicationId': 'agency.carpediem.tv',
    'apkUrl': expected_url,
    'byteSize': apk.stat().st_size,
    'sha256': recorded[apk.name],
}
for key, value in required.items():
    if data.get(key) != value:
        parser.error(f'update.json {key} does not match the release asset')
if not isinstance(data.get('versionCode'), int) or data['versionCode'] < 1:
    parser.error('update.json has no valid versionCode')
if not isinstance(data.get('versionName'), str) or not data['versionName']:
    parser.error('update.json has no valid versionName')
if args.tag != 'v' + data['versionName']:
    parser.error('Release tag does not match update.json versionName')
if not isinstance(data.get('minSdk'), int) or data['minSdk'] < 26:
    parser.error('update.json has an unexpected minSdk')
print(f'Pages release assets verified: {args.tag}, {data["versionName"]}')
