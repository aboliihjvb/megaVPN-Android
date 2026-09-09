#!/usr/bin/env python3
"""ZONE PAK TOOL v0.4 - Termux-friendly Unreal PAK diagnostics and packing helper."""
from __future__ import annotations
import hashlib, json, os, shutil, subprocess, sys, time, struct, platform
from pathlib import Path

VERSION = "0.4.0"
CONFIG = Path.home() / ".zone_pak_tool"
LOG = CONFIG / "zone_tool.log"


def setup():
    CONFIG.mkdir(parents=True, exist_ok=True)


def log(msg):
    setup()
    with LOG.open("a", encoding="utf-8") as f:
        f.write(f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] {msg}\n")


def cfg_load():
    try:
        return json.loads((CONFIG / "config.json").read_text(encoding="utf-8"))
    except Exception:
        return {"unrealpak": "", "repak": ""}


def cfg_save(c):
    setup()
    (CONFIG / "config.json").write_text(json.dumps(c, indent=2), encoding="utf-8")


def find_bin(kind):
    c = cfg_load().get(kind, "")
    candidates = [c] if c else []
    names = [kind, kind + ".exe"]
    for n in names:
        hit = shutil.which(n)
        if hit:
            candidates.append(hit)
    candidates += [str(Path.cwd() / n) for n in names]
    candidates += [str(Path.home() / n) for n in names]
    candidates += [str(Path.home() / ".local" / "bin" / n) for n in names]
    for x in candidates:
        if x and Path(x).is_file() and os.access(x, os.X_OK):
            return x
    return None


def ask_path(prompt, default=None):
    s = input(f"{prompt}" + (f" [{default}]" if default else "") + ": ").strip()
    return Path(s or default).expanduser() if (s or default) else None


def sha256_file(pak):
    h = hashlib.sha256()
    with pak.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def detect_pak(pak):
    """Best-effort inspection of standard Unreal PAK footer magic."""
    size = pak.stat().st_size
    read_n = min(size, 4 * 1024 * 1024)
    with pak.open("rb") as f:
        f.seek(max(0, size - read_n))
        data = f.read(read_n)
    magic = struct.pack("<I", 0x5A6F12E1)
    hits = [i for i in range(max(0, len(data) - 3)) if data[i:i + 4] == magic]
    return {
        "size": size,
        "sha256": sha256_file(pak),
        "magic_found": bool(hits),
        "footer_offsets": hits[-8:],
        "scan_bytes": read_n,
    }


def run_cmd(cmd):
    print("\n$ " + " ".join(map(str, cmd)))
    try:
        p = subprocess.run(cmd, text=True, capture_output=True)
    except Exception as e:
        print("[!] اجرا ناموفق:", e)
        log("ERROR " + repr(e))
        return False
    if p.stdout:
        print(p.stdout.rstrip())
    if p.stderr:
        print(p.stderr.rstrip())
    log(f"CMD={' '.join(map(str, cmd))} RC={p.returncode}")
    return p.returncode == 0


def backend_status():
    rp = find_bin("repak")
    up = find_bin("unrealpak")
    print("\nBACKENDS")
    print("  repak:     " + (rp or "NOT FOUND"))
    print("  UnrealPak: " + (up or "NOT FOUND"))
    print("  platform:  " + platform.machine())
    return rp, up


def show_info():
    pak = ask_path("مسیر PAK")
    if not pak or not pak.is_file():
        print("[!] فایل پیدا نشد.")
        return
    r = detect_pak(pak)
    print(f"\nFILE: {pak}")
    print(f"SIZE: {r['size']:,} bytes")
    print(f"SHA256: {r['sha256']}")
    print(f"SCANNED TAIL: {r['scan_bytes']:,} bytes")
    print("FOOTER MAGIC: detected" if r["magic_found"] else "FOOTER MAGIC: not detected")
    if r["magic_found"]:
        print("TYPE: standard Unreal PAK footer signature found")
    else:
        print("TYPE: unknown / non-standard / encrypted-or-wrapped / incomplete")
        print("NOTE: footer absence alone does NOT prove encryption or corruption.")

    rp, up = backend_status()
    if rp:
        print("\n[*] Testing repak info...")
        run_cmd([rp, "info", str(pak)])
        print("\n[*] Testing repak list...")
        run_cmd([rp, "list", str(pak)])
    elif up:
        print("\n[*] Testing UnrealPak -Test...")
        run_cmd([up, str(pak), "-Test"])
        print("\n[*] Listing with UnrealPak...")
        run_cmd([up, str(pak), "-List"])
    else:
        print("\n[!] No parser backend installed. Install/configure repak or UnrealPak, then rerun 9.")


def run_backend(args, prefer="auto"):
    up = find_bin("unrealpak")
    rp = find_bin("repak")
    if prefer in ("auto", "repak") and rp:
        return run_cmd([rp] + args)
    if up:
        return run_cmd([up] + args)
    print("[!] هیچ backendی پیدا نشد (UnrealPak یا repak).")
    print("    گزینه 8 را برای ثبت مسیر backend استفاده کن.")
    return False


def unpack():
    pak = ask_path("مسیر PAK")
    if not pak or not pak.is_file():
        print("[!] فایل پیدا نشد.")
        return
    info = detect_pak(pak)
    print("[*] footer:", "detected" if info["magic_found"] else "not detected")
    out = ask_path("مسیر خروجی", str(pak.with_suffix("")))
    if not out:
        return
    out.mkdir(parents=True, exist_ok=True)
    rp = find_bin("repak")
    if rp:
        ok = run_backend(["unpack", str(pak), "-o", str(out)], "repak")
    else:
        ok = run_backend([str(pak), "-Extract", str(out)], "unrealpak")
    print("[+] استخراج انجام شد." if ok else "[!] استخراج شکست خورد.")


def repack(full=True):
    folder = ask_path("پوشه استخراج‌شده")
    if not folder or not folder.is_dir():
        print("[!] پوشه پیدا نشد.")
        return
    default = str(folder.parent / (folder.name + ("_repacked.pak" if full else ".pak")))
    out = ask_path("مسیر PAK خروجی", default)
    if not out:
        return
    out.parent.mkdir(parents=True, exist_ok=True)
    if out.exists():
        backup = out.with_suffix(out.suffix + ".bak")
        shutil.copy2(out, backup)
        print("[*] بکاپ:", backup)
    rp = find_bin("repak")
    if rp:
        ok = run_backend(["pack", str(folder), "-o", str(out)], "repak")
    else:
        ok = run_backend([f"-Create={folder}", str(out)], "unrealpak")
    print("[+] REPACK موفق بود." if ok else "[!] REPACK شکست خورد.")


def edit():
    folder = ask_path("پوشه استخراج‌شده")
    if not folder or not folder.is_dir():
        print("[!] پوشه پیدا نشد.")
        return
    files = sum(1 for p in folder.rglob("*") if p.is_file())
    print(f"[+] آماده ویرایش؛ {files} فایل پیدا شد.")


def backend_install_hint():
    print("\n[*] Backendهای قابل استفاده:")
    print("  1) UnrealPak — ابزار رسمی Unreal Engine")
    print("  2) repak — ابزار مستقل برای Unreal PAK")
    print("  Termux/Android ممکن است به build سازگار با معماری دستگاه نیاز داشته باشد.")
    print("  مسیر فعلی: repak و UnrealPak به‌صورت خودکار جست‌وجو می‌شوند.")


def settings():
    c = cfg_load()
    print("UnrealPak:", c.get("unrealpak") or "auto")
    print("repak:    ", c.get("repak") or "auto")
    print("platform: ", platform.machine())
    choice = input("ثبت مسیر (u=UnrealPak / r=repak / Enter=لغو): ").strip().lower()
    if choice == "u":
        p = ask_path("مسیر UnrealPak", c.get("unrealpak") or None)
        if p:
            c["unrealpak"] = str(p)
            cfg_save(c)
    elif choice == "r":
        p = ask_path("مسیر repak", c.get("repak") or None)
        if p:
            c["repak"] = str(p)
            cfg_save(c)


def delete_folder():
    folder = ask_path("پوشه برای حذف")
    if not folder or not folder.is_dir():
        print("[!] پوشه پیدا نشد.")
        return
    resolved = str(folder.resolve())
    if resolved in ("/", str(Path.home().resolve())):
        print("[!] مسیر خطرناک است.")
        return
    if input(f"حذف {folder}? [y/N]: ").lower() != "y":
        return
    shutil.rmtree(folder)
    print("[+] حذف شد.")


def protect():
    print("[i] Protect در این نسخه رمزگذاری، شکستن رمز، یا دورزدن محافظت بازی انجام نمی‌دهد.")


def menu():
    while True:
        print(f"\n╔══════════════════════════════╗\n║      ZONE TOOL v{VERSION}       ║\n╠══════════════════════════════╣")
        print("║ 1  UNPACK / AUTO DETECT      ║\n║ 2  INJECT / EDIT              ║\n║ 3  REPACK FULL                ║\n║ 4  REPACK TO PATH             ║\n║ 5  BUILD NEW PAK              ║\n║ 6  PROTECT PAK                ║\n║ 7  DELETE FOLDER              ║\n║ 8  SETTINGS / BACKENDS        ║\n║ 9  PAK INFO / DIAGNOSTICS     ║\n║ 0  EXIT                       ║\n╚══════════════════════════════╝")
        try:
            n = input("ZONE > ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            return
        if n == "1": unpack()
        elif n == "2": edit()
        elif n == "3": repack(True)
        elif n == "4": repack(False)
        elif n == "5": repack(False)
        elif n == "6": protect()
        elif n == "7": delete_folder()
        elif n == "8":
            settings()
            backend_install_hint()
            backend_status()
        elif n == "9": show_info()
        elif n == "0": return
        else: print("[!] گزینه نامعتبر است.")


if __name__ == "__main__":
    setup()
    menu()
