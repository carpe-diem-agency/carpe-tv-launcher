"""Read the Android identity embedded in an APK."""
import os
from pathlib import Path
import re
import subprocess


def find_build_tools(*names):
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if sdk:
        candidates = [
            path for path in (Path(sdk) / 'build-tools').glob('*')
            if all((path / name).is_file() for name in names)
        ]
        if candidates:
            return max(candidates, key=lambda path: tuple(map(int, re.findall(r'\d+', path.name))))
    raise ValueError(f'Android SDK Build Tools with {" and ".join(names)} are required')


def read_apk_metadata(aapt2, apk):
    try:
        badging = subprocess.check_output(
            [str(aapt2), 'dump', 'badging', str(apk)], text=True,
            stderr=subprocess.PIPE, timeout=30,
        )
    except (OSError, subprocess.SubprocessError) as error:
        raise ValueError('Could not read APK badging with aapt2') from error
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",
                        badging, re.MULTILINE)
    minimum = re.search(r"^minSdkVersion:'(\d+)'", badging, re.MULTILINE)
    if not package or not minimum:
        raise ValueError('Could not read APK identity/version/minSdk')
    metadata = dict(applicationId=package[1], versionCode=int(package[2]),
                    versionName=package[3], minSdk=int(minimum[1]))
    debuggable = 'application-debuggable' in badging.splitlines()
    return metadata, debuggable
