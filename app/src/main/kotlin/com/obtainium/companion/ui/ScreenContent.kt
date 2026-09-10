package com.obtainium.companion.ui

import com.obtainium.companion.AppEntry
import com.obtainium.companion.BuildInfo
import com.obtainium.companion.Notice
import com.obtainium.companion.SelfUpdate
import com.obtainium.companion.UiState
import com.obtainium.companion.describe
import com.obtainium.companion.statusLine

/**
 * 主界面的**呈现内容**，与设计系统无关。
 *
 * 存在的理由：手机和电视两套渲染必须说**同一句话**。把文案和状态判定收敛到这一个函数后，
 * 两个界面各自只剩排版，不可能出现「手机上写着 A、电视上写着 B」这种漂移。
 */
data class ScreenContent(
    val title: String,
    val statusLine: String,
    val manifestUrlLine: String,
    val primaryLabel: String,
    val primaryEnabled: Boolean,
    val selfUpdate: ActionLine?,
    val targetLine: String?,
    val appCountLine: String,
    val appRows: List<AppRow>,
    val warnings: List<String>,
    val busyText: String?,
    val busyFraction: Float?,
    val notice: Notice?,
    val hint: String?,
    /** 多于一个可选目标时必须让用户选（§3.3）。空 = 不需要问。 */
    val targetCandidates: List<com.obtainium.companion.ObtainiumLauncher.Target>,
    /** 多于一个 `kind:"obtainium"` 候选时让用户选（C18）。空 = 不需要问。 */
    val installCandidates: List<AppEntry>,
)

data class ActionLine(val text: String, val actionLabel: String)

data class AppRow(val id: String, val name: String, val detail: String, val badge: String?)

fun buildScreenContent(state: UiState, now: Long, title: String): ScreenContent {
    val busy = state.busy
    val target = state.target

    val primaryLabel = when {
        busy != null -> "请稍候…"
        target == null -> "安装 Obtainium"
        else -> "同步"
    }

    val targetLine = when {
        target != null -> "目标：${target.label}（${target.packageName}）"
        state.targetCandidates.size > 1 -> "发现 ${state.targetCandidates.size} 个可接收的 Obtainium，请先选一个"
        state.manifest != null -> "本机没有检测到 Obtainium"
        else -> null
    }

    val selfUpdate = SelfUpdate.describe(state.selfUpdate, BuildInfo.versionName)?.let { line ->
        if (state.selfUpdate is SelfUpdate.State.Available) {
            ActionLine(line, "更新")
        } else {
            ActionLine(line, "")
        }
    }

    val hint = when {
        state.manifest == null -> null
        state.ordinaryCount == 0 ->
            "清单里 ${state.appCount()} 条全部带 kind，没有可推送给 Obtainium 的应用。"
        else -> null
    }

    return ScreenContent(
        // 标题来自 @string/app_name，不在这里另写一份字面量 —— 应用名只该有一个出处。
        title = title,
        statusLine = state.statusLine(now),
        manifestUrlLine = compactUrl(state.manifestUrl),
        primaryLabel = primaryLabel,
        primaryEnabled = busy == null,
        selfUpdate = selfUpdate,
        targetLine = targetLine,
        appCountLine = if (state.manifest == null) {
            "清单尚未加载"
        } else {
            "清单 ${state.appCount()} 条 · 可推送 ${state.ordinaryCount} 条"
        },
        appRows = state.manifest?.apps.orEmpty().map { it.toRow() },
        warnings = state.warnings,
        busyText = busy?.describe(),
        busyFraction = busy?.fraction(),
        notice = state.notice,
        hint = hint,
        targetCandidates = state.targetCandidates,
        installCandidates = state.installCandidates,
    )
}

private fun UiState.appCount(): Int = manifest?.apps?.size ?: 0

private fun com.obtainium.companion.Busy.fraction(): Float? = when (this) {
    is com.obtainium.companion.Busy.Downloading -> if (total > 0) {
        (read.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    } else {
        null
    }
    else -> null
}

private fun AppEntry.toRow(): AppRow {
    val badge = when (kind) {
        AppEntry.KIND_OBTAINIUM -> "启动器"
        AppEntry.KIND_COMPANION -> "本应用"
        else -> null
    }
    // 带 kind 的条目不进 Obtainium，把这件事直接写在行上，免得用户以为漏推了。
    val detail = buildString {
        append(author)
        if (latestVersion.isNotBlank()) append(" · v").append(latestVersion)
        if (badge != null) append(" · 仅本机元数据，不推送给 Obtainium")
    }
    return AppRow(id = id, name = name, detail = detail, badge = badge)
}

/** 清单地址可能很长，状态区只放得下开头一段 —— 完整值在设置页。 */
private fun compactUrl(url: String): String =
    if (url.length <= 58) url else url.take(34) + "…" + url.takeLast(20)
