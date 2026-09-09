#!/data/data/com.termux/files/usr/bin/bash
set -e

# ZONE PAK TOOL helper for Termux. It does not bypass encryption or game security.
ROOT="$(cd "$(dirname "$0")" && pwd)"
BIN="$HOME/.local/bin"
mkdir -p "$BIN"

pkg update -y
pkg install -y git python rust clang make pkg-config

if command -v repak >/dev/null 2>&1; then
  echo "[+] repak already installed: $(command -v repak)"
elif [ -x "$BIN/repak" ]; then
  echo "[+] repak already installed: $BIN/repak"
else
  echo "[*] Installing repak from its Rust source..."
  TMP="$(mktemp -d)"
  trap 'rm -rf "$TMP"' EXIT
  git clone --depth 1 https://github.com/trumank/repak.git "$TMP/repak"
  cd "$TMP/repak"
  cargo build --release --bin repak
  install -m 755 target/release/repak "$BIN/repak"
  echo "[+] Installed $BIN/repak"
fi

export PATH="$BIN:$PATH"

if command -v repak >/dev/null 2>&1; then
  echo "[+] Backend ready: $(command -v repak)"
  repak --help | head -n 20 || true
else
  echo "[!] repak was not produced. See the cargo error above."
  exit 1
fi

echo
echo "[+] Backend setup finished."
echo "Run:"
echo "  export PATH=\"$BIN:\$PATH\""
echo "  python \"$ROOT/zone_tool.py\""
