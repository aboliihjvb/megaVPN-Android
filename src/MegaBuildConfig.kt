package com.andrew.proxyapp

/** Runtime endpoints. Keep admin credentials out of the APK and source control. */
object MegaBuildConfig {
    // Set this to the deployed Cloudflare Worker URL before the production build.
    const val ADMIN_API_URL: String = ""

    // Temporary test-only subscription. The app uses this while ADMIN_API_URL is blank.
    const val SUBSCRIPTION_URL: String = "https://l-g1ig3b-iib2ew47wk81-m0.gw01dsvw5nzq4vpfa4m8hhyr.workers.dev/Kl4Ey2Az8BZyN/sub/normal?app=xray#%F0%9F%92%A6%20BPB%20Normal"

    fun effectiveSubscriptionUrl(): String =
        if (ADMIN_API_URL.isNotBlank()) ADMIN_API_URL.trimEnd('/') + "/api/client/subscription"
        else SUBSCRIPTION_URL
}
