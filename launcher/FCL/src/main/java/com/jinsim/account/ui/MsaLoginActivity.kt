package com.jinsim.account.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.jinsim.account.AccountRepository
import com.jinsim.account.CloudAccounts
import com.jinsim.account.Http
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * 微软正版登录（官方 client id，login.live.com 授权码流）。
 *
 * 为什么必须用 WebView：官方 id 只认 `https://login.live.com/oauth20_desktop.srf` 这个回调，
 * 它不能登记成自己的域名。所以做法是让 WebView 拦住这个跳转、把 URL 里的 code 抠出来，
 * 一拦到就立刻换成我们自己的「授权成功」页，用户看不到微软那张白底报错页。
 * code 换 token 与 XBL→XSTS→Minecraft 全在服务端跑，手机侧不落微软令牌。
 */
class MsaLoginActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DONE_NAME = "done_name"
        const val EXTRA_DONE_UUID = "done_uuid"
        private const val INTERCEPT_DEFAULT = "https://login.live.com/oauth20_desktop.srf"
        /** 手机版 Edge（EdgA）的 UA：不带 WebView 的 "; wv"，页面按正常手机浏览器渲染 */
        private const val EDGE_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36 EdgA/120.0.0.0"
    }

    private val io = Executors.newSingleThreadExecutor()
    private lateinit var web: WebView
    private lateinit var repo: AccountRepository
    private var sessionToken: String = ""
    private var state: String = ""
    private var interceptPrefix: String = INTERCEPT_DEFAULT
    private var handled = false                 // 防止拦截触发两次
    private var doneName: String = ""
    private var doneUuid: String = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = AccountRepository.get(this)
        sessionToken = repo.sessionToken() ?: ""

        val root = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        web = WebView(this)
        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            // UA 换成手机版 Edge：微软登录页认 WebView 特征（"; wv"、"Version/4.0"）会当不支持浏览器处理，
            // 这里整体换成手机版 Edge 的 UA，页面按正常手机浏览器渲染。
            userAgentString = EDGE_UA
        }
        // 微软登录要跨域写 cookie，不打开第三方 cookie 会一直卡在登录页
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }
        web.setBackgroundColor(Color.WHITE)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                onUrl(request.url.toString())

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = onUrl(url)
        }
        web.addJavascriptInterface(Bridge(), "Jsl")

        if (sessionToken.isBlank()) {
            showPage(failPage("还没登录 Jinsim 主账号", "先去个人中心登录，然后再回来绑微软账号"), false)
            return
        }

        web.loadDataWithBaseURL(null, loadingPage(), "text/html", "UTF-8", null)
        io.execute { requestAuthUrl() }
    }

    // ---------------------------------------------------------------- 授权

    private fun requestAuthUrl() {
        try {
            val text = Http.postJson("${com.jinsim.account.JslApi.BASE_URL}/mc/msa/start", "{}", sessionToken)
            val json = JSONObject(text)
            val authUrl = json.optString("auth_url")
            state = json.optString("state")
            if (json.optString("intercept_prefix").isNotBlank()) interceptPrefix = json.optString("intercept_prefix")
            if (authUrl.isBlank()) throw IllegalStateException("服务端没给出授权地址")
            runOnUiThread { web.loadUrl(authUrl) }
        } catch (e: Exception) {
            runOnUiThread { showPage(failPage("拿不到授权地址", e.message ?: ""), true) }
        }
    }

    /** 返回 true 表示这个 URL 已经被我们接管，WebView 不再自己跳 */
    private fun onUrl(url: String): Boolean {
        if (!url.startsWith(interceptPrefix)) return false
        if (handled) return true
        handled = true

        val code = param(url, "code")
        val err = param(url, "error")
        val errDesc = param(url, "error_description")

        if (err != null || code == null) {
            showPage(failPage("微软没有给授权码", errDesc ?: err ?: "回调里没有 code"), true)
            return true
        }
        showPage(loadingPage("正在完成登录…"), false)
        io.execute { exchange(code) }
        return true
    }

    private fun exchange(code: String) {
        try {
            // 第一步：后端只负责把授权码换成 MSA 令牌（Cloudflare 出网被 Mojang 网关拦，不能再往下走）
            val body = JSONObject().put("code", code).put("state", state).toString()
            val text = Http.postJson("${com.jinsim.account.JslApi.BASE_URL}/mc/msa/exchange", body, sessionToken)
            val json = JSONObject(text)
            val msaToken = json.optString("msa_token")
            val pendingId = json.optLong("pending_id")
            if (msaToken.isBlank() || pendingId <= 0L) throw IllegalStateException("后端没有返回 MSA 令牌")

            // 第二步：Xbox Live → XSTS → Minecraft，全部在手机上跑（手机出口不会被风控）
            runOnUiThread { showPage(loadingPage("正在登录 Xbox / Minecraft…"), false) }
            val mc = com.jinsim.account.McLogin.login(msaToken)

            // 第三步：把算出来的档案和令牌交给后端入库
            val bind = JSONObject()
                .put("pending_id", pendingId)
                .put("uuid", mc.uuid)
                .put("name", mc.name)
                .put("mc_token", mc.accessToken)
                .toString()
            val bound = JSONObject(
                Http.postJson("${com.jinsim.account.JslApi.BASE_URL}/accounts/mc-bind", bind, sessionToken)
            )
            doneName = bound.optString("name").ifBlank { mc.name }
            doneUuid = bound.optString("uuid").ifBlank { mc.uuid }
            // 关键：立刻写进 FCL 原生账号仓库，返回账号页就能看到并可选中启动
            CloudAccounts.addBound(
                this,
                bound.optLong("account_id"),
                doneName,
                doneUuid,
                mc.accessToken,
                sessionToken
            )
            runOnUiThread { showPage(successPage(doneName, doneUuid), false) }
        } catch (e: Exception) {
            runOnUiThread { showPage(failPage("绑定微软账号失败", e.message ?: ""), true) }
        }
    }

    private fun finishOk() {
        runCatching { com.tungsten.fcl.ui.UIManager.instance.accountUI.loadAccounts() }
        setResult(RESULT_OK, Intent().putExtra(EXTRA_DONE_NAME, doneName).putExtra(EXTRA_DONE_UUID, doneUuid))
        finish()
    }

    private fun param(url: String, key: String): String? =
        runCatching { java.net.URL(url).query?.split("&")?.firstOrNull { it.substringBefore("=") == key }?.substringAfter("=") }
            .getOrNull()
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") }

    private fun showPage(html: String, @Suppress("UNUSED_PARAMETER") retryable: Boolean) {
        web.visibility = View.VISIBLE
        web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    /** 页面上的按钮只负责关掉或重来，不做任何网络请求 */
    inner class Bridge {
        @JavascriptInterface
        fun done() = runOnUiThread { finishOk() }

        @JavascriptInterface
        fun close() = runOnUiThread { finish() }

        @JavascriptInterface
        fun retry() = runOnUiThread {
            handled = false
            web.loadDataWithBaseURL(null, loadingPage(), "text/html", "UTF-8", null)
            io.execute { requestAuthUrl() }
        }
    }

    // ---------------------------------------------------------------- 页面

    private fun shell(inner: String) = """
        <!doctype html><html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <style>
          body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;
               font-family:-apple-system,"PingFang SC",sans-serif;
               background:linear-gradient(180deg,#EFF3FA,#E8F5FA);color:#1B1B22}
          .card{background:#fff;border-radius:18px;padding:28px 22px;width:84%;max-width:420px;
                box-shadow:0 10px 30px rgba(65,74,108,.12);text-align:center}
          .ic{width:64px;height:64px;border-radius:20px;margin:0 auto 16px;display:flex;
              align-items:center;justify-content:center;font-size:30px;color:#fff}
          h1{font-size:17px;margin:0 0 8px}
          p{font-size:13px;color:#6B7080;line-height:1.7;margin:0 0 6px;word-break:break-all}
          .btn{display:block;margin-top:18px;padding:14px;border-radius:14px;background:#99DAD6;
               color:#fff;font-size:15px;font-weight:600;text-decoration:none}
          .btn.ghost{background:#E8EFFD;color:#99DAD6;margin-top:10px}
          .spin{width:34px;height:34px;border-radius:50%;margin:0 auto 16px;
                border:3px solid #DCE5FB;border-top-color:#99DAD6;animation:r 900ms linear infinite}
          @keyframes r{to{transform:rotate(360deg)}}
        </style></head><body><div class="card">$inner</div></body></html>
    """

    private fun loadingPage(text: String = "正在跳转微软登录…") = shell("""
        <div class="spin"></div>
        <p>$text</p>
    """)

    private fun successPage(name: String, uuid: String) = shell("""
        <div class="ic" style="background:linear-gradient(140deg,#C2F0EC,#99DAD6)">✓</div>
        <h1>授权成功</h1>
        <p>${if (name.isBlank()) "微软账号已绑定到 JinSim" else "玩家名：$name"}</p>
        <p>${if (uuid.isBlank()) "" else "UUID：$uuid"}</p>
        <a class="btn" href="javascript:Jsl.done()">完成</a>
    """)

    private fun failPage(title: String, detail: String) = shell("""
        <div class="ic" style="background:linear-gradient(140deg,#FF7A9A,#E5486B)">!</div>
        <h1>$title</h1>
        <p>$detail</p>
        <a class="btn" href="javascript:Jsl.retry()">重新登录</a>
        <a class="btn ghost" href="javascript:Jsl.close()">先不绑了</a>
    """)

    override fun onDestroy() {
        io.shutdownNow()
        runCatching { web.destroy() }
        super.onDestroy()
    }
}
