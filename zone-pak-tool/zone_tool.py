#!/usr/bin/env python3
"""ZONE PAK TOOL v0.2 - Termux helper for Unreal Engine PAK workflows."""
from __future__ import annotations
import json, os, shutil, subprocess, sys, time
from pathlib import Path

VERSION = "0.2.0"
CONFIG = Path.home() / ".zone_pak_tool"
LOG = CONFIG / "zone_tool.log"
DEFAULT_ROOT = Path.home() / "zone-pak-work"


def setup():
    CONFIG.mkdir(parents=True, exist_ok=True)
    DEFAULT_ROOT.mkdir(parents=True, exist_ok=True)


def log(msg: str):
    CONFIG.mkdir(parents=True, exist_ok=True)
    with LOG.open("a", encoding="utf-8") as f:
        f.write(f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] {msg}\n")


def cfg_load():
    p = CONFIG / "config.json"
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except Exception:
        return {"unrealpak": ""}


def cfg_save(c):
    CONFIG.mkdir(parents=True, exist_ok=True)
    (CONFIG / "config.json").write_text(json.dumps(c, indent=2), encoding="utf-8")


def find_unrealpak():
    c = cfg_load().get("unrealpak", "")
    candidates = [c] if c else []
    candidates += [
        str(Path.cwd() / "UnrealPak"), str(Path.cwd() / "UnrealPak.exe"),
        str(Path.home() / "UnrealPak"), str(Path.home() / "UnrealPak.exe"),
    ]
    for x in candidates:
        if x and Path(x).is_file() and os.access(x, os.X_OK): return x
        if x and Path(x).is_file(): return x
    for name in ("UnrealPak", "UnrealPak.exe"):
        hit = shutil.which(name)
        if hit: return hit
    return None


def ask_path(prompt, default=None):
    s = input(f"{prompt}" + (f" [{default}]" if default else "") + ": ").strip()
    return Path(s or default).expanduser() if (s or default) else None


def run_pak(args):
    exe = find_unrealpak()
    if not exe:
        print("[!] UnrealPak پیدا نشد.")
        print("    مسیر UnrealPak را در Settings ثبت کن.")
        return False
    cmd = [exe] + args
    print("\n$ " + " ".join(map(str, cmd)))
    try:
        p = subprocess.run(cmd, text=True, capture_output=True)
        if p.stdout: print(p.stdout)
        if p.stderr: print(p.stderr)
        log(f"CMD={' '.join(map(str, cmd))} RC={p.returncode}")
        return p.returncode == 0
    except Exception as e:
        print(f"[!] اجرای UnrealPak ناموفق بود: {e}")
        log(f"ERROR {e}")
        return False


def unpack():
    pak = ask_path("مسیر PAK")
    if not pak or not pak.is_file(): print("[!] فایل پیدا نشد."); return
    out = ask_path("مسیر خروجی", str(pak.with_suffix("")))
    if out is None: return
    out.mkdir(parents=True, exist_ok=True)
    print("[*] UNPACK...")
    ok = run_pak([str(pak), "-Extract", str(out)])
    print("[+] انجام شد." if ok else "[!] استخراج شکست خورد.")


def repack(full=True):
    folder = ask_path("پوشه استخراج‌شده")
    if not folder or not folder.is_dir(): print("[!] پوشه پیدا نشد."); return
    default = str(folder.parent / (folder.name + ("_repacked.pak" if full else ".pak")))
    out = ask_path("مسیر PAK خروجی", default)
    if not out: return
    out.parent.mkdir(parents=True, exist_ok=True)
    if out.exists():
        backup = out.with_suffix(out.suffix + ".bak")
        shutil.copy2(out, backup)
        print(f"[*] بکاپ: {backup}")
    ok = run_pak([f"-Create={folder}", str(out)])
    print("[+] REPACK موفق بود." if ok else "[!] REPACK شکست خورد.")


def inject_edit():
    folder = ask_path("پوشه استخراج‌شده برای EDIT")
    if not folder or not folder.is_dir(): print("[!] پوشه پیدا نشد."); return
    files = sum(1 for p in folder.rglob("*") if p.is_file())
    print(f"[+] پوشه آماده و قابل ویرایش است. فایل‌ها: {files}")
    print("    فایل‌ها را با ابزار دلخواه ویرایش کن؛ سپس REPACK را بزن.")


def build_new():
    print("[*] BUILD NEW PAK")
    repack(full=False)


def protect():
    print("PROTECT PAK: این نسخه رمزگذاری/دورزدن قفل بازی انجام نمی‌دهد.")
    print("برای محافظت امن، از بکاپ و دسترسی فایل سیستم استفاده کن.")


def delete_folder():
    folder = ask_path("پوشه برای حذف")
    if not folder or not folder.is_dir(): print("[!] پوشه پیدا نشد."); return
    if str(folder.resolve()) in ("/", str(Path.home().resolve())):
        print("[!] حذف این مسیر مجاز نیست."); return
    if input(f"حذف کامل {folder}? [y/N]: ").lower() != "y": return
    shutil.rmtree(folder)
    print("[+] حذف شد.")


def settings():
    c = cfg_load()
    print(f"UnrealPak فعلی: {c.get('unrealpak') or '(auto)'}")
    p = ask_path("مسیر UnrealPak", c.get("unrealpak") or None)
    if p:
        c["unrealpak"] = str(p)
        cfg_save(c)
        print("[+] ذخیره شد.")


def menu():
    while True:
        print("\n╔══════════════════════════════╗")
        print(f"║      ZONE TOOL v{VERSION}       ║")
        print("║        TERMUX / ANDROID      ║")
        print("╠══════════════════════════════╣")
        print("║ 1  UNPACK PAK                ║")
        print("║ 2  INJECT / EDIT             ║")
        print("║ 3  REPACK FULL               ║")
        print("║ 4  REPACK TO PATH            ║")
        print("║ 5  BUILD NEW PAK             ║")
        print("║ 6  PROTECT PAK               ║")
        print("║ 7  DELETE FOLDER             ║")
        print("║ 8  SETTINGS                  ║")
        print("║ 0  EXIT                      ║")
        print("╚══════════════════════════════╝")
        try: n = input("ZONE > ").strip()
        except (EOFError, KeyboardInterrupt): print(); return
        if n == "1": unpack()
        elif n == "2": inject_edit()
        elif n == "3": repack(True)
        elif n == "4": repack(False)
        elif n == "5": build_new()
        elif n == "6": protect()
        elif n == "7": delete_folder()
        elif n == "8": settings()
        elif n == "0": return
        else: print("[!] گزینه نامعتبر است.")


if __name__ == "__main__":
    setup(); menu()
