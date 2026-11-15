#!/usr/bin/env python3
"""Download the Mono runtime packs and lay them out for the Android app.

The .NET Android runtime packs are the same files used by .NET for Android.
They are not committed to the repository; CI (or a local build) runs this
script once before Gradle packages the app.

Generated layout:
  thirdparty/Runtime.Mono/gen-android/dist/Runtime.Framework/   managed BCL
  thirdparty/Runtime.Mono/gen-android/dist/Runtime.Framework/<abi>/CoreLib
  thirdparty/Runtime.Mono/gen-android/jniLibs/<abi>/*.so        native runtime
"""

from __future__ import annotations

import os
import shutil
import sys
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "thirdparty" / "Runtime.Mono"
CACHE = BASE / "cache"
DIST = BASE / "gen-android" / "dist" / "Runtime.Framework"
JNI_LIBS = BASE / "gen-android" / "jniLibs"

VERSION = os.environ.get("LUMINA_DOTNET_RUNTIME_VERSION", "10.0.15")
MARKER = BASE / "gen-android" / f".prepared-{VERSION}"

# NuGet RID -> Android ABI folder
TARGETS = {
    "android-arm64": "arm64-v8a",
    "android-arm": "armeabi-v7a",
    "android-x64": "x86_64",
}


def package_url(rid: str) -> str:
    package = f"microsoft.netcore.app.runtime.mono.{rid}"
    return (
        "https://api.nuget.org/v3-flatcontainer/"
        f"{package}/{VERSION}/{package}.{VERSION}.nupkg"
    )


def download(rid: str) -> Path:
    CACHE.mkdir(parents=True, exist_ok=True)
    target = CACHE / f"{rid}-{VERSION}.nupkg"
    if target.is_file() and target.stat().st_size > 0:
        return target
    print(f"Downloading Mono runtime {rid} {VERSION}...")
    tmp = target.with_suffix(".part")
    urllib.request.urlretrieve(package_url(rid), tmp)
    tmp.replace(target)
    return target


def copy_tree(zf: zipfile.ZipFile, prefix: str, dest: Path, predicate) -> int:
    dest.mkdir(parents=True, exist_ok=True)
    count = 0
    for info in zf.infolist():
        if info.is_dir() or not info.filename.startswith(prefix):
            continue
        name = info.filename[len(prefix):]
        if "/" in name or not predicate(name):
            continue
        with zf.open(info) as src, open(dest / name, "wb") as out:
            shutil.copyfileobj(src, out)
        count += 1
    return count


def prepare_target(rid: str, abi: str) -> None:
    archive = download(rid)
    prefix = f"runtimes/{rid}/"
    with zipfile.ZipFile(archive) as zf:
        names = zf.namelist()
        lib_entry = next(
            (n for n in names
             if n.startswith(prefix + "lib/net") and not n.endswith("/")),
            None,
        )
        if lib_entry is None:
            raise RuntimeError(f"{archive.name} has no lib/net* directory")
        lib_prefix = lib_entry.rsplit("/", 1)[0] + "/"

        copied = copy_tree(zf, lib_prefix, DIST, lambda _name: True)
        copied_native = copy_tree(
            zf, prefix + "native/", JNI_LIBS / abi, lambda n: n.endswith(".so")
        )
        if copied_native == 0:
            raise RuntimeError(f"{archive.name} has no native .so files")

        corelib = zipfile.Path(zf, at=prefix + "native/System.Private.CoreLib.dll")
        if not corelib.exists():
            raise RuntimeError(f"{archive.name} has no System.Private.CoreLib.dll")
        corelib_dest = DIST / abi
        corelib_dest.mkdir(parents=True, exist_ok=True)
        with corelib.open("rb") as src, open(
            corelib_dest / "System.Private.CoreLib.dll", "wb"
        ) as out:
            shutil.copyfileobj(src, out)

    print(f"  {abi}: {copied} managed files, {copied_native} native libraries")


def main() -> int:
    if MARKER.is_file() and DIST.is_dir() and JNI_LIBS.is_dir():
        print(f"Mono runtime {VERSION} already prepared at {BASE / 'gen-android'}")
        return 0

    for rid, abi in TARGETS.items():
        prepare_target(rid, abi)

    MARKER.parent.mkdir(parents=True, exist_ok=True)
    MARKER.write_text(VERSION + "\n", encoding="utf-8")
    print(f"Mono runtime ready: {BASE / 'gen-android'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
