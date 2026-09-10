package com.obtainium.companion

import android.content.Context
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * 拉清单原文。**只做这一件事**（规格 01 §2.1）。
 *
 * 刻意**不发 `If-None-Match`、不做 304 短路**（决策 Q3）：一个按钮永远做同一件事，
 * 永远可强制重推（例如你在 Obtainium 里误删了某行想拉回来）。
 * 代价是无变化时白点一次「继续」，而 L1 本来就要付。
 */
class MarketClient(private val context: Context, private val prefs: Prefs) {

    fun fetchRaw(): String {
        // 仅 debug 构建：改用随包内置的样例清单，便于离线开发（见 app/src/debug/assets/）
        if (BuildConfig.DEBUG && prefs.useBundledFixture) return readBundledFixture()

        val url = prefs.manifestUrl
        val conn = open(url)
        return try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw MarketException(MarketException.Kind.HTTP, "服务器返回 HTTP $code")
            }
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (body.isBlank()) throw MarketException(MarketException.Kind.EMPTY, "清单内容为空")
            body
        } catch (e: MarketException) {
            throw e
        } catch (e: IOException) {
            throw MarketException(MarketException.Kind.NETWORK, describe(e), e)
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        val conn = try {
            URL(url).openConnection() as? HttpURLConnection
                ?: throw MarketException(MarketException.Kind.NETWORK, "清单地址不是 http(s)：$url")
        } catch (e: MarketException) {
            throw e
        } catch (e: IOException) {
            throw MarketException(MarketException.Kind.NETWORK, "清单地址无法解析：$url", e)
        }
        return conn.apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "obtainium-companion/${BuildConfig.VERSION_NAME}")
        }
    }

    private fun readBundledFixture(): String = try {
        context.assets.open(FIXTURE_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (e: IOException) {
        throw MarketException(
            MarketException.Kind.NETWORK,
            "内置样例清单缺失（$FIXTURE_ASSET）—— 它只存在于 debug 构建里，请关闭该选项",
            e,
        )
    }

    private fun describe(e: IOException): String = when (e) {
        is UnknownHostException -> "无法解析域名（检查网络，或清单地址是否写错）"
        is SocketTimeoutException -> "连接超时"
        is SSLException -> "TLS 握手失败"
        else -> e.message ?: e.javaClass.simpleName
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val FIXTURE_ASSET = "fixture-manifest.json"
    }
}
