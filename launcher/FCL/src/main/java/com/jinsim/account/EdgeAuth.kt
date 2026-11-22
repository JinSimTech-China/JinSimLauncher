package com.jinsim.account

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * 微软登录通道选择：
 *  1. 装了 Microsoft Edge（com.microsoft.emmx）→ Custom Tabs，能复用 Edge 已登录的微软会话，SSO/MFA 都顺
 *  2. 没装 Edge → 内置 WebView，页面会提示无法共享浏览器 Cookie
 */
object EdgeAuth {

    const val EDGE_PACKAGE = "com.microsoft.emmx"

    fun hasEdge(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(EDGE_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** @return true 表示已成功唤起 Edge；false 交给 WebView 回退 */
    fun launch(context: Context, url: String): Boolean {
        if (!hasEdge(context)) return false
        return try {
            val intent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setUrlBarHidingEnabled(false)
                .build()
            intent.intent.setPackage(EDGE_PACKAGE)
            intent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.launchUrl(context, Uri.parse(url))
            true
        } catch (e: Exception) {
            false
        }
    }
}
