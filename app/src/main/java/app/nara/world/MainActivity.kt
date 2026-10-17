package app.nara.world

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.io.OutputStreamWriter

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var pendingBackup: String? = null

    companion object {
        private const val REQUEST_OPEN_FILE = 1001
        private const val REQUEST_SAVE_BACKUP = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // Keep the packaged app on its local page. HTTPS requests from fetch() still work.
                return request.url.scheme != "file" && request.url.scheme != "https" && request.url.scheme != "about"
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.fileChooserCallback?.onReceiveValue(null)
                this@MainActivity.fileChooserCallback = filePathCallback
                return try {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    startActivityForResult(intent, REQUEST_OPEN_FILE)
                    true
                } catch (_: Exception) {
                    this@MainActivity.fileChooserCallback?.onReceiveValue(null)
                    this@MainActivity.fileChooserCallback = null
                    Toast.makeText(this@MainActivity, "Tidak bisa membuka pemilih file.", Toast.LENGTH_SHORT).show()
                    false
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Route the existing JSON export button to Android's Save As dialog.
                webView.evaluateJavascript(
                    "window.exportData=function(){AndroidBridge.saveBackup(JSON.stringify({...state,running:false},null,2));};",
                    null
                )
            }
        }

        if (savedInstanceState == null) {
            webView.loadUrl("file:///android_asset/index.html")
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    private inner class AndroidBridge {
        @JavascriptInterface
        fun saveBackup(json: String) {
            runOnUiThread {
                pendingBackup = json
                try {
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/json"
                        putExtra(Intent.EXTRA_TITLE, "naraapp-world-backup.json")
                    }
                    startActivityForResult(intent, REQUEST_SAVE_BACKUP)
                } catch (_: Exception) {
                    pendingBackup = null
                    Toast.makeText(this@MainActivity, "Tidak bisa membuka dialog penyimpanan.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    @Deprecated("Legacy activity result API supports the minimum Android version without extra dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OPEN_FILE) {
            val callback = fileChooserCallback
            fileChooserCallback = null
            if (resultCode == RESULT_OK && data?.data != null) {
                callback?.onReceiveValue(arrayOf(data.data!!))
            } else {
                callback?.onReceiveValue(null)
            }
        } else if (requestCode == REQUEST_SAVE_BACKUP) {
            val uri = data?.data
            val content = pendingBackup
            pendingBackup = null
            if (resultCode == RESULT_OK && uri != null && content != null) {
                try {
                    contentResolver.openOutputStream(uri)?.use { stream ->
                        OutputStreamWriter(stream, Charsets.UTF_8).use { it.write(content) }
                    }
                    Toast.makeText(this, "Backup NaraApp berhasil disimpan.", Toast.LENGTH_LONG).show()
                } catch (_: Exception) {
                    Toast.makeText(this, "Gagal menyimpan backup.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("AndroidBridge")
            webView.destroy()
        }
        super.onDestroy()
    }
}
