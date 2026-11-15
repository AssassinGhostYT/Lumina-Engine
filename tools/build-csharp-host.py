#!/usr/bin/env python3
"""Compile the C# host assembly and copy it into the Android assets."""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / "tools" / "csharp" / "Lumina.ScriptHost"
OUT = ROOT / "build" / "csharp-host"
ASSETS = ROOT / "app" / "src" / "main" / "assetsGen" / "Managed"


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

    dll = OUT / "Lumina.ScriptHost.dll"
    if not dll.is_file():
        print(f"Build finished but {dll} is missing", file=sys.stderr)
        return 1

    ASSETS.mkdir(parents=True, exist_ok=True)
    shutil.copy2(dll, ASSETS / dll.name)
    print(f"Copied {dll.name} to {ASSETS}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
