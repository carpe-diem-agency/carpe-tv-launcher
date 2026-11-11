#!/usr/bin/env python3
"""Regression checks for the public download gate."""
import hashlib
import json
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


class PagesReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.assets = Path(self.temp.name)
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

    def verify(self):
        return subprocess.run([sys.executable, str(SCRIPT), str(self.assets),
                               '--repository', REPOSITORY, '--tag', TAG],
                              capture_output=True, text=True).returncode

    def test_matching_assets(self):
        self.assertEqual(self.verify(), 0)

    def test_tampered_apk(self):
        self.apk.write_bytes(b'tampered APK')
        self.assertNotEqual(self.verify(), 0)

    def test_wrong_manifest_url(self):
        self.data['apkUrl'] = 'https://example.com/carpe-tv.apk'
        self.save_manifest()
        self.assertNotEqual(self.verify(), 0)

    def test_version_not_matching_tag(self):
        self.data['versionName'] = '1.0.1'
        self.save_manifest()
        self.assertNotEqual(self.verify(), 0)

    def test_missing_source_archive(self):
        self.source.unlink()
        self.assertNotEqual(self.verify(), 0)

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
        self.assertIn('"downloadUrl"', enabled)
        self.assertNotEqual(subprocess.run([sys.executable, str(PAGE_SCRIPT), str(page), str(self.manifest)],
                                           capture_output=True).returncode, 0)


if __name__ == '__main__':
    unittest.main()
