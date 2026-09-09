#!/usr/bin/env python3
"""ZONE PAK TOOL - Android/Termux starter.

This is a generic PAK workflow front-end. It delegates actual Unreal PAK
processing to a user-supplied UnrealPak executable; it does not bypass game
security or anti-cheat protections.
"""
import os
import shutil
import subprocess
from pathlib import Path

VERSION = "0.1.0"
CONFIG = Path.home() / ".zone_pak_tool"


def clear():
    os.system("clear")


def banner():
    print("\033[96m" + "=" * 58)
    print("                 ZONE PAK TOOL")
    print(f"                 Android / Termux  v{VERSION}")
    print("=" * 58 + "\033[0m")


def get_unrealpak():
    if CONFIG.exists():
        value = CONFIG.read_text(encoding="utf-8").strip()
        if value:
            return Path(value).expanduser()
    return Path("UnrealPak")


def set_unrealpak():
    print(f"Current: {get_unrealpak()}")
    value = input("UnrealPak path: ").strip().strip('"').strip("'")
    if value:
        CONFIG.write_text(value, encoding="utf-8")
        print("[+] Saved.")


def run_tool(args):
    tool = get_unrealpak()
    try:
        p = subprocess.run([str(tool), *map(str, args)], text=True)
    except FileNotFoundError:
        print(f"[!] UnrealPak not found: {tool}")
        print("    Use SETTINGS to configure its path.")
        return False
    return p.returncode == 0


def unpack():
    pak = Path(input("PAK file: ").strip().strip('"').strip("'")).expanduser()
    if not pak.is_file():
        print("[!] PAK file not found.")
        return
    out = Path(str(pak) + "_unpacked")
    out.mkdir(parents=True, exist_ok=True)
    print(f"[*] Extracting to {out}")
    print("[+] Done." if run_tool([pak, "-Extract", out]) else "[!] Failed.")


def repack():
    src = Path(input("Folder to pack: ").strip().strip('"').strip("'")).expanduser()
    if not src.is_dir():
        print("[!] Folder not found.")
        return
    out = Path(input("Output PAK: ").strip().strip('"').strip("'")).expanduser()
    out.parent.mkdir(parents=True, exist_ok=True)
    print("[+] Done." if run_tool([f"-Create={src}", out]) else "[!] Failed.")


def edit():
    folder = Path(input("Extracted folder: ").strip().strip('"').strip("'")).expanduser()
    if folder.is_dir():
        print(f"[+] Ready to edit normally: {folder}")
    else:
        print("[!] Folder not found.")


def delete_folder():
    folder = Path(input("Folder to delete: ").strip().strip('"').strip("'")).expanduser()
    if not folder.is_dir():
        print("[!] Folder not found.")
        return
    if input(f"Delete {folder}? [y/N]: ").lower() == "y":
        shutil.rmtree(folder)
        print("[+] Deleted.")


def protect():
    print("[!] PAK protection is engine/version dependent.")
    print("    v0.1 does not invent encryption or corrupt files.")


def main():
    while True:
        clear(); banner()
        print("""
  [1] UNPACK PAK
  [2] INJECT / EDIT
  [3] REPACK FULL
  [4] REPACK TO PATH
  [5] BUILD NEW PAK
  [6] PROTECT PAK
  [7] DELETE FOLDER
  [8] SETTINGS
  [0] EXIT
""")
        choice = input("PLEASE ENTER YOUR CHOICE: ").strip()
        if choice == "1": unpack()
        elif choice == "2": edit()
        elif choice in ("3", "4", "5"): repack()
        elif choice == "6": protect()
        elif choice == "7": delete_folder()
        elif choice == "8": set_unrealpak()
        elif choice == "0": break
        else: print("[!] Invalid choice.")
        input("\nPress Enter to continue...")


if __name__ == "__main__":
    main()
