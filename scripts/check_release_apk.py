#!/usr/bin/env python3
"""Reject a release APK that embeds private reference artwork.

This checks packaged media bytes, including when Android renames resources. It is
an artifact guard for this repository, not a rights audit or history scanner.
"""

import hashlib
import pathlib
import sys
import zipfile


ROOT = pathlib.Path(__file__).resolve().parents[1]
MEDIA = {'.jpg', '.jpeg', '.png', '.webp'}
PRIVATE = ROOT / 'app/src/debug/res'
NATURE = ROOT / 'app/src/main/res/drawable-nodpi'
REQUIRED = {'wallpaper_alpine.jpg', 'wallpaper_aurora.jpg', 'wallpaper_como.jpg'}
REQUIRED_LICENSES = {
    'Apache-2.0.txt',
    'GPL-3.0.txt',
    'SPAKE2-LGPL-3.0.txt',
    'Montserrat-OFL.txt',
    'Shizuku-API-MIT.txt',
    'Bouncy-Castle-LICENSE.md',
}
REQUIRED_NOTICES = {'THIRD-PARTY-NOTICES.md', 'Carpe-TV-NOTICE.txt'}
# Fingerprints of private development reference assets. The public tree does
# not contain their source files, so keep the denylist independent of PRIVATE.
KNOWN_PRIVATE_HASHES = {
    'f36c9a503fd395b198fb514cbbc59b09c40c441bf273848b5fff4ce51769f61e',
    '0d76b2902d7525b3670478cb411ad76d0c00fa9fa411c685e29ac1c094f35e4a',
    '4c636723d68b67a832ea03a495428d47c966a7a06d3843d745c5e9cdc8ba0f54',
    '85d14d339681a569269317071ac143045533ed702dc471570d78353fc01264b2',
    'd5005613e65bb1f6d73c9bac09b4ff54c2203b406ba895d23e00f6a9206d4fd',
    '65b167fbf7b62b5c33fe90514316a54c48ff6efc1c5f8a3b0143aa85eeb1f82f',
}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def source_hashes(directory):
    return {digest(path.read_bytes()): path.name
            for path in directory.rglob('*') if path.is_file() and path.suffix.lower() in MEDIA}


def check(apk):
    private = source_hashes(PRIVATE)
    nature = {digest((NATURE / name).read_bytes()): name for name in REQUIRED}
    found_nature = set()
    errors = []
    with zipfile.ZipFile(apk) as archive:
        for name in sorted(REQUIRED_LICENSES | REQUIRED_NOTICES):
            source = ROOT / 'app/src/main/assets/licenses' / name
            packaged = f'assets/licenses/{name}'
            if not source.is_file() or packaged not in archive.namelist():
                errors.append(f'Required license/notice missing from APK: {name}')
            elif archive.read(packaged) != source.read_bytes():
                errors.append(f'Packaged license/notice differs from source: {name}')
        for member in archive.infolist():
            if member.is_dir() or pathlib.PurePosixPath(member.filename).suffix.lower() not in MEDIA:
                continue
            fingerprint = digest(archive.read(member))
            if fingerprint in private or fingerprint in KNOWN_PRIVATE_HASHES:
                errors.append(f'Private artwork packaged as {member.filename}')
            if fingerprint in nature:
                found_nature.add(nature[fingerprint])
    for name in sorted(REQUIRED - found_nature):
        errors.append(f'Redistributable default photo missing from APK: {name}')
    if errors:
        print('\n'.join(errors), file=sys.stderr)
        return 1
    print(f'Release artifact checks passed: {len(found_nature)} nature photos, '
          f'{len(REQUIRED_LICENSES)} required license texts, '
          f'{len(REQUIRED_NOTICES)} notices, no known private artwork.')
    return 0


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('Usage: check_release_apk.py PATH_TO_APK')
    raise SystemExit(check(pathlib.Path(sys.argv[1])))
