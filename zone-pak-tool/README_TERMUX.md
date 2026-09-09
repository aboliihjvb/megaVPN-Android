# ZONE PAK TOOL — Termux / Android

This branch contains a Termux-friendly Unreal PAK helper. The tool uses an installed backend for actual PAK parsing/packing; it does not bypass encryption, authentication, anti-cheat, or other game security.

## Quick setup

From the cloned repository:

```sh
cd ~/megaVPN-Android/zone-pak-tool
bash install_termux.sh
export PATH="$HOME/.local/bin:$PATH"
python zone_tool.py
```

Then choose **9** (PAK INFO / DIAGNOSTICS). If `repak` is available, the tool will run `repak info` and `repak list` automatically.

## Your PAK

For an Android shared-storage file, use:

```text
/storage/emulated/0/Download/game_patch_4.6.0.21552.pak
```

If Termux cannot read shared storage, run once:

```sh
termux-setup-storage
```

The current diagnostic result for the user's sample showed a 119,215-byte file but no standard Unreal PAK footer signature. That alone does **not** prove corruption or encryption. The backend test is the authoritative next check.

## ARM64 note

Official prebuilt `repak` releases may not include an Android ARM64 binary. The installer therefore builds `repak` from its Rust source on the device. Build time and RAM/disk requirements vary by phone.

If the build fails, keep the complete `cargo build` error; do not modify the PAK to work around the error.
