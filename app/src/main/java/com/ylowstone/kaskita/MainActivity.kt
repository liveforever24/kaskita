package com.ylowstone.kaskita

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader

/**
 * MainActivity KasKita — by ylowstone
 * Mendukung Android 10 (API 29) hingga Android 17 (API 37):
 * - AndroidX Core SplashScreen (tanpa white flash)
 * - Edge-to-Edge WindowInsets (Status Bar, Nav Bar, & Soft Keyboard)
 * - WebViewAssetLoader (100% offline dengan localStorage permanen)
 * - Storage Access Framework untuk Export CSV & Backup/Restore JSON
 */
class MainActivity : AppCompatActivity() {

    private lateinit var rootContainer: FrameLayout
    private lateinit var webView: WebView
    private var pendingExportContent: String? = null

    // Launcher SAF untuk menyimpan file Export CSV / Backup JSON
    private val createDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
            val content = pendingExportContent
            pendingExportContent = null
            if (uri != null && content != null) {
                try {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(this, "File berhasil disimpan", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Gagal menyimpan file", Toast.LENGTH_SHORT).show()
                }
            }
        }

    // Launcher SAF untuk memilih file Restore JSON
    private val openDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null) {
                        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        webView.evaluateJavascript("window.onAndroidJsonImported('$b64')", null)
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Gagal membuka file backup", Toast.LENGTH_SHORT).show()
                }
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Pasang Splash Screen resmi Android sebelum super.onCreate
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Wajib Edge-to-Edge agar tampil rapi sempurna di Android 15, 16, dan 17
        enableEdgeToEdge()

        val isSystemDark =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val initialBg = if (isSystemDark) Color.BLACK else Color.WHITE

        rootContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(initialBg)
        }

        // Terapkan padding otomatis untuk Status Bar, Navigation Bar, & Keyboard (IME)
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // AssetLoader lokal agar localStorage berjalan stabil 100% offline
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(initialBg)
            overScrollMode = WebView.OVER_SCROLL_NEVER

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                cacheMode = WebSettings.LOAD_NO_CACHE
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
            }

            addJavascriptInterface(KasKitaBridge(), "AndroidBridge")

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    return assetLoader.shouldInterceptRequest(request.url)
                }
            }
        }

        rootContainer.addView(webView)
        setContentView(rootContainer)
        applySystemBars(isSystemDark)

        // Navigasi tombol Back Android yang ramah (tutup modal/sheet atau kembali ke Beranda dulu)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript(
                    "(window.handleAndroidBack ? window.handleAndroidBack() : 'exit')"
                ) { result ->
                    if (result == "\"exit\"") {
                        finish()
                    }
                }
            }
        })

        // Muat UI KasKita 100% offline dari assets/index.html
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        webView.evaluateJavascript(
            "if (window.onAndroidSystemThemeChanged) window.onAndroidSystemThemeChanged();",
            null
        )
    }

    private fun applySystemBars(isDark: Boolean) {
        val bgColor = if (isDark) Color.BLACK else Color.WHITE
        rootContainer.setBackgroundColor(bgColor)
        webView.setBackgroundColor(bgColor)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark
    }

    /**
     * Jembatan komunikasi aman antara antarmuka KasKita dan fitur native Android
     */
    inner class KasKitaBridge {
        @JavascriptInterface
        fun setThemeAppearance(isDark: Boolean) {
            runOnUiThread {
                applySystemBars(isDark)
            }
        }

        @JavascriptInterface
        fun saveFile(filename: String, mimeType: String, content: String) {
            runOnUiThread {
                pendingExportContent = content
                createDocumentLauncher.launch(filename)
            }
        }

        @JavascriptInterface
        fun openJsonFile() {
            runOnUiThread {
                openDocumentLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            }
        }
    }
}
