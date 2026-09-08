package com.andrew.proxyapp

/** Runtime endpoints. Keep real admin credentials and private subscription URLs out of source control. */
object MegaBuildConfig {
    // Set this to the deployed Cloudflare Worker URL, e.g. https://vpn-admin.example.workers.dev
    // The app falls back to the test subscription when it is blank or unreachable.
    const val ADMIN_API_URL: String = ""

    // Temporary test-only subscription. Move this to the admin backend before public release.
    const val SUBSCRIPTION_URL: String = "https://l-g1ig3b-iib2ew47wk81-m0.gw01dsvw5nzq4vpfa4m8hhyr.workers.dev/Kl4Ey2Az8BZyN/sub/normal?app=xray#%F0%9F%92%A6%20BPB%20Normal"
}
