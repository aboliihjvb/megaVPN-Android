#!/usr/bin/env python3
"""ZONE PAK TOOL v0.3 - Termux PAK inspector/extractor/packer."""
from __future__ import annotations
import json, os, shutil, subprocess, sys, time, struct, platform
from pathlib import Path

VERSION = "0.3.0"
CONFIG = Path.home() / ".zone_pak_tool"
LOG = CONFIG / "zone_tool.log"


def setup():
    CONFIG.mkdir(parents=True, exist_ok=True)


def log(msg):
    setup()
    with LOG.open("a", encoding="utf-8") as f: f.write(f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] {msg}\n")


def cfg_load():
    try: return json.loads((CONFIG / "config.json").read_text(encoding="utf-8"))
    except Exception: return {"unrealpak": "", "repak": ""}


def cfg_save(c):
    setup(); (CONFIG / "config.json").write_text(json.dumps(c, indent=2), encoding="utf-8")


def find_bin(kind):
    c = cfg_load().get(kind, "")
    candidates = [c] if c else []
    names = [kind, kind + ".exe"]
    for n in names:
        hit = shutil.which(n)
        if hit: candidates.append(hit)
    candidates += [str(Path.cwd()/n) for n in names]
    candidates += [str(Path.home()/n) for n in names]
    for x in candidates:
        if x and Path(x).is_file(): return x
    return None


def ask_path(prompt, default=None):
    s = input(f"{prompt}" + (f" [{default}]" if default else "") + ": ").strip()
    return Path(s or default).expanduser() if (s or default) else None


def detect_pak(pak):
    """Best-effort footer inspection. Unreal PAK footer magic is 0x5A6F12E1."""
    size = pak.stat().st_size
    data = b""
    with pak.open("rb") as f:
        f.seek(max(0, size - min(size, 1024 * 1024)))
        data = f.read()
    magic = struct.pack("<I", 0x5A6F12E1)
    hits = [i for i in range(len(data)-4) if data[i:i+4] == magic]
    result = {"size": size, "magic_found": bool(hits), "footer_offsets": hits[-8:]}
    if hits:
        off = hits[-1]
        tail = data[off:]
        result["tail_hex"] = tail[:64].hex()
        # Common footer layout has encrypted/index flags and version fields after the magic;
        # don't pretend an exact version when bytes are ambiguous.
        result["note"] = "Unreal PAK footer detected; exact version requires a parser/backend."
    else:
        result["note"] = "Standard Unreal PAK footer magic not found in the last 1 MiB."
    return result


def show_info():
    pak = ask_path("مسیر PAK")
    if not pak or not pak.is_file(): print("[!] فایل پیدا نشد."); return
    r = detect_pak(pak)
    print(f"\nFILE: {pak}\nSIZE: {r['size']:,} bytes")
    print("TYPE: Unreal PAK-like" if r["magic_found"] else "TYPE: unknown / non-standard / protected")
    print("FOOTER: detected" if r["magic_found"] else "FOOTER: not detected")
    if r["magic_found"]: print("NOTE:", r["note"])


def run_backend(args, prefer="auto"):
    up = find_bin("unrealpak")
    rp = find_bin("repak")
    if prefer in ("auto", "repak") and rp:
        cmd = [rp] + args
    elif up:
        cmd = [up] + args
    else:
        print("[!] هیچ backendی پیدا نشد (UnrealPak یا repak).")
        print("    گزینه 8 را برای ثبت مسیر backend استفاده کن.")
        return False
    print("\n$ " + " ".join(map(str, cmd)))
    try:
        p = subprocess.run(cmd, text=True, capture_output=True)
        if p.stdout: print(p.stdout)
        if p.stderr: print(p.stderr)
        log(f"CMD={' '.join(map(str, cmd))} RC={p.returncode}")
        return p.returncode == 0
    except Exception as e:
        print("[!] اجرا ناموفق:", e); log("ERROR " + repr(e)); return False


def unpack():
    pak = ask_path("مسیر PAK")
    if not pak or not pak.is_file(): print("[!] فایل پیدا نشد."); return
    info = detect_pak(pak)
    print("[*] تشخیص:", "Unreal PAK footer" if info["magic_found"] else "نامشخص")
    out = ask_path("مسیر خروجی", str(pak.with_suffix("")))
    if not out: return
    out.mkdir(parents=True, exist_ok=True)
    rp = find_bin("repak")
    if rp:
        ok = run_backend(["unpack", str(pak), "-o", str(out)], "repak")
    else:
        ok = run_backend([str(pak), "-Extract", str(out)], "unrealpak")
    print("[+] استخراج انجام شد." if ok else "[!] استخراج شکست خورد.")


def repack(full=True):
    folder = ask_path("پوشه استخراج‌شده")
    if not folder or not folder.is_dir(): print("[!] پوشه پیدا نشد."); return
    default = str(folder.parent / (folder.name + ("_repacked.pak" if full else ".pak")))
    out = ask_path("مسیر PAK خروجی", default)
    if not out: return
    out.parent.mkdir(parents=True, exist_ok=True)
    if out.exists():
        backup = out.with_suffix(out.suffix + ".bak")
        shutil.copy2(out, backup); print("[*] بکاپ:", backup)
    rp = find_bin("repak")
    if rp:
        ok = run_backend(["pack", str(folder), "-o", str(out)], "repak")
    else:
        ok = run_backend([f"-Create={folder}", str(out)], "unrealpak")
    print("[+] REPACK موفق بود." if ok else "[!] REPACK شکست خورد.")


def edit():
    folder = ask_path("پوشه استخراج‌شده")
    if not folder or not folder.is_dir(): print("[!] پوشه پیدا نشد."); return
    files = sum(1 for p in folder.rglob("*") if p.is_file())
    print(f"[+] آماده ویرایش؛ {files} فایل پیدا شد.")


def backend_install_hint():
    print("\n[*] Backendهای قابل استفاده:")
    print("  1) UnrealPak — ابزار رسمی Unreal Engine")
    print("  2) repak — ابزار مستقل PAK؛ نسخه‌های رسمی فعلی باینری Linux x86_64 دارند.")
    print("  Termux روی ARM64 ممکن است نیاز به build از source داشته باشد.")
    print("  برای UnrealPak هم باید build سازگار با بازی/پروژه را داشته باشی.")


def settings():
    c = cfg_load()
    print("UnrealPak:", c.get("unrealpak") or "auto")
    print("repak:    ", c.get("repak") or "auto")
    print("platform: ", platform.machine())
    choice = input("ثبت مسیر (u=UnrealPak / r=repak / Enter=لغو): ").strip().lower()
    if choice == "u":
        p = ask_path("مسیر UnrealPak", c.get("unrealpak") or None)
        if p: c["unrealpak"] = str(p); cfg_save(c)
    elif choice == "r":
        p = ask_path("مسیر repak", c.get("repak") or None)
        if p: c["repak"] = str(p); cfg_save(c)


def delete_folder():
    folder = ask_path("پوشه برای حذف")
    if not folder or not folder.is_dir(): print("[!] پوشه پیدا نشد."); return
    resolved = str(folder.resolve())
    if resolved in ("/", str(Path.home().resolve())): print("[!] مسیر خطرناک است."); return
    if input(f"حذف {folder}? [y/N]: ").lower() != "y": return
    shutil.rmtree(folder); print("[+] حذف شد.")


def protect():
    print("[i] Protect در این نسخه رمزگذاری یا دورزدن محافظت بازی انجام نمی‌دهد.")


def menu():
    while True:
        print(f"\n╔══════════════════════════════╗\n║      ZONE TOOL v{VERSION}       ║\n╠══════════════════════════════╣")
        print("║ 1  UNPACK / AUTO DETECT      ║\n║ 2  INJECT / EDIT              ║\n║ 3  REPACK FULL                ║\n║ 4  REPACK TO PATH             ║\n║ 5  BUILD NEW PAK              ║\n║ 6  PROTECT PAK                ║\n║ 7  DELETE FOLDER              ║\n║ 8  SETTINGS / BACKENDS        ║\n║ 9  PAK INFO / DIAGNOSTICS     ║\n║ 0  EXIT                       ║\n╚══════════════════════════════╝")
        try: n=input("ZONE > ").strip()
        except (EOFError, KeyboardInterrupt): print(); return
        if n=="1": unpack()
        elif n=="2": edit()
        elif n=="3": repack(True)
        elif n=="4": repack(False)
        elif n=="5": repack(False)
        elif n=="6": protect()
        elif n=="7": delete_folder()
        elif n=="8": settings(); backend_install_hint()
        elif n=="9": show_info()
        elif n=="0": return
        else: print("[!] گزینه نامعتبر است.")


if __name__ == "__main__": setup(); menu()
