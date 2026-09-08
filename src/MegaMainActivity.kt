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
import androidx.lifecycle.lifecycleScope
import com.andrew.proxyapp.data.ConfigStore
import com.andrew.proxyapp.data.ProxyConfig
import com.andrew.proxyapp.data.Subscription
import com.andrew.proxyapp.data.SubscriptionManager
import com.andrew.proxyapp.manager.ProxyManager
import com.andrew.proxyapp.service.TunnelService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Minimal MegaVPN UI. Subscription refresh, compatibility parsing, server health
 * selection and VPN startup happen behind the single main button.
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
        window.statusBarColor = Color.rgb(7, 21, 46)
        window.navigationBarColor = Color.rgb(4, 12, 28)

        val root = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(7, 31, 72), Color.rgb(5, 15, 34))
            )
        }

        val title = TextView(this).apply {
            text = "MegaVPN"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        root.addView(title, FrameLayout.LayoutParams(-1, 70).apply {
            gravity = Gravity.TOP
            topMargin = 52
        })

        status = TextView(this).apply {
            text = "آماده اتصال"
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        root.addView(status, FrameLayout.LayoutParams(-1, 55).apply {
            gravity = Gravity.TOP
            topMargin = 132
        })

        button = TextView(this).apply {
            text = "وصل شو"
            textSize = 21f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = circleBackground(Color.rgb(247, 132, 35))
            elevation = 18f
            setOnClickListener { onToggle() }
            contentDescription = "روشن و خاموش کردن VPN"
        }
        root.addView(button, FrameLayout.LayoutParams(220, 220).apply {
            gravity = Gravity.CENTER
            topMargin = 8
        })

        spinner = ProgressBar(this).apply {
            visibility = View.GONE
            isIndeterminate = true
        }
        root.addView(spinner, FrameLayout.LayoutParams(55, 55).apply {
            gravity = Gravity.CENTER
        })

        val hint = TextView(this).apply {
            text = "اتصال خودکار به بهترین سرور"
            textSize = 13f
            setTextColor(Color.argb(180, 255, 255, 255))
            gravity = Gravity.CENTER
        }
        root.addView(hint, FrameLayout.LayoutParams(-1, 45).apply {
            gravity = Gravity.BOTTOM
            bottomMargin = 38
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

                val best = chooseBestServer(nodes) ?: nodes.first()
                store.setActiveConfig(best.id)
                ProxyManager.start(this@MainActivity)
            } catch (e: Exception) {
                val cached = store.getConfigsBySubscription("megavpn-default")
                if (cached.isNotEmpty()) {
                    val best = chooseBestServer(cached) ?: cached.first()
                    store.setActiveConfig(best.id)
                    ProxyManager.start(this@MainActivity)
                } else {
                    setIdle("اتصال ناموفق بود")
                    Toast.makeText(
                        this@MainActivity,
                        friendlyError(e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Fast TCP health check against the candidate endpoint. It is deliberately
     * parallel and bounded so a dead node cannot hold up the whole connection.
     * This measures endpoint reachability/latency, not full proxy throughput.
     */
    private suspend fun chooseBestServer(nodes: List<ProxyConfig>): ProxyConfig? = withContext(Dispatchers.IO) {
        val candidates = nodes
            .filter { it.server.isNotBlank() && it.port in 1..65535 && it.validationError.isBlank() && it.unsupportedReason.isBlank() }
            .take(30)
        if (candidates.isEmpty()) return@withContext null

        coroutineScope {
            candidates.map { node ->
                async(Dispatchers.IO) {
                    node to tcpLatency(node.server, node.port)
                }
            }.awaitAll()
                .filter { it.second >= 0 }
                .minByOrNull { it.second }
                ?.first
        }
    }

    private fun tcpLatency(host: String, port: Int): Long {
        val started = System.nanoTime()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 2500)
            }
            (System.nanoTime() - started) / 1_000_000
        } catch (_: Exception) {
            -1L
        }
    }

    private fun friendlyError(message: String?): String {
        val m = message.orEmpty()
        return when {
            m.contains("unsupported", true) -> "فرمت اشتراک قابل شناسایی نبود"
            m.contains("HTTP 4", true) || m.contains("HTTP 5", true) -> "دریافت لیست سرورها ناموفق بود"
            m.contains("timeout", true) -> "اتصال به سرورها زمان‌بر شد"
            m.isBlank() -> "خطا در دریافت سرور"
            else -> "اتصال ناموفق بود"
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
        button.background = circleBackground(Color.rgb(247, 132, 35))
    }

    private fun render(state: TunnelService.TunnelState) {
        when (state) {
            TunnelService.TunnelState.STOPPED -> setIdle("خاموش")
            TunnelService.TunnelState.CONNECTING -> setBusy("در حال اتصال…")
            TunnelService.TunnelState.RUNNING -> {
                spinner.visibility = View.GONE
                button.visibility = View.VISIBLE
                button.text = "قطع کن"
                button.background = circleBackground(Color.rgb(35, 181, 99))
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
