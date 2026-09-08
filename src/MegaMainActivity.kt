package com.andrew.proxyapp

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.andrew.proxyapp.data.ConfigStore
import com.andrew.proxyapp.data.Subscription
import com.andrew.proxyapp.data.SubscriptionManager
import com.andrew.proxyapp.manager.ProxyManager
import com.andrew.proxyapp.service.TunnelService
import kotlinx.coroutines.launch

/**
 * Intentionally tiny MegaVPN UI.
 * All subscription download, parsing, node selection and VPN startup happen behind one button.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var button: TextView
    private lateinit var status: TextView
    private lateinit var spinner: ProgressBar
    private val store by lazy { ConfigStore.get(this) }

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) connectInBackground()
        else setIdle("مجوز VPN لازم است")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        render(ProxyManager.state.value)
    }

    private fun buildUi() {
        window.statusBarColor = Color.rgb(8, 28, 58)
        window.navigationBarColor = Color.rgb(5, 18, 38)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(7, 25, 52))
        }

        val title = TextView(this).apply {
            text = "MegaVPN"
            textSize = 27f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        root.addView(title, FrameLayout.LayoutParams(-1, 70).apply {
            gravity = Gravity.TOP
            topMargin = 55
        })

        status = TextView(this).apply {
            text = "آماده اتصال"
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        root.addView(status, FrameLayout.LayoutParams(-1, 55).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            topMargin = 135
        })

        button = TextView(this).apply {
            text = "وصل شو"
            textSize = 21f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = circleBackground(Color.rgb(24, 123, 255))
            elevation = 18f
            setOnClickListener { onToggle() }
            contentDescription = "روشن و خاموش کردن VPN"
        }
        root.addView(button, FrameLayout.LayoutParams(210, 210).apply {
            gravity = Gravity.CENTER
            topMargin = 10
        })

        spinner = ProgressBar(this).apply {
            visibility = View.GONE
            isIndeterminate = true
        }
        root.addView(spinner, FrameLayout.LayoutParams(55, 55).apply {
            gravity = Gravity.CENTER
        })

        setContentView(root)
        ProxyManager.state.collectInLifecycle(this) { render(it) }
    }

    private fun onToggle() {
        if (ProxyManager.isRunning || ProxyManager.state.value == TunnelService.TunnelState.CONNECTING) {
            ProxyManager.stop(this)
            return
        }
        val permission = ProxyManager.permissionIntent(this)
        if (permission != null) {
            setBusy("در حال آماده‌سازی…")
            vpnPermissionLauncher.launch(permission)
        } else {
            connectInBackground()
        }
    }

    private fun connectInBackground() {
        setBusy("در حال پیدا کردن بهترین سرور…")
        lifecycleScope.launch {
            try {
                val subId = "megavpn-default"
                val subscription = store.getSubscription(subId)
                    ?: Subscription(id = subId, name = "MegaVPN", url = MegaBuildConfig.SUBSCRIPTION_URL)
                subscription.url = MegaBuildConfig.SUBSCRIPTION_URL
                SubscriptionManager.update(subscription, store)

                val nodes = store.getConfigsBySubscription(subId)
                if (nodes.isEmpty()) error("هیچ سرور فعالی پیدا نشد")

                // The base client already has node health testing; for the first test build
                // choose the first valid node. Automatic latency ranking can be added later.
                store.setActiveConfig(nodes.first().id)
                ProxyManager.start(this@MainActivity)
            } catch (e: Exception) {
                val cached = store.getConfigsBySubscription("megavpn-default")
                if (cached.isNotEmpty()) {
                    store.setActiveConfig(cached.first().id)
                    ProxyManager.start(this@MainActivity)
                } else {
                    setIdle("اتصال ناموفق بود")
                    Toast.makeText(this@MainActivity, e.message ?: "خطا در دریافت سرور", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setBusy(message: String) {
        spinner.visibility = View.VISIBLE
        button.visibility = View.INVISIBLE
        status.text = message
    }

    private fun setIdle(message: String) {
        spinner.visibility = View.GONE
        button.visibility = View.VISIBLE
        status.text = message
        button.text = "وصل شو"
        button.background = circleBackground(Color.rgb(24, 123, 255))
    }

    private fun render(state: TunnelService.TunnelState) {
        when (state) {
            TunnelService.TunnelState.STOPPED -> setIdle("خاموش")
            TunnelService.TunnelState.CONNECTING -> setBusy("در حال اتصال…")
            TunnelService.TunnelState.RUNNING -> {
                spinner.visibility = View.GONE
                button.visibility = View.VISIBLE
                button.text = "قطع کن"
                button.background = circleBackground(Color.rgb(26, 176, 94))
                status.text = "متصل"
            }
            TunnelService.TunnelState.ERROR -> setIdle("اتصال ناموفق بود")
        }
    }

    private fun circleBackground(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(3, Color.argb(80, 255, 255, 255))
    }
}

private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectInLifecycle(
    activity: AppCompatActivity,
    block: (T) -> Unit
) {
    activity.lifecycleScope.launch {
        collect { block(it) }
    }
}
