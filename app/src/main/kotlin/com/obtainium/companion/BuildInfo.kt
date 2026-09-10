package com.obtainium.companion

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * 构建与运行环境信息。设置页展示。
 *
 * **签名指纹是这份信息里唯一有运维价值的一项**：自更新依赖「签名恒定」——
 * 一旦下次发布换了签名，覆盖安装会被系统拒绝，而设备上那个版本就再也升不上来了。
 * 把指纹显示出来，是为了让「我是不是换了 keystore」这个问题有一个当场可查的答案。
 */
object BuildInfo {

    val versionName: String get() = BuildConfig.VERSION_NAME

    val versionCode: Long get() = BuildConfig.VERSION_CODE.toLong()

    /** 完整的 SHA-256 签名指纹，冒号分隔大写十六进制。取不到返回「未知」而不是抛异常。 */
    fun signingSha256(context: Context): String = try {
        val pm = context.packageManager
        val pkg = context.packageName
        val sig = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo
                ?.apkContentsSigners
                ?.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
                .signatures
                ?.firstOrNull()
        }
        sig?.let { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toHexFingerprint() } ?: UNKNOWN
    } catch (e: Exception) {
        UNKNOWN
    }

    /** 界面上只放得下前 8 字节；完整值留在 [signingSha256]。 */
    fun shortSigningFingerprint(context: Context): String {
        val full = signingSha256(context)
        if (full == UNKNOWN) return UNKNOWN
        return full.split(':').take(8).joinToString(":") + " …"
    }

    /** 设备与 ABI —— 排查「装错了变体」时第一个要看的东西。 */
    fun deviceSummary(): String {
        val abis = AbiFilter.deviceAbis().joinToString(", ").ifBlank { "未知" }
        return "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · $abis"
    }

    private fun ByteArray.toHexFingerprint(): String =
        joinToString(":") { "%02X".format(it) }

    private const val UNKNOWN = "未知"
}
