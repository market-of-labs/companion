package com.obtainium.companion

import android.content.Context
import android.content.SharedPreferences

/**
 * 全部持久化状态就这几项（规格 01 §4）。
 *
 * 注意：**没有任何 Obtainium 侧的数据** —— 伴侣应用读不到 Obtainium 的行
 * （位于 `/sdcard/Android/data/<pkg>/files/`，Android 11+ 无权限），这也是
 * 「移除不传播」「删除会复活」两个结构性代价的来源（01 §5 L5 / 02 §2.9）。
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 清单地址。内置默认 = 第一期 raw 直链；部署期切 CF 只需改这一项，客户端不发版。 */
    var manifestUrl: String
        get() = sp.getString(KEY_MANIFEST_URL, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_MANIFEST_URL
        set(value) = sp.edit().putString(KEY_MANIFEST_URL, value.trim()).apply()

    /** 选定（或自动选中）的 Obtainium 包名。null = 还没定。 */
    var targetPackage: String?
        get() = sp.getString(KEY_TARGET_PACKAGE, null)
        set(value) = sp.edit().putString(KEY_TARGET_PACKAGE, value).apply()

    /** 上次投递成功的时刻。UI 只陈述「已发送 N 条」，**不**陈述「同步成功」（01 §5 L4）。 */
    var lastSyncAt: Long
        get() = sp.getLong(KEY_LAST_SYNC_AT, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_SYNC_AT, value).apply()

    /**
     * 上次投递出去的条数。持久化是为了重启后状态行仍能如实陈述，
     * 而不是退化成一个没有任何信息量的「上次同步：某个时间」。
     */
    var lastSentCount: Int
        get() = sp.getInt(KEY_LAST_SENT_COUNT, 0)
        set(value) = sp.edit().putInt(KEY_LAST_SENT_COUNT, value).apply()

    /** 仅 debug 构建可见：改用随包内置的样例清单，便于离线开发（见 MarketClient）。 */
    var useBundledFixture: Boolean
        get() = sp.getBoolean(KEY_USE_FIXTURE, false)
        set(value) = sp.edit().putBoolean(KEY_USE_FIXTURE, value).apply()

    private companion object {
        const val FILE = "companion"
        const val KEY_MANIFEST_URL = "manifest_url"
        const val KEY_TARGET_PACKAGE = "target_package"
        const val KEY_LAST_SYNC_AT = "last_sync_at"
        const val KEY_LAST_SENT_COUNT = "last_sent_count"
        const val KEY_USE_FIXTURE = "use_bundled_fixture"
    }
}
