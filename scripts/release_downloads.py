#!/usr/bin/env python3
"""Count GitHub Release APK downloads without app-side telemetry."""

import json
import urllib.request


REPOSITORY = "carpe-diem-agency/carpe-tv-launcher"
ASSET = "carpe-tv.apk"


def main():
    url = f"https://api.github.com/repos/{REPOSITORY}/releases?per_page=100"
    total = 0
    while url:
        request = urllib.request.Request(
            url,
            headers={"Accept": "application/vnd.github+json", "User-Agent": "carpe-tv-release-metrics"},
        )
        with urllib.request.urlopen(request, timeout=15) as response:
            releases = json.load(response)
            links = response.headers.get("Link", "")
        for release in releases:
            for asset in release.get("assets", []):
                if asset.get("name") == ASSET:
                    count = int(asset.get("download_count", 0))
                    total += count
                    print(f"{release['tag_name']}: {count} APK downloads")
        url = next(
            (part.split(";", 1)[0].strip(" <>") for part in links.split(",") if 'rel="next"' in part),
            None,
        )
    print(f"Total: {total} APK downloads")
    print("Downloads are not unique users or confirmed installations.")


if __name__ == "__main__":
    main()
