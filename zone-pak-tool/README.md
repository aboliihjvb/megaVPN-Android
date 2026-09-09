# ZONE PAK TOOL v0.1

A small Android/Termux CLI front-end for working with Unreal Engine PAK files.

## Run

```bash
pkg update
pkg install python
python zone_tool.py
```

## Features

- Unpack PAK
- Edit extracted files
- Repack/build PAK
- Delete working folders
- Configure the UnrealPak executable path
- Safe placeholder for protection functionality

## Important

The tool is a front-end and requires a compatible `UnrealPak` executable for real PAK processing. Compatibility depends on the target Unreal Engine/game version. This project does not bypass anti-cheat, authentication, or other game security controls.
