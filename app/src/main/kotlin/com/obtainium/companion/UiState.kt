package com.obtainium.companion

/**
 * 主界面状态。手机与电视两个界面**消费同一个 [UiState]** ——
 * 自适应是渲染层的分支，不是两套业务逻辑（否则两份状态迟早说法不一致）。
 */
data class UiState(
    /** 正在进行的耗时动作；null = 空闲。同一时刻只会有一个（按钮在此期间禁用）。 */
    val busy: Busy? = null,
    val manifest: Manifest? = null,
    /** 解析期丢弃了什么。**不能藏起来** —— 静默降级比报错更难查。 */
    val warnings: List<String> = emptyList(),
    /** 已选定且仍然有效的投递目标。null = 没得投 （按钮会变成「安装 Obtainium」）。 */
    val target: ObtainiumLauncher.Target? = null,
    /** 多个候选、尚未选定。非空时界面必须让用户选，不能替他猜（§3.3）。 */
    val targetCandidates: List<ObtainiumLauncher.Target> = emptyList(),
    /** 安装 Obtainium 时若清单里有多个 `kind:"obtainium"` 条目，需要用户选一个（C18）。 */
    val installCandidates: List<AppEntry> = emptyList(),
    val selfUpdate: SelfUpdate.State = SelfUpdate.State.NotConfigured,
    val notice: Notice? = null,
    val lastSyncAt: Long = 0L,
    val lastSentCount: Int = 0,
    val manifestUrl: String = "",
) {
    val hasTarget: Boolean get() = target != null
    val ordinaryCount: Int get() = manifest?.ordinaryApps?.size ?: 0
    val obtainiumCandidateCount: Int get() = manifest?.obtainiumKinds?.size ?: 0
}

sealed interface Busy {
    data object Fetching : Busy
    data class Downloading(val label: String, val read: Long, val total: Long) : Busy
    data object Installing : Busy
}

/** 给用户看的一句话。`detail` 是补充说明，不是日志。 */
data class Notice(
    val text: String,
    val kind: Kind,
    val detail: String? = null,
) {
    enum class Kind { INFO, WARN, ERROR }
}

/** 状态行文案。**L4：只能陈述「已发送 N 条」，永远不能陈述「同步成功」**。 */
fun UiState.statusLine(now: Long): String = when {
    lastSyncAt <= 0L -> "尚未发送过"
    else -> "上次发送：${formatTime(lastSyncAt, now)} · $lastSentCount 条"
}

fun Busy.describe(): String = when (this) {
    Busy.Fetching -> "正在拉取清单…"
    is Busy.Downloading -> if (total > 0) {
        "正在下载 $label… ${(read * 100 / total).coerceIn(0, 100)}%"
    } else {
        "正在下载 $label… ${read / 1024} KB"
    }
    Busy.Installing -> "正在唤起系统安装器…"
}

private fun formatTime(at: Long, now: Long): String {
    val deltaMin = (now - at) / 60_000L
    return when {
        deltaMin < 1L -> "刚刚"
        deltaMin < 60L -> "${deltaMin} 分钟前"
        deltaMin < 60L * 24L -> "${deltaMin / 60L} 小时前"
        else -> java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(at))
    }
}
