package com.obtainium.companion

import android.content.Context
import java.io.File

/**
 * 下载下来的 APK 的落盘目录 + **文件名净化**。
 *
 * 文件名来自网络清单（`apkUrls[i][0]`），属于**不可信输入**。虽然 [ApkProvider]
 * 在读取侧还有一道 canonical 路径检查，但输入侧就该拦：两边都做，是因为任意一边单独失效
 * 都会变成「任意文件写/读」。
 *
 * 放在 `cacheDir` 而不是 `filesDir`：这些 APK 用完即弃，让系统在存储紧张时能自己回收。
 */
class ApkStore(context: Context) {

    private val appContext = context.applicationContext

    val dir: File = File(appContext.cacheDir, DIR_NAME).apply { mkdirs() }

    /** 净化后的本地文件。**不保证存在** —— 只保证路径合法且落在 [dir] 内。 */
    fun fileFor(rawName: String): File = File(dir, sanitize(rawName))

    /** 已下载的 APK 列表（按修改时间倒序）。 */
    fun list(): List<File> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(SUFFIX, ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /** 清掉不在 [keep] 里的残留。安装流程收尾时调用，避免缓存里堆历史版本。 */
    fun sweep(keep: Set<String>) {
        list().forEach { f -> if (f.name !in keep) runCatching { f.delete() } }
    }

    fun sweepAll() {
        sweep(emptySet())
    }

    /**
     * 只保留 `[A-Za-z0-9._-]`，剥掉任何目录成分，强制 `.apk` 后缀，限长。
     *
     * 注意 `.` 是保留字符（版本号要用），所以不能简单地把非字母数字全换掉；
     * 代价是必须显式处理 `..` —— 见 [trimDots]。
     */
    fun sanitize(rawName: String): String {
        // 先剥目录：同时处理 POSIX 与 Windows 分隔符（清单可能是别处生成的）
        val base = rawName.substringAfterLast('/').substringAfterLast('\\')

        val cleaned = buildString(base.length) {
            base.forEach { c -> append(if (c.isLetterOrDigit() || c in ALLOWED_PUNCT) c else '_') }
        }

        val stem = cleaned
            .removeSuffixCI(SUFFIX)
            .trim('.')                       // 干掉前导/尾随点：挡住 "."、".."、"..." 与隐藏文件
            .trimEnd('_', '-', '.')          // 别让截断留在分隔符上
            .ifBlank { FALLBACK_STEM }
            .take(MAX_STEM_CHARS)
            .trimEnd('_', '-', '.')

        return (stem.ifBlank { FALLBACK_STEM }) + SUFFIX
    }

    private fun String.removeSuffixCI(suffix: String): String =
        if (endsWith(suffix, ignoreCase = true)) dropLast(suffix.length) else this

    private companion object {
        const val DIR_NAME = "apk"
        const val SUFFIX = ".apk"
        const val FALLBACK_STEM = "download"
        const val MAX_STEM_CHARS = 110
        val ALLOWED_PUNCT = charArrayOf('.', '_', '-')
    }
}
