package com.jinsim.account

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 极简 HTTP（不引第三方库，走 HttpsURLConnection） */
object Http {

    private const val TIMEOUT_MS = 15_000

    fun postForm(url: String, form: Map<String, String>, bearer: String? = null): String {
        val body = form.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }
        return request("POST", url, "application/x-www-form-urlencoded; charset=UTF-8", body, bearer)
    }

    fun postJson(url: String, json: String, bearer: String? = null, headers: Map<String, String> = emptyMap()): String =
        request("POST", url, "application/json; charset=UTF-8", json, bearer, headers)

    fun get(url: String, bearer: String? = null, headers: Map<String, String> = emptyMap()): String =
        request("GET", url, null, null, bearer, headers)

    fun delete(url: String, bearer: String? = null): String =
        request("DELETE", url, null, null, bearer)

    private fun request(
        method: String,
        url: String,
        contentType: String?,
        body: String?,
        bearer: String?,
        headers: Map<String, String> = emptyMap()
    ): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            contentType?.let { setRequestProperty("Content-Type", it) }
            if (bearer != null) setRequestProperty("Authorization", "Bearer $bearer")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            doOutput = body != null
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { s ->
                BufferedReader(InputStreamReader(s, Charsets.UTF_8)).use { r -> r.readText() }
            } ?: ""
            if (code !in 200..299) throw IOExceptionWithBody(code, text)
            return text
        } finally {
            conn.disconnect()
        }
    }

    class IOExceptionWithBody(val status: Int, val bodyText: String) :
        IOException("HTTP $status: ${bodyText.take(300)}")
}
