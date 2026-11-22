package com.jinsim.account

import android.net.Uri

/**
 * 浏览器（Edge Custom Tabs / WebView）与回调 Activity 之间的结果传递。
 * 同进程内存态即可：授权过程中应用不会被回收，万一被回收，回调页会提示重试。
 */
object OAuthBus {

    interface Listener {
        fun onCode(code: String, state: String?)
        fun onError(message: String)
    }

    @Volatile
    private var listener: Listener? = null

    @Volatile
    var expectedState: String? = null
        private set

    fun begin(state: String, l: Listener) {
        expectedState = state
        listener = l
    }

    fun cancel() {
        listener = null
        expectedState = null
    }

    /** 由 OAuthCallbackActivity 调用 */
    fun deliver(uri: Uri): Boolean {
        val l = listener ?: return false
        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            val desc = uri.getQueryParameter("error_description") ?: error
            listener = null
            l.onError(desc)
            return true
        }
        val (code, state) = MicrosoftPkce.parseCallback(uri)
        if (code.isNullOrBlank()) {
            listener = null
            l.onError("回调里没有 code")
            return true
        }
        if (expectedState != null && state != null && state != expectedState) {
            listener = null
            l.onError("state 校验失败，可能被篡改，请重试")
            return true
        }
        listener = null
        l.onCode(code, state)
        return true
    }
}
