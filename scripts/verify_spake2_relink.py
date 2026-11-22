#!/usr/bin/env python3
"""Confirm that each packaged SPAKE2 library came from the supplied AAR."""

import argparse
from pathlib import Path
from zipfile import BadZipFile, ZipFile


ABIS = ('arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64')
MARKER = b'CARPE_SPAKE2_RELINK_PROBE_2026'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('aar', type=Path)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--modified-probe', action='store_true')
    args = parser.parse_args()

    try:
        with ZipFile(args.aar) as source, ZipFile(args.apk) as packaged:
            for archive, prefix in ((source, 'jni'), (packaged, 'lib')):
                libraries = sorted(name for name in archive.namelist()
                                   if name.startswith(prefix + '/') and name.endswith('/libspake2.so'))
                expected = sorted(f'{prefix}/{abi}/libspake2.so' for abi in ABIS)
                if libraries != expected:
                    parser.error(f'Expected exactly four supported SPAKE2 libraries in {archive.filename}')
            for abi in ABIS:
                source_name = f'jni/{abi}/libspake2.so'
                apk_name = f'lib/{abi}/libspake2.so'
                binary = packaged.read(apk_name)
                if source.read(source_name) != binary:
                    parser.error(f'APK native library differs from the supplied AAR: {apk_name}')
                if args.modified_probe and MARKER not in binary:
                    parser.error(f'Modified source marker missing from APK: {apk_name}')
                print(f'Verified source-built native library: {apk_name}')
    except (OSError, BadZipFile) as error:
        parser.error(str(error))


if __name__ == '__main__':
    main()
