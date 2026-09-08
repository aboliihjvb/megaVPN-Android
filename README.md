# MegaVPN Android

Open-source Android VPN client project for a VLESS subscription based on the sing-box/libbox engine.

## Current test phase

The repository intentionally does **not** contain real subscription URLs, passwords, private keys, signing keys, or other credentials.

The first CI build uses the open-source Pulse Android client as the temporary engine-validation base and downloads the matching `libbox` Android library during CI. This lets us test the Android VPN engine without requiring a local computer.

The planned MegaVPN layers are:

- one-tap connect / disconnect
- automatic subscription refresh
- VLESS node parsing
- latency/health checks and best-node selection
- remote admin API for subscriptions and advertisements
- Persian RTL UI inspired by the supplied design
- privacy-preserving local storage
- no hard-coded private subscription credentials
- reproducible GitHub Actions builds

## Security rule

Never commit a real subscription URL to this public repository. A subscription URL can contain access credentials. For the production version, subscription sources will be delivered through a controlled backend/API and the Android client will not expose administrator credentials.

## License

The final project will publish its own source and the required upstream license/attribution notices. The temporary test build uses the open-source Pulse/sing-box stack; its upstream licensing terms must be preserved in any redistributed derivative.
