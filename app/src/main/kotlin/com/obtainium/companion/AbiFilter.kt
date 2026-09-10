package com.obtainium.companion

import android.os.Build
import org.json.JSONArray

/**
 * 按设备 ABI 折叠 `apkUrls`。
 *
 * **这是路线 C 下必须由伴侣应用承担的一步。** 原版 Obtainium 的 `filterApksByArch`
 * 只在 `getApp()` 路径执行，而 deep-link 导入走 `App.fromJson`、**不经过它** ——
 * 实测导入后 `apkUrls` 保留全部变体、`preferredApkIndex=0`（= universal），
 * 在 armeabi-v7a 设备上会装错包。
 *
 * 同一套折叠被两处复用（01 §3.10）：生成同步 payload、以及挑选要下载的 APK。
 * 语义与原版 `filterApksByArch` 等价（精确匹配优先 → 退 universal → 退全量），只是执行者换人。
 */
object AbiFilter {

    /** 02 §2.4 固定的 abi token 集。 */
    val KNOWN_ABIS = listOf("universal", "arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    private val KNOWN_SET = KNOWN_ABIS.toSet()
    private const val APK_SUFFIX = ".apk"
    private const val UNIVERSAL = "universal"

    /**
     * 从文件名尾缀取 abi token —— `…-{abi}.apk`。
     *
     * 用**尾缀**而不是"按 `-` 切第几段"：即使 appId 或 version 里含 `-` 也不歧义（02 §2.4）。
     * 取不到返回 null（文件名不合规，调用方跳过而非猜测）。
     */
    fun abiOf(fileName: String): String? {
        if (!fileName.endsWith(APK_SUFFIX)) return null
        val stem = fileName.substring(0, fileName.length - APK_SUFFIX.length)
        val token = stem.substringAfterLast('-', missingDelimiterValue = "")
        return token.takeIf { it in KNOWN_SET }
    }

    /**
     * 02 §2.5 的折叠算法。
     *
     * @param deviceAbis 设备 ABI，**有序、最优先在前**（= `Build.SUPPORTED_ABIS`）。
     * @return 折叠后的列表：命中则单个；既无匹配也无 universal 则**原样返回全量**（规则 5）。
     */
    fun fold(refs: List<ApkRef>, deviceAbis: List<String> = deviceAbis()): List<ApkRef> {
        if (refs.size <= 1) return refs

        for (abi in deviceAbis) {
            refs.firstOrNull { abiOf(it.name) == abi }?.let { return listOf(it) }
        }
        refs.firstOrNull { abiOf(it.name) == UNIVERSAL }?.let { return listOf(it) }

        return refs
    }

    /** 有序：`Build.SUPPORTED_ABIS` 的首项是最优先的 ABI。 */
    fun deviceAbis(): List<String> = Build.SUPPORTED_ABIS.toList()

    /**
     * 折叠成单个时恒为 index 0；保留全量时也恒为 0（02 §2.5 规则 5/6）。
     * 所以这个值**总是 0** —— 保留成函数是为了让契约点显式可见，而不是散在调用处。
     */
    fun preferredIndexAfterFold(): Int = 0

    internal fun toPairsJson(refs: List<ApkRef>): String =
        JSONArray().apply {
            refs.forEach { put(JSONArray().put(it.name).put(it.url)) }
        }.toString()
}
