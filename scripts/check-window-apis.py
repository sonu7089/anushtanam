"""Fail if a release APK/AAB retains deprecated system-bar setter names in DEX.

Usage: python scripts/check-window-apis.py path/to/app-release.aab
This is a conservative binary check, not a substitute for Play's own analysis or
device testing. Inlined numeric cutout constants cannot be identified this way.
"""

import argparse
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("artifact")
    args = parser.parse_args()
    forbidden = (b"setStatusBarColor", b"setNavigationBarColor")
    hits = []
    with zipfile.ZipFile(args.artifact) as archive:
        dex_files = [name for name in archive.namelist() if name.endswith(".dex")]
        if not dex_files:
            raise SystemExit("No DEX files found; supply a built APK or AAB.")
        for name in dex_files:
            data = archive.read(name)
            hits.extend(f"{name}: {symbol.decode()}" for symbol in forbidden if symbol in data)
    if hits:
        raise SystemExit("Deprecated system-bar symbols remain:\n" + "\n".join(hits))
    print(f"PASS: {len(dex_files)} DEX files contain no deprecated system-bar setter names.")


if __name__ == "__main__":
    main()
