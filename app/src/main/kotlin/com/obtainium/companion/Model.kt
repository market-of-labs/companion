package com.obtainium.companion

import org.json.JSONObject

/**
 * [文件名, 下载地址]。与清单里 apkUrls 的 `[["name","url"], …]` 同构（02 §2.2）。
 */
data class ApkRef(val name: String, val url: String)

/**
 * 清单里的一个应用条目（契约见 02 §2.2）。
 *
 * 只保留伴侣应用真正消费的字段；未知字段一律忽略（02 §2.9 向前兼容）。
 */
data class AppEntry(
    val id: String,
    val name: String,
    val author: String,
    /** 恒为 `https://market.invalid/<id>` 哨兵，永不联网（02 规则 8）。 */
    val url: String,
    val overrideSource: String,
    val latestVersion: String,
    /** 该版本的**全部** ABI 变体；折叠由 [AbiFilter] 在生成 payload 时做（02 §2.5）。 */
    val apkUrls: List<ApkRef>,
    val otherAssetUrls: List<ApkRef>,
    /** 原样保留并在 payload 里回传；`versionCode` 从这里取。 */
    val additionalSettings: JSONObject,
    val releaseDate: Long?,
    val changeLog: String,
    val categories: List<String>,
    /**
     * 清单侧元数据，**不是** Obtainium 字段（01 §3.11）：
     * `"obtainium"` = 启动器安装候选；`"companion"` = 本应用自更新来源；null = 普通市场 App。
     * 生成 deep link 前，带 kind 的条目必须整体剔除。
     */
    val kind: String?,
) {
    /** v1 契约必填（02 §2.3）。取不到返回 -1，调用方按「不可比较」处理。 */
    val versionCode: Long get() = additionalSettings.optLong(KEY_VERSION_CODE, -1L)

    /** 普通市场 App = 会被推给 Obtainium 的那种。 */
    val isOrdinary: Boolean get() = kind == null

    companion object {
        const val KIND_OBTAINIUM = "obtainium"
        const val KIND_COMPANION = "companion"
        const val KEY_VERSION_CODE = "versionCode"
    }
}

/** 清单信封（02 §2.1）。**注意**：它不能直接作为 deep link 的 payload，剥壳是强制的（01 §3.1）。 */
data class Manifest(
    val schemaVersion: Int,
    val exportedAt: String?,
    val generatedBy: String?,
    val apps: List<AppEntry>,
) {
    val ordinaryApps: List<AppEntry> get() = apps.filter { it.isOrdinary }
    val obtainiumKinds: List<AppEntry> get() = apps.filter { it.kind == AppEntry.KIND_OBTAINIUM }
}

/** 解析期的非致命问题。丢弃了什么必须让用户看得见，不能静默降级。 */
data class ParseWarning(val message: String)

data class ParseResult(val manifest: Manifest, val warnings: List<ParseWarning>)

/**
 * 统一的失败分类 —— UI 据此给出**可读**文案而不是堆栈（规格 01 §3.6 / C9 / C19）。
 */
class MarketException(
    val kind: Kind,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    enum class Kind {
        /** 连不上 / 超时 / DNS。 */
        NETWORK,

        /** 服务器返回非 2xx。 */
        HTTP,

        /** JSON 非法、字段缺失、schemaVersion 不认。 */
        PARSE,

        /** 拿到了但内容为空 / 无可用条目。 */
        EMPTY,

        /** URI 超出 Binder 事务缓冲区 —— 捕获后转可读错误，不崩溃（01 §3.6）。 */
        URI_TOO_LARGE,

        /** 没有可投递的 Obtainium 目标。 */
        NO_TARGET,

        /** 清单里找不到对应的条目（例如没有 kind:"obtainium"）。 */
        NO_ENTRY,

        /** 下载 / 安装链路失败。 */
        INSTALL,
    }
}

/**
 * 进程内的最近一次清单缓存。
 *
 * 存在的唯一理由：设置页需要判断「安装其他版本」入口是否显隐（01 §3.12 / Q12），
 * 而不想为此再拉一次网络。MainActivity 每次拉取成功都会写进来。
 */
object MarketCache {
    @Volatile
    var last: ParseResult? = null
}
