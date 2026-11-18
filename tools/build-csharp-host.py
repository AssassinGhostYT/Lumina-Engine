#!/usr/bin/env python3
"""Compile the C# host assembly and copy it into the Android assets."""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / "tools" / "csharp" / "Nebulon.ScriptHost"
OUT = ROOT / "build" / "csharp-host"
ASSETS = ROOT / "app" / "src" / "main" / "assetsGen" / "Managed"
REFS = ASSETS / "refs"


def reference_dir(dotnet: str) -> Path | None:
    roots = [Path(dotnet).resolve().parent]
    env_root = os.environ.get("DOTNET_ROOT")
    if env_root:
        roots.append(Path(env_root))
    for root in roots:
        packs = root / "packs" / "Microsoft.NETCore.App.Ref"
        if not packs.is_dir():
            continue
        versions = sorted(
            (p for p in packs.iterdir() if (p / "ref" / "net10.0").is_dir()),
            key=lambda p: p.name,
            reverse=True,
        )
        if versions:
            return versions[0] / "ref" / "net10.0"
    return None


def main() -> int:
    dotnet = shutil.which("dotnet")
    if dotnet is None:
        print(
            "dotnet was not found. Install the .NET 10 SDK or run this step in CI.",
            file=sys.stderr,
        )
        return 1

    OUT.mkdir(parents=True, exist_ok=True)
    env = os.environ.copy()
    # The .NET 10 GC reserves a huge virtual range by default; some CI and
    # sandbox kernels reject it even though physical memory is available.
    env.setdefault("DOTNET_GCHeapHardLimit", "0x40000000")
    env.setdefault("DOTNET_gcServer", "0")
    subprocess.run(
        [
            dotnet,
            "build",
            str(PROJECT),
            "-c",
            "Release",
            "-o",
            str(OUT),
            "--nologo",
        ],
        check=True,
        env=env,
    )

    dll = OUT / "Nebulon.ScriptHost.dll"
    if not dll.is_file():
        print(f"Build finished but {dll} is missing", file=sys.stderr)
        return 1

    refs = reference_dir(dotnet)
    if refs is None:
        print("Microsoft.NETCore.App.Ref net10.0 was not found", file=sys.stderr)
        return 1

    ASSETS.mkdir(parents=True, exist_ok=True)
    for old in ASSETS.glob("*.dll"):
        old.unlink()
    for source in sorted(OUT.glob("*.dll")):
        shutil.copy2(source, ASSETS / source.name)

    REFS.mkdir(parents=True, exist_ok=True)
    for old in REFS.glob("*.dll"):
        old.unlink()
    for source in sorted(refs.glob("*.dll")):
        shutil.copy2(source, REFS / source.name)

    print(f"Copied {len(list(ASSETS.glob('*.dll')))} host assemblies and "
          f"{len(list(REFS.glob('*.dll')))} reference assemblies to {ASSETS}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
