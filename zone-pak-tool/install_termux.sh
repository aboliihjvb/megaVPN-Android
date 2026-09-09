#!/data/data/com.termux/files/usr/bin/bash
set -e

# ZONE PAK TOOL helper for Termux. It does not bypass encryption or game security.
ROOT="$(cd "$(dirname "$0")" && pwd)"
BIN="$HOME/.local/bin"
mkdir -p "$BIN"

pkg update -y
pkg install -y python rust clang make pkg-config

if command -v repak >/dev/null 2>&1; then
  echo "[+] repak already installed: $(command -v repak)"
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
python "$ROOT/zone_tool.py" <<'EOF'
8

0
EOF

echo
printf '%s\n' '[+] Backend setup finished.' 'Run:' "  export PATH=\"$BIN:\$PATH\"" "  python \"$ROOT/zone_tool.py\""