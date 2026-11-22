#!/usr/bin/env python3
"""Verify stable release assets before enabling the Pages APK download."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

from apk_metadata import find_build_tools, read_apk_metadata

parser = argparse.ArgumentParser()
parser.add_argument('assets', type=Path)
parser.add_argument('--repository', required=True)
parser.add_argument('--tag', required=True)
parser.add_argument('--expected-cert-sha256', required=True)
args = parser.parse_args()
expected_certificate = args.expected_cert_sha256.replace(':', '').lower()
if not re.fullmatch(r'[0-9a-f]{64}', expected_certificate):
    parser.error('Expected release certificate must be a SHA-256 fingerprint')

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

try:
    tools = find_build_tools('aapt2', 'apksigner')
except ValueError as error:
    parser.error(str(error))
try:
    signature = subprocess.run(
        [str(tools / 'apksigner'), 'verify', '--print-certs', str(apk)],
        capture_output=True, text=True, timeout=30,
    )
except (OSError, subprocess.SubprocessError):
    parser.error('Could not run apksigner to verify the release APK')
if signature.returncode != 0:
    parser.error('Release APK signature verification failed')
certificates = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F:]+)\s*$',
                          signature.stdout, re.MULTILINE)
if len(certificates) != 1 or certificates[0].replace(':', '').lower() != expected_certificate:
    parser.error('APK signing certificate differs from the pinned release identity')
try:
    identity, debuggable = read_apk_metadata(tools / 'aapt2', apk)
except ValueError as error:
    parser.error(str(error))
if debuggable:
    parser.error('Stable release APK is debuggable')
for key, value in identity.items():
    if type(data[key]) is not type(value) or data[key] != value:
        parser.error(f'update.json {key} does not match the APK manifest')

print(f'Pages release assets verified: {args.tag}, {data["versionName"]}')
