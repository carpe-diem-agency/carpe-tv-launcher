#!/usr/bin/env python3
"""Small repository policy check; not a replacement for a dedicated secret scanner."""
import pathlib
import hashlib
import tarfile
import re
import json
import subprocess
import sys
import xml.etree.ElementTree as ET
from check_release_apk import KNOWN_PRIVATE_HASHES

root = pathlib.Path(__file__).resolve().parents[1]
if (root / '.git').exists():
    paths = subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard','-z'], cwd=root).decode().split('\0')
else:
    excluded = {'.gradle', '.git', 'build'}
    paths = [str(p.relative_to(root)) for p in root.rglob('*')
             if p.is_file() and not any(part in excluded for part in p.relative_to(root).parts)]
errors = []
secret_patterns = [r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----', r'gh[pousr]_[A-Za-z0-9]{30,}', r'github_pat_[A-Za-z0-9_]{40,}']
for name in filter(None, paths):
    p = root/name
    if p.suffix.lower() in ('.apk','.aab','.jks','.keystore','.p12','.pem') or p.name in ('local.properties','.env','signing.properties','key.properties'):
        errors.append('Forbidden local/build file: '+name)
    if p.is_file() and p.suffix.lower() in ('.jpg','.jpeg','.png','.webp'):
        if hashlib.sha256(p.read_bytes()).hexdigest() in KNOWN_PRIVATE_HASHES:
            errors.append('Private reference image in source: '+name)
    if p.is_file() and p.suffix.lower() in ('.java','.xml','.gradle','.properties','.yml','.yaml','.md','.sh','.py','.txt'):
        text = p.read_text(errors='replace')
        if any(re.search(pattern,text) for pattern in secret_patterns): errors.append('Possible credential in '+name)
        if p.suffix in ('.java','.xml') and re.search(r'192\.168\.\d{1,3}\.\d{1,3}',text): errors.append('Device address in '+name)
manifest = (root/'app/src/main/AndroidManifest.xml').read_text()
for permission in ('RECORD_AUDIO','CAMERA','ACCESS_FINE_LOCATION','QUERY_ALL_PACKAGES'):
    if 'android.permission.'+permission+'"' in manifest: errors.append('Unexpected baseline permission: '+permission)
expected_permissions = {'android.permission.INTERNET', 'android.permission.REQUEST_INSTALL_PACKAGES', 'android.permission.SCHEDULE_EXACT_ALARM'}
actual_permissions = {e.attrib.get('{http://schemas.android.com/apk/res/android}name')
                      for e in ET.fromstring(manifest).findall('uses-permission')
                      if e.attrib.get('{http://schemas.android.com/tools}node') != 'remove'}
if actual_permissions != expected_permissions: errors.append('Unexpected launcher permission set')
english = [e.attrib['name'] for e in ET.parse(root/'app/src/main/res/values/strings.xml').getroot().findall('string')]
ukrainian = [e.attrib['name'] for e in ET.parse(root/'app/src/main/res/values-uk/strings.xml').getroot().findall('string')]
if len(set(english)) != len(english) or len(set(ukrainian)) != len(ukrainian) or set(english) != set(ukrainian):
    errors.append('English/Ukrainian string coverage differs or has duplicate names')
if not (root/'app/src/main/assets/licenses/Montserrat-OFL.txt').exists(): errors.append('Missing font license')
if (root/'LICENSE').read_bytes() != (root/'app/src/main/assets/licenses/Apache-2.0.txt').read_bytes():
    errors.append('Repository license differs from bundled Apache-2.0 text')
if (root/'NOTICE').read_bytes() != (root/'app/src/main/assets/licenses/Carpe-TV-NOTICE.txt').read_bytes():
    errors.append('Carpe TV notice differs from bundled APK notice')
release_workflow = (root/'.github/workflows/release.yml').read_text()
install_page = (root/'docs/index.html').read_text()
gradle = (root/'app/build.gradle').read_text()
asset_name = 'carpe-tv.apk'
stable_link = 'https://github.com/carpe-diem-agency/carpe-tv-launcher/releases/latest/download/' + asset_name
feed = 'https://api.github.com/repos/carpe-diem-agency/carpe-tv-launcher/releases/latest'
if install_page.count('href="' + stable_link + '"') != 2:
    errors.append('Install page must link both languages to the stable release APK')
if 'cp app/build/outputs/apk/release/app-release.apk dist/' + asset_name not in release_workflow:
    errors.append('Release workflow APK asset differs from the install page')
if 'dist/' + asset_name + ' dist/SHA256SUMS dist/update.json' not in release_workflow:
    errors.append('Release workflow must attach the APK, checksum and updater metadata')
pages_workflow = (root/'.github/workflows/pages.yml').read_text()
for media in ('site-ambient.webp', 'brand-icon.png', 'manrope.ttf', 'OFL-Manrope.txt', 'home-preview.jpg', 'google-tv-home-test-stick.png', 'settings-preview.jpg', 'interface-preview.mp4'):
    if not (root/'docs/assets'/media).is_file() or (media != 'OFL-Manrope.txt' and 'assets/'+media not in install_page):
        errors.append('Install page preview missing: '+media)
    if 'cp docs/assets/'+media+' site/assets/'+media not in pages_workflow:
        errors.append('Pages workflow does not publish preview: '+media)
if 'cp docs/robots.txt docs/sitemap.xml site/' not in pages_workflow:
    errors.append('Pages workflow is missing discovery files')
if '<html lang="uk" data-release-ready="false">' not in install_page or '"downloadUrl"' in install_page:
    errors.append('Source install page must default to preview without download metadata')
if 'python3 scripts/prepare_pages_release.py site/index.html' not in pages_workflow:
    errors.append('Pages workflow must enable the APK action only after release verification')
if 'carpe-diem-agency.github.io/carpe-tv-launcher/' not in install_page:
    errors.append('Install page canonical URL differs from the new repository')
match = re.search(r'<script type="application/ld\+json">(.*?)</script>', install_page, re.S)
if not match:
    errors.append('Install page is missing structured data')
else:
    try: json.loads(match.group(1))
    except json.JSONDecodeError: errors.append('Install page structured data is invalid JSON')
source_name = 'spake2-java-2.2.1-with-submodule.tar.gz'
source_archive = root/'compliance/source'/source_name
source_checksum = root/'compliance/source/SHA256SUMS'
if not source_archive.is_file() or not source_checksum.is_file():
    errors.append('Missing SPAKE2 source bundle or checksum')
else:
    expected = source_checksum.read_text().strip().split()
    if len(expected) != 2 or expected[1] != source_name or hashlib.sha256(source_archive.read_bytes()).hexdigest() != expected[0]:
        errors.append('SPAKE2 source bundle checksum differs')
    else:
        with tarfile.open(source_archive, 'r:gz') as archive:
            names = set(archive.getnames())
            prefix = 'spake2-java-2.2.1/'
            for required in ('LICENSE', 'android/build.gradle', 'android/src/main/cpp/spake2-c/LICENSE',
                             'android/src/main/cpp/spake2-c/spake2.c', 'android/src/main/cpp/spake2_jni.cpp'):
                if prefix + required not in names:
                    errors.append('SPAKE2 source bundle missing '+required)
if 'dist/' + source_name not in release_workflow:
    errors.append('Draft release must attach the SPAKE2 source bundle')
if feed not in gradle:
    errors.append('Release updater feed differs from the documented GitHub repository')
if errors:
    print('\n'.join(errors)); sys.exit(1)
print('Repository policy checks passed (no dedicated secret-scanner guarantee).')
