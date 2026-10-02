package com.quotationmaker.app

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    // The web app is served from the APK's assets under a stable https origin,
    // so its saved companies and quotations (localStorage) survive app restarts.
    private val assetLoader by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    }

    private val pickFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        fileCallback?.onReceiveValue(uris)
        fileCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            textZoom = 100
            setSupportZoom(false)
        }
        WebView.setWebContentsDebuggingEnabled(true)

        webView.addJavascriptInterface(Bridge(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.host == WebViewAssetLoader.DEFAULT_DOMAIN) return false
                // Any outside link opens in the phone's browser / matching app
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, url)) }
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    pickFile.launch(params.createIntent())
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    false
                }
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl("https://${WebViewAssetLoader.DEFAULT_DOMAIN}/assets/www/index.html")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    /** Called from the web page as window.AndroidBridge.* */
    inner class Bridge {

        /** Saves the file to Downloads/Quotations and returns a message to show. */
        @JavascriptInterface
        fun saveFile(base64: String, fileName: String, mime: String): String {
            return try {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                val name = safeName(fileName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        put(MediaStore.Downloads.MIME_TYPE, mime)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Quotations")
                    }
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: return "फ़ाइल सेव नहीं हो पाई"
                    contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    "Download/Quotations में सेव हुई"
                } else {
                    val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
                    dir.mkdirs()
                    File(dir, name).writeBytes(bytes)
                    "सेव हुई: " + dir.absolutePath
                }
            } catch (e: Exception) {
                "फ़ाइल सेव नहीं हो पाई: " + (e.message ?: "error")
            }
        }

        /** Opens the Android share sheet (WhatsApp, Gmail…) with the file. */
        @JavascriptInterface
        fun shareFile(base64: String, fileName: String, mime: String): String {
            return try {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                val dir = File(cacheDir, "shared").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, safeName(fileName))
                file.writeBytes(bytes)
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runOnUiThread { startActivity(Intent.createChooser(send, "कोटेशन भेजें")) }
                ""
            } catch (e: Exception) {
                "शेयर नहीं हो पाया: " + (e.message ?: "error")
            }
        }

        private fun safeName(name: String): String =
            name.replace(Regex("[\\\\/:*?\"<>|]"), "-").take(150).ifBlank { "quotation.pdf" }
    }
}
