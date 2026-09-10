package com.obtainium.companion

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 下载 APK → 交给系统安装器。用于两件事（01 §3.12 / §3.13）：
 * 引导安装 Obtainium、以及伴侣应用自更新。
 *
 * 三层防御，都很便宜：
 *  1. **重定向自己跟**（GitHub Release asset 会跳到 `objects.githubusercontent.com`，
 *     跨协议跳转不能指望 `HttpURLConnection` 自动跟）；
 *  2. **先写 `.part` 再改名** —— 中断留下的半截文件不会伪装成完整 APK；
 *  3. **校验 ZIP 魔数 `PK`** —— GitHub 出错时会返回 HTML 错误页，长度非零、内容却不是 APK。
 *     这一条挡掉的正是「下载成功但装不上」这类最难查的故障。
 */
class DownloadInstaller(private val context: Context) {

    fun download(url: String, dest: File, onProgress: (bytesRead: Long, total: Long) -> Unit = { _, _ -> }): File {
        dest.parentFile?.mkdirs()
        val part = File(dest.parentFile, dest.name + PART_SUFFIX)

        val conn = openFollowing(url)
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw MarketException(MarketException.Kind.HTTP, "下载失败：HTTP $code")

            val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
            conn.inputStream.use { input ->
                part.outputStream().buffered().use { output ->
                    val buf = ByteArray(BUFFER_BYTES)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        read += n
                        onProgress(read, total)
                    }
                }
            }

            if (total > 0 && part.length() != total) {
                throw MarketException(
                    MarketException.Kind.INSTALL,
                    "下载不完整（${part.length()} / $total 字节）",
                )
            }
            verifyLooksLikeApk(part)

            if (dest.exists()) dest.delete()
            if (!part.renameTo(dest)) {
                // 极少数文件系统上 rename 会失败（跨挂载点），退化成复制
                part.copyTo(dest, overwrite = true)
                part.delete()
            }
            return dest
        } catch (e: MarketException) {
            part.delete()
            throw e
        } catch (e: IOException) {
            part.delete()
            throw MarketException(MarketException.Kind.INSTALL, "下载中断：${e.message ?: e.javaClass.simpleName}", e)
        } finally {
            conn.disconnect()
        }
    }

    /** Android 8+ 的「安装未知应用」是**每个来源应用**单独授权的特殊权限。 */
    fun canRequestPackageInstalls(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /** 跳去系统的「允许安装未知应用」页，直接定位到本应用。 */
    fun unknownSourcesSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** 经自带的 [ApkProvider] 以 content:// + 一次性读权限把 APK 交给系统安装器。 */
    fun installIntent(apk: File): Intent {
        val uri = ApkProvider.uriFor(context.packageName, apk.name)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, ApkProvider.APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    // --- 内部 ---

    private fun openFollowing(url: String): HttpURLConnection {
        var current = url
        repeat(MAX_REDIRECTS) {
            val conn = try {
                URL(current).openConnection() as? HttpURLConnection
                    ?: throw MarketException(MarketException.Kind.NETWORK, "下载地址不是 http(s)：$current")
            } catch (e: MarketException) {
                throw e
            } catch (e: IOException) {
                throw MarketException(MarketException.Kind.NETWORK, "下载地址无法解析：$current", e)
            }

            conn.apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", "obtainium-companion/${BuildConfig.VERSION_NAME}")
            }

            val code = try {
                conn.responseCode
            } catch (e: IOException) {
                conn.disconnect()
                throw MarketException(MarketException.Kind.NETWORK, "连接失败：${e.message}", e)
            }

            if (code !in 300..399) return conn

            val location = conn.getHeaderField("Location")
            conn.disconnect()
            if (location.isNullOrBlank()) {
                throw MarketException(MarketException.Kind.HTTP, "服务器要求跳转但没给 Location")
            }
            // 相对跳转也要支持（URL(base, location) 两种都能处理）
            current = URL(URL(current), location).toString()
        }
        throw MarketException(MarketException.Kind.HTTP, "重定向超过 $MAX_REDIRECTS 次，已放弃")
    }

    /** APK 本质是 ZIP，头两个字节必为 `PK`。挡掉 HTML 错误页 / 登录页 / 限流页。 */
    private fun verifyLooksLikeApk(file: File) {
        val head = ByteArray(2)
        val read = file.inputStream().use { it.read(head) }
        if (read < 2 || head[0] != 0x50.toByte() || head[1] != 0x4B.toByte()) {
            throw MarketException(
                MarketException.Kind.INSTALL,
                "下载到的不是 APK（${file.length()} 字节，开头不是 ZIP 魔数）。" +
                    "多半是镜像地址失效、返回了错误页，或仓库是 private 而链接需要鉴权。",
            )
        }
    }

    private companion object {
        const val PART_SUFFIX = ".part"
        const val BUFFER_BYTES = 64 * 1024
        const val MAX_REDIRECTS = 6
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000
    }
}
