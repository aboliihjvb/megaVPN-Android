package com.andrew.proxyapp

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.TrafficStats
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
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
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var mainPanel: LinearLayout
    private lateinit var button: TextView
    private lateinit var status: TextView
    private lateinit var spinner: ProgressBar
    private lateinit var trafficText: TextView
    private lateinit var trafficSub: TextView
    private lateinit var serverText: TextView
    private lateinit var timerText: TextView
    private lateinit var loadingText: TextView

    private val store by lazy { ConfigStore.get(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var startedAt = 0L
    private var lastRx = -1L
    private var lastTx = -1L

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) connectInBackground()
        else setIdle("مجوز VPN لازم است")
    }

    private val trafficTicker = object : Runnable {
        override fun run() {
            updateTraffic()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showLoading()
        handler.postDelayed({
            if (!isFinishing) {
                buildUi()
                refreshConfigsSilently()
            }
        }, 850)
    }

    override fun onResume() {
        super.onResume()
        if (::button.isInitialized) render(ProxyManager.state.value)
        handler.post(trafficTicker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(trafficTicker)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun showLoading() {
        val bg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(5, 24, 58), Color.rgb(2, 8, 22))
        )
        val frame = FrameLayout(this).apply { background = bg }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 0, 48, 0)
        }
        val logo = TextView(this).apply {
            text = "M"
            textSize = 56f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = circleBackground(Color.rgb(18, 82, 160))
        }
        box.addView(logo, LinearLayout.LayoutParams(120, 120))
        val title = TextView(this).apply {
            text = "MegaVPN"
            textSize = 30f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
        box.addView(title, LinearLayout.LayoutParams(-1, 60).apply { topMargin = 24 })
        loadingText = TextView(this).apply {
            text = "در حال آماده‌سازی و دریافت سرورها…"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
        }
        box.addView(loadingText, LinearLayout.LayoutParams(-1, 44).apply { topMargin = 6 })
        val progress = ProgressBar(this).apply { isIndeterminate = true }
        box.addView(progress, LinearLayout.LayoutParams(48, 48).apply { topMargin = 8 })
        frame.addView(box, FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)
    }

    private fun buildUi() {
        window.statusBarColor = Color.rgb(5, 18, 42)
        window.navigationBarColor = Color.rgb(2, 8, 22)
        root = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(6, 31, 70), Color.rgb(3, 11, 27))
            )
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 18, 22, 18)
        }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))

        val top = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        val title = TextView(this).apply {
            text = "MegaVPN"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
        top.addView(title, LinearLayout.LayoutParams(0, 58, 1f))
        val badge = TextView(this).apply {
            text = "بدون تبلیغ"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(Color.argb(55, 255, 255, 255), 40f)
            setPadding(18, 0, 18, 0)
        }
        top.addView(badge, LinearLayout.LayoutParams(-2, 38))
        content.addView(top)

        val trafficCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 14, 20, 14)
            background = rounded(Color.argb(38, 255, 255, 255), 26f)
        }
        val trafficTitle = TextView(this).apply {
            text = "ترافیک زنده  •  ↑ ↓"
            textSize = 12f
            setTextColor(Color.LTGRAY)
        }
        trafficCard.addView(trafficTitle, LinearLayout.LayoutParams(-1, 25))
        trafficText = TextView(this).apply {
            text = "↓ 0 B    ↑ 0 B"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
        trafficCard.addView(trafficText, LinearLayout.LayoutParams(-1, 30))
        trafficSub = TextView(this).apply {
            text = "سرورها در پس‌زمینه بررسی می‌شوند"
            textSize = 11f
            setTextColor(Color.rgb(174, 194, 220))
        }
        trafficCard.addView(trafficSub, LinearLayout.LayoutParams(-1, 22))
        content.addView(trafficCard, LinearLayout.LayoutParams(-1, 92).apply { topMargin = 4 })

        status = TextView(this).apply {
            text = "آماده اتصال"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
        }
        content.addView(status, LinearLayout.LayoutParams(-1, 55).apply { topMargin = 10 })

        val center = FrameLayout(this)
        content.addView(center, LinearLayout.LayoutParams(-1, 300).apply { weight = 1f })
        button = TextView(this).apply {
            text = "وصل شو"
            textSize = 21f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = circleBackground(Color.rgb(247, 132, 35))
            elevation = 22f
            setOnClickListener { onToggle() }
            contentDescription = "روشن و خاموش کردن VPN"
        }
        center.addView(button, FrameLayout.LayoutParams(224, 224).apply { gravity = Gravity.CENTER })
        spinner = ProgressBar(this).apply { visibility = View.GONE; isIndeterminate = true }
        center.addView(spinner, FrameLayout.LayoutParams(52, 52).apply { gravity = Gravity.CENTER })

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(10, 8, 10, 8)
        }
        serverText = TextView(this).apply {
            text = "سرور: انتخاب خودکار"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
        }
        info.addView(serverText, LinearLayout.LayoutParams(-1, 28))
        timerText = TextView(this).apply {
            text = "زمان اتصال: 00:00"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(174, 194, 220))
        }
        info.addView(timerText, LinearLayout.LayoutParams(-1, 28))
        content.addView(info, LinearLayout.LayoutParams(-1, 70))

        val hint = TextView(this).apply {
            text = "اتصال هوشمند به سریع‌ترین سرور"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.argb(180, 255, 255, 255))
        }
        content.addView(hint, LinearLayout.LayoutParams(-1, 36))

        setContentView(root)
        ProxyManager.state.collectInLifecycle(this) { render(it) }
    }

    private fun refreshConfigsSilently() {
        lifecycleScope.launch {
            try {
                val subId = "megavpn-default"
                val sub = store.getSubscription(subId)
                    ?: Subscription(id = subId, name = "MegaVPN", url = MegaBuildConfig.SUBSCRIPTION_URL)
                sub.url = MegaBuildConfig.SUBSCRIPTION_URL
                SubscriptionManager.update(sub, store)
                val count = store.getConfigsBySubscription(subId).size
                if (::trafficSub.isInitialized) trafficSub.text = "$count سرور آماده • به‌روزرسانی خودکار"
            } catch (_: Exception) {
                if (::trafficSub.isInitialized) trafficSub.text = "حالت آفلاین • استفاده از سرورهای ذخیره‌شده"
            }
        }
    }

    private fun onToggle() {
        if (ProxyManager.isRunning || ProxyManager.state.value == TunnelService.TunnelState.CONNECTING) {
            ProxyManager.stop(this)
            return
        }
        val permission = ProxyManager.permissionIntent(this)
        if (permission != null) {
            setBusy("در حال آماده‌سازی اتصال…")
            vpnPermissionLauncher.launch(permission)
        } else connectInBackground()
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
                serverText.text = "سرور: ${best.server}:${best.port}"
                animateConnect()
                ProxyManager.start(this@MainActivity)
            } catch (e: Exception) {
                val cached = store.getConfigsBySubscription("megavpn-default")
                if (cached.isNotEmpty()) {
                    val best = chooseBestServer(cached) ?: cached.first()
                    store.setActiveConfig(best.id)
                    serverText.text = "سرور: ${best.server}:${best.port}"
                    animateConnect()
                    ProxyManager.start(this@MainActivity)
                } else {
                    setIdle("اتصال ناموفق بود")
                    Toast.makeText(this@MainActivity, friendlyError(e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun animateConnect() {
        button.animate().scaleX(1.08f).scaleY(1.08f).setDuration(180)
            .setInterpolator(AccelerateDecelerateInterpolator()).withEndAction {
                button.animate().scaleX(1f).scaleY(1f).setDuration(280).start()
            }.start()
    }

    private suspend fun chooseBestServer(nodes: List<ProxyConfig>): ProxyConfig? = withContext(Dispatchers.IO) {
        val candidates = nodes.filter {
            it.server.isNotBlank() && it.port in 1..65535 &&
                it.validationError.isBlank() && it.unsupportedReason.isBlank()
        }.take(30)
        if (candidates.isEmpty()) return@withContext null
        coroutineScope {
            candidates.map { node -> async(Dispatchers.IO) { node to tcpLatency(node.server, node.port) } }
                .awaitAll().filter { it.second >= 0 }.minByOrNull { it.second }?.first
        }
    }

    private fun tcpLatency(host: String, port: Int): Long {
        val started = System.nanoTime()
        return try {
            Socket().use { it.connect(InetSocketAddress(host, port), 2500) }
            (System.nanoTime() - started) / 1_000_000
        } catch (_: Exception) { -1L }
    }

    private fun updateTraffic() {
        val rx = TrafficStats.getTotalRxBytes().coerceAtLeast(0)
        val tx = TrafficStats.getTotalTxBytes().coerceAtLeast(0)
        if (lastRx < 0) { lastRx = rx; lastTx = tx }
        val dr = (rx - lastRx).coerceAtLeast(0)
        val dt = (tx - lastTx).coerceAtLeast(0)
        lastRx = rx; lastTx = tx
        trafficText.text = "↓ ${formatBytes(dr)}/s    ↑ ${formatBytes(dt)}/s"
        if (ProxyManager.isRunning && startedAt > 0) {
            val sec = (System.currentTimeMillis() - startedAt) / 1000
            timerText.text = String.format(Locale.US, "زمان اتصال: %02d:%02d", sec / 60, sec % 60)
        }
    }

    private fun formatBytes(v: Long): String = when {
        v < 1024 -> "$v B"
        v < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", v / 1024.0)
        v < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", v / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.1f GB", v / (1024.0 * 1024.0 * 1024.0))
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
        startedAt = 0L
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
                status.text = "متصل • اتصال امن برقرار است"
                if (startedAt == 0L) startedAt = System.currentTimeMillis()
            }
            TunnelService.TunnelState.ERROR -> setIdle("اتصال ناموفق بود")
        }
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius
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
    activity.lifecycleScope.launch { collect { block(it) } }
}
