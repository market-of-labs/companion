package com.obtainium.companion

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.TransactionTooLargeException

/**
 * 目标发现 + 精确投递。
 *
 * **为什么不能用隐式 Intent 直接 `startActivity`**：设备上可能并存多个注册了 `obtainium://`
 * 的变体（原版 / 各分支 / 你自己重新签名的）。隐式投递会让系统弹选择器，甚至默认送到错的那个上。
 * 所以先发现、记住一个包名，之后一律 `setPackage` 精确投递（01 §3.3 陷阱三）。
 *
 * 发现依赖 `AndroidManifest.xml` 里的 `<queries>`：Android 11 起包可见性受限，
 * 少了它 `queryIntentActivities` 恒返回空 —— 表现是「永远以为没装 Obtainium，反复引导安装」。
 */
class ObtainiumLauncher(private val context: Context, private val prefs: Prefs) {

    data class Target(val packageName: String, val label: String)

    sealed interface Resolution {
        /** 可投递。 */
        data class Ready(val target: Target) : Resolution

        /** 没装 / 没找到任何能接的。 */
        data object None : Resolution

        /** 多个候选且用户尚未选定 —— 必须问，不能替用户猜（01 §3.3 / Q11）。 */
        data class Ambiguous(val candidates: List<Target>) : Resolution
    }

    /**
     * 仲裁规则：**已记住且仍然有效 → 用它**；否则唯一候选自动选定；多候选交给 UI。
     */
    fun resolve(): Resolution {
        prefs.targetPackage
            ?.takeIf { it.isNotBlank() }
            ?.let { pkg ->
                discoverTargets().firstOrNull { it.packageName == pkg }?.let {
                    return Resolution.Ready(it)
                }
                // 记住了但已经不可用（被卸载 / 换成了别的变体）→ 清掉重来
                prefs.targetPackage = null
            }

        return when (val candidates = discoverTargets()) {
            emptyList<Target>() -> Resolution.None
            else -> if (candidates.size == 1) {
                Resolution.Ready(candidates.first().also { prefs.targetPackage = it.packageName })
            } else {
                Resolution.Ambiguous(candidates)
            }
        }
    }

    fun remember(target: Target) {
        prefs.targetPackage = target.packageName
    }

    /** 设备上所有能接 `obtainium://apps/...` 的应用，按标签排序、按包名去重。 */
    fun discoverTargets(): List<Target> {
        val pm = context.packageManager
        // CATEGORY_DEFAULT 不能省：Obtainium 的 intent-filter 带了它，带上才能被匹配到。
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("$SCHEME://$HOST/probe"))
            .addCategory(Intent.CATEGORY_DEFAULT)

        val resolved: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(probe, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(probe, 0)
        }

        return resolved
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
                Target(pkg, label)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label }
    }

    /** 精确投递。调用方拿到的 uri 必须已由 [DeepLinkBuilder] 剥壳并编码。 */
    fun deliver(targetPackage: String, uri: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            setPackage(targetPackage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            prefs.targetPackage = null
            throw MarketException(
                MarketException.Kind.NO_TARGET,
                "「$targetPackage」无法接收导入链接（可能已被卸载或改包名）。请重新选择目标。",
                e,
            )
        } catch (e: TransactionTooLargeException) {
            throw MarketException(
                MarketException.Kind.URI_TOO_LARGE,
                "链接太大，超出系统 Binder 事务上限。请把清单拆成多份再同步。",
                e,
            )
        } catch (e: SecurityException) {
            throw MarketException(
                MarketException.Kind.NO_TARGET,
                "系统拒绝了向「$targetPackage」的投递。",
                e,
            )
        }
    }

    private companion object {
        const val SCHEME = DeepLinkBuilder.SCHEME
        const val HOST = DeepLinkBuilder.HOST
    }
}
