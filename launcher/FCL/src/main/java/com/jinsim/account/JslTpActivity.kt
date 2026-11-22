package com.jinsim.account

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.jinsim.account.ui.MsaLoginActivity
import com.jinsim.account.ui.PersonalCenterActivity

/**
 * 深链分发：jsl://tp?pages=xxx
 *
 * 用途：网页上「使用微软账号绑定」这类按钮，点一下直接拉起 App 对应页面，
 * 不用让用户在浏览器里自己折腾微软授权。
 *
 * 支持的页面：
 *  - bindms   → 微软账号绑定（App 内 WebView 拦截 desktop.srf，不依赖浏览器扩展）
 *  - account  → JinSim 账号页
 *  - home     → 只拉起 App，走默认首页
 */
class JslTpActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data: Uri? = intent?.data
        val page = data?.getQueryParameter("pages")?.lowercase().orEmpty()

        when (page) {
            "bindms" -> {
                // 已登录 JinSim 直接进 WebView 授权；没登录先登录，登录完成自动续上授权
                if (AccountRepository.get(this).sessionToken().isNullOrBlank()) {
                    toast("请先登录 JinSim 账号，再绑定微软")
                }
                CloudAccounts.openBind(this)
            }
            "account" -> PersonalCenterActivity.launch(this)
            "home", "" -> {
                // 只把 App 拉起来，菜单交给默认启动页
                val launch = packageManager.getLaunchIntentForPackage(packageName)
                if (launch != null) startActivity(launch) else toast("无法打开启动器")
            }
            else -> toast("不支持的跳转：$page")
        }
        finish()
        overridePendingTransition(0, 0)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
