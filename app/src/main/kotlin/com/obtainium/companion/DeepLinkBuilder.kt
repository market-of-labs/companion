package com.obtainium.companion

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * 组装投递用的 `obtainium://apps/<payload>`。
 *
 * 三条不能错的地方，每一条错了都是**静默失败**或**装错包**：
 *
 * 1. **剥壳**：payload 必须是**裸数组**。把信封 `{"schemaVersion":…,"apps":[…]}` 整个塞进去，
 *    Obtainium 侧报 `failedToImport: type '_Map<String, dynamic>' is not a subtype of type 'List<dynamic>'`
 *    —— 而且是在它自己的 UI 里报，本应用看不到（01 §3.1 陷阱一）。
 * 2. **编码**：必须用 `android.net.Uri.encode`。`URLEncoder.encode` 是 `application/x-www-form-urlencoded`，
 *    会把空格变成 `+`，而 URI 里 `+` 是字面加号 —— 解析出来的 JSON 就坏了（01 §3.1 陷阱二）。
 * 3. **ABI 折叠**：deep-link 导入走 `App.fromJson`，**绕过** Obtainium 的 `filterApksByArch`，
 *    所以这一步必须由我们做完（[AbiFilter]）。
 */
object DeepLinkBuilder {

    const val SCHEME = "obtainium"
    const val HOST = "apps"

    /**
     * 预检阈值：Binder 事务缓冲区是 **1 MB 且全进程共享**，不是每个 Intent 一份。
     * 超了会抛 TransactionTooLargeException（[ObtainiumLauncher] 也兜了一层），
     * 但先在这里拦下来，能给出「去分包」这种可操作的文案而不是一个崩溃。
     */
    private const val MAX_URI_CHARS = 400_000

    data class BuiltLink(
        val uri: String,
        /** 实际推出去的条数（= 普通条目数，kind 条目已被剔除）。 */
        val sentCount: Int,
        /** 因为带 kind 而被剔除的条数（obtainium 候选 + companion 自更新源）。 */
        val strippedCount: Int,
        val jsonChars: Int,
        val uriChars: Int,
    )

    fun build(manifest: Manifest): BuiltLink {
        val ordinary = manifest.ordinaryApps
        val stripped = manifest.apps.size - ordinary.size

        if (ordinary.isEmpty()) {
            throw MarketException(
                MarketException.Kind.NO_ENTRY,
                "清单里没有可同步的应用（$stripped 条均带 kind，已全部剔除）",
            )
        }

        val payload = JSONArray()
        ordinary.forEach { payload.put(toObtainiumJson(it)) }
        val json = payload.toString()

        // 必须 Uri.encode：URLEncoder.encode 会把空格编码成 '+'，URI 里 '+' 是字面量，JSON 就此损坏。
        val uri = "$SCHEME://$HOST/${Uri.encode(json)}"

        if (uri.length > MAX_URI_CHARS) {
            throw MarketException(
                MarketException.Kind.URI_TOO_LARGE,
                "本次要推送 ${ordinary.size} 条，URI 长度 ${uri.length} 已超上限 $MAX_URI_CHARS。" +
                    "请把清单拆成多份（注意 Binder 事务缓冲区是全进程共享的 1 MB，不是每份 Intent 一份）。",
            )
        }

        return BuiltLink(
            uri = uri,
            sentCount = ordinary.size,
            strippedCount = stripped,
            jsonChars = json.length,
            uriChars = uri.length,
        )
    }

    /** 02 §2.8 规则 4 的最小字段集 —— 不多不少。 */
    private fun toObtainiumJson(e: AppEntry): JSONObject {
        val folded = AbiFilter.fold(e.apkUrls)
        return JSONObject().apply {
            put("id", e.id)
            put("name", e.name)
            put("author", e.author)
            put("url", e.url)
            put("overrideSource", e.overrideSource)
            put("latestVersion", e.latestVersion)
            // 这三个在 Obtainium 里是 jsonDecode 出来的**字符串**，不是嵌套对象（02 §2.2）。
            put("apkUrls", AbiFilter.toPairsJson(folded))
            put("otherAssetUrls", AbiFilter.toPairsJson(e.otherAssetUrls))
            put("additionalSettings", e.additionalSettings.toString())
            put("preferredApkIndex", AbiFilter.preferredIndexAfterFold())
            put("changeLog", e.changeLog)
            put("categories", JSONArray(e.categories))
            // 02 §2.8 规则 8：空/缺省 releaseDate 合法，缺了就整个不发，别塞 null。
            e.releaseDate?.let { put("releaseDate", it) }
        }
    }
}
