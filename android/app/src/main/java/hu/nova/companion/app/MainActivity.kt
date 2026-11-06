package hu.nova.companion.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader

/**
 * Nova Companion – a telefonos felület egy WebView-ban fut (assets/index.html = companion-web),
 * és a játékban futó Nova Companion moddal (LAN) vagy a relay szerverrel (internet) kommunikál.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView

    /**
     * A felületet egy belső https címről szolgáljuk ki (nem file://), így a WebView „biztonságos környezetnek”
     * tekinti – ez kell a titkosításhoz (Web Crypto) az interneten át működő kapcsolatnál.
     */
    private val assetLoader by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // térképként használva ne aludjon el a kijelző
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        web = WebView(this)
        setContentView(web)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            mediaPlaybackRequiresUserGesture = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        web.setBackgroundColor(0xFF11151C.toInt())
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.host == ASSET_HOST) return false
                // külső linkek a böngészőben nyíljanak meg
                if (url.scheme == "http" || url.scheme == "https") {
                    startActivity(Intent(Intent.ACTION_VIEW, url))
                    return true
                }
                return false
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState)
        } else {
            web.loadUrl(startUrl(intent))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        web.loadUrl(startUrl(intent))
    }

    /** nova://pair?online=KOD vagy nova://pair?addr=...&code=... -> a paraméterek átadása a webes felületnek */
    private fun startUrl(intent: Intent?): String {
        val base = "https://$ASSET_HOST/assets/index.html"
        val data: Uri = intent?.data ?: return base
        val online = data.getQueryParameter("online")
        if (!online.isNullOrBlank()) return base + "?online=" + Uri.encode(online)
        val addr = data.getQueryParameter("addr")
        val code = data.getQueryParameter("code")
        if (addr.isNullOrBlank() || code.isNullOrBlank()) return base
        return base + "?addr=" + Uri.encode(addr) + "&code=" + Uri.encode(code)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    companion object {
        const val ASSET_HOST = "appassets.androidplatform.net"
    }
}
