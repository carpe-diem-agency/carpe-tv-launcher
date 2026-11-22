#!/usr/bin/env python3
"""Generate updater metadata from the actual APK, without signing credentials."""
import argparse
import hashlib
import json
import pathlib
from urllib.parse import urlsplit

from apk_metadata import find_build_tools, read_apk_metadata

parser = argparse.ArgumentParser()
parser.add_argument('apk', type=pathlib.Path)
parser.add_argument('--apk-url', required=True)
parser.add_argument('--channel', choices=('stable', 'internal'), default='stable')
parser.add_argument('--notes', default='')
parser.add_argument('--output', type=pathlib.Path, required=True)
parser.add_argument('--expected-application-id', default='')
args = parser.parse_args()
url = urlsplit(args.apk_url)
if url.scheme != 'https' or not url.netloc or url.username or url.fragment:
    parser.error('Release APK URL must use HTTPS without credentials or fragments')
try:
    tools = find_build_tools('aapt2')
    identity, _ = read_apk_metadata(tools / 'aapt2', args.apk)
except ValueError as error:
    parser.error(str(error))
if args.expected_application_id and identity['applicationId'] != args.expected_application_id:
    parser.error(f'Expected application ID {args.expected_application_id}, found {identity["applicationId"]}')
size = args.apk.stat().st_size
if not 1024 <= size <= 50 * 1024 * 1024:
    parser.error('APK is outside the supported size range')
with args.apk.open('rb') as apk_input:
    digest = hashlib.file_digest(apk_input, 'sha256').hexdigest()
metadata = dict(schemaVersion=1, channel=args.channel, **identity,
                apkUrl=args.apk_url, byteSize=size,
                sha256=digest,
                releaseNotes=args.notes[:4000])
args.output.write_text(json.dumps(metadata, ensure_ascii=False, indent=2)+'\n')
print(f'Wrote {args.output}: {identity["versionName"]} ({identity["versionCode"]})')
