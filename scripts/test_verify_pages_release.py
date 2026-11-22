#!/usr/bin/env python3
"""Regression checks for the public download gate."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('verify_pages_release.py')
PAGE_SCRIPT = Path(__file__).with_name('prepare_pages_release.py')
PAGE_SOURCE = SCRIPT.parent.parent / 'docs/index.html'
REPOSITORY = 'carpe-diem-agency/carpe-tv-launcher'
TAG = 'v1.0.0'
CERTIFICATE = 'a1' * 32


class PagesReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.assets = Path(self.temp.name)
        self.tools = self.assets / 'sdk' / 'build-tools' / '36.0.0'
        self.tools.mkdir(parents=True)
        self.badging = (
            "package: name='agency.carpediem.tv' versionCode='1' versionName='1.0.0'\n"
            "minSdkVersion:'26'\n"
        )
        self.stub_tool('aapt2', f'assert sys.argv[1:] == {["dump", "badging", str(self.assets / "carpe-tv.apk")]!r}\n'
                       f'print({self.badging!r}, end="")')
        self.stub_tool('apksigner',
                       f'assert sys.argv[1:] == {["verify", "--print-certs", str(self.assets / "carpe-tv.apk")]!r}\n'
                       f'print("Signer #1 certificate SHA-256 digest: {CERTIFICATE}")')
        self.apk = self.assets / 'carpe-tv.apk'
        self.source = self.assets / 'spake2-java-2.2.1-with-submodule.tar.gz'
        self.apk.write_bytes(b'signed test APK')
        self.source.write_bytes(b'source archive')
        apk_hash = hashlib.sha256(self.apk.read_bytes()).hexdigest()
        source_hash = hashlib.sha256(self.source.read_bytes()).hexdigest()
        (self.assets / 'SHA256SUMS').write_text(
            f'{apk_hash}  {self.apk.name}\n{source_hash}  {self.source.name}\n')
        self.manifest = self.assets / 'update.json'
        self.data = dict(schemaVersion=1, channel='stable',
                         applicationId='agency.carpediem.tv', versionCode=1,
                         versionName='1.0.0', minSdk=26,
                         apkUrl=f'https://github.com/{REPOSITORY}/releases/download/{TAG}/carpe-tv.apk',
                         byteSize=self.apk.stat().st_size, sha256=apk_hash)
        self.save_manifest()

    def save_manifest(self):
        self.manifest.write_text(json.dumps(self.data))

    def stub_tool(self, name, code):
        script = self.tools / name
        script.write_text(f'#!/usr/bin/env python3\nimport sys\n{code}\n')
        script.chmod(0o755)

    def verify(self, certificate=CERTIFICATE):
        return subprocess.run([sys.executable, str(SCRIPT), str(self.assets),
                               '--repository', REPOSITORY, '--tag', TAG,
                               '--expected-cert-sha256', certificate],
                              capture_output=True, text=True,
                              env=dict(os.environ, ANDROID_HOME=str(self.assets / 'sdk')))

    def test_matching_assets(self):
        result = self.verify()
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_tampered_apk(self):
        self.apk.write_bytes(b'tampered APK')
        self.assertIn('SHA-256 mismatch', self.verify().stderr)

    def test_wrong_manifest_url(self):
        self.data['apkUrl'] = 'https://example.com/carpe-tv.apk'
        self.save_manifest()
        self.assertIn('apkUrl does not match', self.verify().stderr)

    def test_version_not_matching_tag(self):
        self.data['versionName'] = '1.0.1'
        self.save_manifest()
        self.assertIn('Release tag does not match', self.verify().stderr)

    def test_missing_source_archive(self):
        self.source.unlink()
        self.assertIn('Missing or empty release asset', self.verify().stderr)

    def test_invalid_signature(self):
        self.stub_tool('apksigner', 'sys.exit(1)')
        self.assertIn('signature verification failed', self.verify().stderr)

    def test_valid_signature_from_other_identity(self):
        self.assertIn('differs from the pinned release identity', self.verify('b2' * 32).stderr)

    def test_additional_signer_is_rejected(self):
        self.stub_tool('apksigner',
                       f'print("Signer #1 certificate SHA-256 digest: {CERTIFICATE}")\n'
                       f'print("Signer #2 certificate SHA-256 digest: {"b2" * 32}")')
        self.assertIn('differs from the pinned release identity', self.verify().stderr)

    def test_missing_or_invalid_pin_is_rejected(self):
        self.assertIn('must be a SHA-256 fingerprint', self.verify('').stderr)
        self.assertIn('must be a SHA-256 fingerprint', self.verify('invalid').stderr)

    def test_colon_delimited_uppercase_pin_is_supported(self):
        result = self.verify(':'.join(['A1'] * 32))
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_mismatched_embedded_metadata(self):
        for key, apk_value in (
            ('applicationId', self.badging.replace('agency.carpediem.tv', 'agency.example.tv')),
            ('versionCode', self.badging.replace("versionCode='1'", "versionCode='2'")),
            ('versionName', self.badging.replace('1.0.0', '1.0.1')),
            ('minSdk', self.badging.replace('26', '27')),
        ):
            with self.subTest(key=key):
                self.stub_tool('aapt2', f'print({apk_value!r}, end="")')
                self.assertIn(f'{key} does not match the APK manifest', self.verify().stderr)

    def test_debuggable_apk(self):
        self.stub_tool('aapt2', f'print({self.badging!r} + "application-debuggable")')
        self.assertIn('Stable release APK is debuggable', self.verify().stderr)

    def test_page_stays_preview_until_explicitly_enabled(self):
        source = PAGE_SOURCE.read_text()
        self.assertIn('data-release-ready="false"', source)
        self.assertNotIn('"downloadUrl"', source)
        page = self.assets / 'index.html'
        page.write_text(source)
        result = subprocess.run([sys.executable, str(PAGE_SCRIPT), str(page), str(self.manifest)],
                                capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        enabled = page.read_text()
        self.assertIn('data-release-ready="true"', enabled)
        release = f'https://github.com/{REPOSITORY}/releases/tag/{TAG}'
        self.assertEqual(enabled.count(f'href="{self.data["apkUrl"]}"'), 2)
        self.assertEqual(enabled.count(f'href="{release}"'), 4)
        self.assertEqual(enabled.count(f'<span class="download-version" data-release-version>{TAG}</span>'), 2)
        self.assertIn(f'"downloadUrl":"{self.data["apkUrl"]}"', enabled)
        self.assertNotIn('/releases/latest', enabled)
        self.assertNotEqual(subprocess.run([sys.executable, str(PAGE_SCRIPT), str(page), str(self.manifest)],
                                           capture_output=True).returncode, 0)

    def test_page_rejects_an_apk_link_for_another_release(self):
        self.data['apkUrl'] = self.data['apkUrl'].replace(TAG, 'v1.0.1')
        self.save_manifest()
        source = PAGE_SOURCE.read_text()
        page = self.assets / 'index.html'
        page.write_text(source)
        result = subprocess.run([sys.executable, str(PAGE_SCRIPT), str(page), str(self.manifest)],
                                capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(page.read_text(), source)


if __name__ == '__main__':
    unittest.main()
