#!/usr/bin/env python3
"""Generate updater metadata from the actual APK, without signing credentials."""
import argparse
import hashlib
import json
import os
import pathlib
import re
import subprocess
from urllib.parse import urlsplit

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
sdk = pathlib.Path(os.environ['ANDROID_HOME'])
tools = list((sdk/'build-tools').glob('*/aapt2'))
if not tools:
    parser.error('Android SDK Build Tools with aapt2 are required')
def version(path):
    return tuple(int(part) for part in re.findall(r'\d+', path.parent.name))
aapt = max(tools, key=version)
badging = subprocess.check_output([str(aapt), 'dump', 'badging', str(args.apk)], text=True)
package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
minimum = re.search(r"minSdkVersion:'(\d+)'", badging)
if not package or not minimum:
    parser.error('Could not read APK identity/version/minSdk')
if args.expected_application_id and package[1] != args.expected_application_id:
    parser.error(f'Expected application ID {args.expected_application_id}, found {package[1]}')
size = args.apk.stat().st_size
if not 1024 <= size <= 50 * 1024 * 1024:
    parser.error('APK is outside the supported size range')
with args.apk.open('rb') as apk_input:
    digest = hashlib.file_digest(apk_input, 'sha256').hexdigest()
metadata = dict(schemaVersion=1, channel=args.channel, applicationId=package[1],
                versionCode=int(package[2]), versionName=package[3], minSdk=int(minimum[1]),
                apkUrl=args.apk_url, byteSize=size,
                sha256=digest,
                releaseNotes=args.notes[:4000])
args.output.write_text(json.dumps(metadata, ensure_ascii=False, indent=2)+'\n')
print(f'Wrote {args.output}: {package[3]} ({package[2]})')
