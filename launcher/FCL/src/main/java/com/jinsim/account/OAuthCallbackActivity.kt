package com.jinsim.account

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * 接收 jsl://oauth-callback?...
 *
 * 两条路：
 *  1. ticket —— 微软授权走完后端中转（/auth/ms/callback）回跳过来时带的票据。
 *     这里用它换出 session_token 并把云端账号列表整体拉回本地，网页端与 App 端共用同一条授权流。
 *  2. code —— 早期客户端 PKCE 直连留下的方式，仍然交给 OAuthBus 处理。
 *
 * 必须在 AndroidManifest 里声明 exported=true 并挂上 VIEW/BROWSABLE 的 intent-filter。
 */
class OAuthCallbackActivity : Activity() {

    private val io = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data
        if (data == null) {
            Toast.makeText(this, "回调缺少数据", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val ticket = data.getQueryParameter("ticket")
        if (!ticket.isNullOrBlank()) {
            val error = data.getQueryParameter("error")
            if (!error.isNullOrBlank()) {
                Toast.makeText(this, "授权失败：${data.getQueryParameter("message") ?: error}", Toast.LENGTH_LONG).show()
                finish()
                return
            }
            Toast.makeText(this, "授权完成，正在同步账号…", Toast.LENGTH_SHORT).show()
            io.execute { redeem(ticket) }
            return
        }

        val delivered = OAuthBus.deliver(data)
        if (!delivered) {
            val err = data.getQueryParameter("message") ?: data.getQueryParameter("error")
            Toast.makeText(
                this,
                err ?: "登录会话已失效，请回到账号页重新登录",
                Toast.LENGTH_LONG
            ).show()
        }
        finish()
        overridePendingTransition(0, 0)
    }

    private fun redeem(ticket: String) {
        val repo = AccountRepository.get(this)
        var msg: String
        try {
            val body = JSONObject().put("ticket", ticket).toString()
            val text = Http.postJson("${JslApi.BASE_URL}/auth/ticket", body)
            val json = JSONObject(text)
            val token = json.optString("session_token")
            val user = json.optJSONObject("user")
            if (token.isBlank() || user == null) throw IllegalStateException("服务端没有返回会话")

            val jinsim = JinsimAccount(
                id = user.optString("id"),
                email = user.optString("email"),
                displayName = user.optString("display_name").ifBlank { user.optString("email") },
                avatarUrl = null
            )
            repo.setJinsim(jinsim, token)

            val arr = json.optJSONArray("accounts")
            val list = ArrayList<BoundAccount>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { list.add(BoundAccount.fromJson(it)) }
                }
            }
            repo.replaceFromCloud(list)
            msg = if (list.isEmpty()) "微软账号已绑定" else "微软账号已绑定，共 ${list.size} 个附属账号"
        } catch (e: Exception) {
            msg = "同步失败：" + (e.message ?: "未知错误")
        }

        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            finish()
            overridePendingTransition(0, 0)
        }
    }
}
