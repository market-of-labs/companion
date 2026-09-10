package com.obtainium.companion

/**
 * 伴侣应用自更新（01 §3.13）。
 *
 * 它和普通市场 App 走**同一条**下载/安装链路（[DownloadInstaller]），
 * 唯一区别是数据来源：`kind:"companion"` 的条目而不是用户点的那一行。
 * 这条例目不进 deep link —— 它是给本应用自己看的（01 §3.11）。
 */
object SelfUpdate {

    sealed interface State {
        /** 清单里的版本不高于当前版本。 */
        data object UpToDate : State

        data class Available(
            val versionName: String,
            val versionCode: Long,
            val ref: ApkRef,
        ) : State

        /**
         * 清单里根本没有 `kind:"companion"` 条目。
         * §3.13 明确：这**不是错误**，界面须完全静默 —— 所以它与 [Unknown] 分开，
         * 否则一个合法的清单配置会被渲染成一条警告。
         */
        data object NotConfigured : State

        /**
         * 确实配了但读不出来 —— 条目 id 对不上、versionCode 缺失、没有可用 APK 地址。
         * **必须与 [UpToDate] 区分**：把「没查到」说成「已是最新」是在撒谎。
         */
        data class Unknown(val reason: String) : State
    }

    /**
     * @param applicationId 本应用的包名，用来确认清单里那条 companion 条目确实是说我们自己。
     * @param currentVersionCode `BuildConfig.VERSION_CODE`。
     */
    fun check(manifest: Manifest, applicationId: String, currentVersionCode: Long): State {
        val companions = manifest.apps.filter { it.kind == AppEntry.KIND_COMPANION }
        if (companions.isEmpty()) return State.NotConfigured

        // 认 id 与包名一致的那条（解析器已保证 companion 至多一条）。对不上就不猜。
        val entry = companions.firstOrNull { it.id == applicationId }
            ?: return State.Unknown(
                "自更新条目的 id=\"${companions.first().id}\" 与本应用包名「$applicationId」不符"
            )

        if (entry.versionCode <= 0L) {
            return State.Unknown("自更新条目缺少 versionCode，无法比较版本")
        }
        if (entry.versionCode <= currentVersionCode) return State.UpToDate

        val ref = AbiFilter.fold(entry.apkUrls).firstOrNull()
            ?: return State.Unknown("自更新条目没有可用的 APK 地址")

        return State.Available(entry.latestVersion, entry.versionCode, ref)
    }

    /**
     * 主界面要显示的一行。**返回 null = 不显示**（§3.13：无 companion 条目时静默）。
     */
    fun describe(state: State, currentVersionName: String): String? = when (state) {
        is State.NotConfigured -> null
        is State.UpToDate -> "已是最新（$currentVersionName）"
        is State.Available -> "伴侣应用 ${state.versionName} 可用"
        is State.Unknown -> "无法判定更新：${state.reason}"
    }
}
