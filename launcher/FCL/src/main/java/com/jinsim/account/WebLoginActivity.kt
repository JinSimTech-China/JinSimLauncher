package com.jinsim.account

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * 没装 Edge 时的回退登录页。
 * 开 JS / DOM Storage / 第三方 Cookie，拦 jsl://oauth-callback 取 code。
 * 页面上方常驻提示：WebView 无法共享浏览器 Cookie，需要重新登录一次微软账号。
 */
class WebLoginActivity : Activity() {

    private lateinit var webView: WebView
    private var handled = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val banner = TextView(this).apply {
            text = "当前设备未安装 Microsoft Edge，使用内置 WebView 登录。\n" +
                    "WebView 无法共享浏览器 Cookie，需要重新输入微软账号；如需免密/单点登录请安装 Edge。"
            textSize = 12f
            setPadding(24, 24, 24, 16)
            setBackgroundColor(0xFFFFF4D6.toInt())
        }
        webView = WebView(this)
        root.addView(banner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        val url = intent.getStringExtra(EXTRA_AUTH_URL) ?: run {
            Toast.makeText(this, "缺少授权地址", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            @Suppress("DEPRECATION")
            allowFileAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = userAgentString.replace("; wv", "")
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                intercept(request.url)

            @Deprecated("兼容旧 WebView")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                intercept(Uri.parse(url))

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                url?.let { intercept(Uri.parse(it)) }
            }
        }
        webView.loadUrl(url)
    }

    private fun intercept(uri: Uri): Boolean {
        if (uri.scheme != "jsl" || uri.host != "oauth-callback") return false
        if (handled) return true
        handled = true
        val delivered = OAuthBus.deliver(uri)
        if (!delivered) Toast.makeText(this, "登录会话已失效，请重新登录", Toast.LENGTH_LONG).show()
        finish()
        return true
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_AUTH_URL = "auth_url"
    }
}
