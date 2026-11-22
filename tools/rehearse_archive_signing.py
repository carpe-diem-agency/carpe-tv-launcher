#!/usr/bin/env python3
"""Rehearse signing an archived Carpe TV candidate with temporary local keys."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys
from tempfile import TemporaryDirectory
from zipfile import ZipFile


REPOSITORY = 'carpe-diem-agency/carpe-tv-launcher'
APP_ID = 'agency.carpediem.tv'
NATIVE_SOURCE = 'spake2-java-2.2.1-with-submodule.tar.gz'
ARCHIVED_SCRIPTS = (
    'apk_metadata.py', 'check_release_apk.py', 'make_update_manifest.py',
    'prepare_pages_release.py', 'verify_pages_release.py',
)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def read_candidate(folder, trusted_source):
    metadata = json.loads((folder / 'candidate.json').read_text())
    version = metadata['version']
    code = metadata['version_code']
    require(re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9a-zA-Z.-]+)?', version)
            and type(code) is int and code > 0 and metadata['application_id'] == APP_ID
            and metadata['signing'] == 'unsigned' and metadata['published'] is False,
            'Archive does not describe an unsigned Carpe TV release')
    apk = folder / f'Carpe-TV-{version}-{code}-unsigned.apk'
    source = folder / f'Carpe-TV-{version}-source.zip'
    names = {apk.name, source.name, 'update-check.json'}
    hashes = metadata['checksums']
    sums = [tuple(line.split()) for line in (folder / 'SHA256SUMS').read_text().splitlines()]
    require(set(hashes) == names and sorted(sums) == sorted((hashes[name], name) for name in names),
            'Archive checksum lists disagree')
    require(hashes[source.name] == trusted_source, 'Source checksum does not match the trusted fingerprint')
    require(all(digest(folder / name) == hashes[name] for name in names), 'An archived file failed SHA-256')
    return metadata, apk, source


def extract_release_inputs(source, root, version, commit):
    prefix = f'Carpe-TV-{version}/'
    fixed = {f'scripts/{script}' for script in ARCHIVED_SCRIPTS}
    fixed.update({'docs/index.html', 'compliance/source/SHA256SUMS', f'compliance/source/{NATIVE_SOURCE}'})
    asset_dirs = ('app/src/main/assets/licenses/', 'app/src/main/res/drawable-nodpi/', 'app/src/main/res/raw/')
    with ZipFile(source) as archive:
        require(archive.comment == commit.encode('ascii') and archive.testzip() is None,
                'Source ZIP identity or CRC check failed')
        names = archive.namelist()
        require(len(names) == len(set(names)), 'Source ZIP has duplicate paths')
        for name in names:
            relative = name.removeprefix(prefix)
            if not name.startswith(prefix) or name.endswith('/') or not (relative in fixed or relative.startswith(asset_dirs)):
                continue
            path = Path(relative)
            require(not path.is_absolute() and '..' not in path.parts, 'Unsafe source path')
            destination = root / path
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(archive.read(name))


def zip_contents(path):
    with ZipFile(path) as archive:
        names = archive.namelist()
        require(archive.testzip() is None and len(names) == len(set(names)), 'Invalid APK ZIP contents')
        return {name: hashlib.sha256(archive.read(name)).digest() for name in names}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('candidate', type=Path)
    parser.add_argument('--source-sha256', required=True, help='Trusted source ZIP hash from outside the candidate folder')
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, required=True)
    args = parser.parse_args()
    trusted_source = args.source_sha256.lower()
    require(re.fullmatch(r'[0-9a-f]{64}', trusted_source), 'Supply the trusted source ZIP SHA-256')
    metadata, unsigned, source = read_candidate(args.candidate.resolve(), trusted_source)
    version, code, commit = metadata['version'], metadata['version_code'], metadata['source_commit']
    require(re.fullmatch(r'[0-9a-f]{40}', commit), 'Invalid archived source commit')
    sdk = args.sdk.resolve()
    build_tools = sdk / 'build-tools/36.0.0'
    java = args.java_home.resolve()
    signer = build_tools / 'apksigner'
    env = {key: value for key, value in os.environ.items() if not key.startswith(('ANDROID_KEY', 'ANDROID_STORE', 'RELEASE_'))}
    env.update(JAVA_HOME=str(java), ANDROID_HOME=str(sdk), ANDROID_SDK_ROOT=str(sdk), PYTHONDONTWRITEBYTECODE='1')
    tag = f'v{version}'
    apk_url = f'https://github.com/{REPOSITORY}/releases/download/{tag}/carpe-tv.apk'

    def run(*command, key_env=env, check=True):
        return subprocess.run(command, env=key_env, capture_output=True, text=True, timeout=60, check=check)

    with TemporaryDirectory(prefix='carpe-archive-rehearsal-') as directory:
        stage = Path(directory)
        extracted = stage / 'source'
        extract_release_inputs(source, extracted, version, commit)

        def archived(script, *arguments, check=True):
            return run(sys.executable, extracted / 'scripts' / script, *arguments, check=check)

        def make_key(label):
            key = stage / f'{label}.p12'
            cert = stage / f'{label}.der'
            run(java / 'bin/keytool', '-genkeypair', '-alias', label, '-keyalg', 'RSA',
                '-keysize', '2048', '-validity', '3', '-dname', 'CN=Carpe TV disposable rehearsal',
                '-storetype', 'PKCS12', '-keystore', key, '-storepass:env', 'CARPE_TEST_PASSWORD', key_env=key_env)
            run(java / 'bin/keytool', '-exportcert', '-alias', label, '-keystore', key,
                '-storepass:env', 'CARPE_TEST_PASSWORD', '-file', cert, key_env=key_env)
            return key, digest(cert)

        def sign(key, alias):
            run(signer, 'sign', '--ks', key, '--ks-key-alias', alias, '--ks-pass', 'env:CARPE_TEST_PASSWORD',
                '--v1-signing-enabled', 'false', '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'true',
                '--v4-signing-enabled', 'false', '--out', apk, unsigned, key_env=key_env)
            run(build_tools / 'zipalign', '-c', '-P', '16', '4', apk)
            require(zip_contents(apk) == original_entries, 'Signing changed ZIP payload contents')

        def make_assets():
            (dist / 'SHA256SUMS').write_text(''.join(f'{digest(path)}  {path.name}\n' for path in (apk, native)))
            manifest = dist / 'update.json'
            archived('make_update_manifest.py', apk, '--expected-application-id', APP_ID,
                     '--apk-url', apk_url, '--output', manifest)
            identity = json.loads(manifest.read_text())
            require(identity['versionCode'] == code and identity['versionName'] == version, 'Signed version differs from archive')
            return manifest

        def verify(pin, check=True):
            return archived('verify_pages_release.py', dist, '--repository', REPOSITORY,
                            '--tag', tag, '--expected-cert-sha256', pin, check=check)

        key_env = dict(env, CARPE_TEST_PASSWORD=secrets.token_urlsafe(24))
        key, expected = make_key('temporary')
        backup = stage / 'backup.p12'
        shutil.copyfile(key, backup)
        key.unlink()
        restored = stage / 'restored.p12'
        shutil.copyfile(backup, restored)
        backup.unlink()

        dist = stage / 'bundle'
        dist.mkdir()
        apk = dist / 'carpe-tv.apk'
        native = dist / NATIVE_SOURCE
        shutil.copyfile(extracted / 'compliance/source' / NATIVE_SOURCE, native)
        expected_native = (extracted / 'compliance/source/SHA256SUMS').read_text().split()[0]
        require(digest(native) == expected_native, 'Archived SPAKE2 source failed its checksum')
        run(build_tools / 'zipalign', '-c', '-P', '16', '4', unsigned)
        original_entries = zip_contents(unsigned)
        sign(restored, 'temporary')
        restored.unlink()
        schemes = run(signer, 'verify', '--verbose', apk).stdout
        require(all(re.search(rf'Verified using v{v} scheme .*: true', schemes) for v in (2, 3)),
                'Signed APK is missing v2 or v3')
        archived('check_release_apk.py', apk)
        signed_hash = digest(apk)
        manifest = make_assets()
        verify(expected)
        page = extracted / 'docs/index.html'
        archived('prepare_pages_release.py', page, manifest)
        require(page.read_text().count(f'href="{apk_url}"') == 2, 'Archived page links did not select the signed release')

        alternate, different = make_key('alternate')
        require(different != expected, 'Temporary test keys unexpectedly share the same certificate')
        apk.unlink()
        sign(alternate, 'alternate')
        make_assets()
        verify(different)
        rejected = verify(expected, check=False)
        require(rejected.returncode != 0 and 'differs from the pinned release identity' in rejected.stderr,
                'A valid APK with the alternate certificate was not rejected by the original pin')

    require(not stage.exists(), 'Temporary keys or APKs remain')
    print(json.dumps(dict(version=version, version_code=code, source_commit=commit,
                          unsigned_sha256=digest(unsigned), temporary_signed_sha256=signed_hash,
                          matching_zip_entries=len(original_entries), v2_v3='PASS',
                          archived_assets_and_pages='PASS', alternate_signer_rejected=True,
                          temporary_keys_and_apks_removed=True, production_signing='UNRUN',
                          device_installation='UNRUN', publication='UNRUN'), indent=2))


if __name__ == '__main__':
    try:
        main()
    except subprocess.CalledProcessError as error:
        raise SystemExit((error.stderr or error.stdout or 'Archived release command failed').strip()) from None
    except (ValueError, OSError) as error:
        raise SystemExit(str(error)) from None
