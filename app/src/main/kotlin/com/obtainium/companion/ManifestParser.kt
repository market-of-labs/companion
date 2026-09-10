package com.obtainium.companion

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * 信封解析 + 02 §2.8 校验。
 *
 * 容错口径（规格 01 §4）：**逐条校验，坏条目丢弃并记 warning，不让一条烂数据废掉整份清单**；
 * 但若一条可用的都不剩，那就是硬失败 —— 静默产出空 payload 比报错危险得多。
 * 丢弃了什么必须让用户看得见，见 [ParseResult.warnings]。
 */
object ManifestParser {

    const val SUPPORTED_SCHEMA_VERSION = 2

    /** 哨兵源地址前缀（02 规则 8）。RFC 2606 保留 TLD，规范保证永不解析。 */
    const val SENTINEL_PREFIX = "https://market.invalid/"

    fun parse(json: String): ParseResult {
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw MarketException(MarketException.Kind.PARSE, "清单不是合法 JSON：${e.message}", e)
        }

        val schema = root.optInt("schemaVersion", -1)
        if (schema != SUPPORTED_SCHEMA_VERSION) {
            throw MarketException(
                MarketException.Kind.PARSE,
                "清单 schemaVersion=$schema，本应用只认 $SUPPORTED_SCHEMA_VERSION",
            )
        }

        val arr = root.optJSONArray("apps")
            ?: throw MarketException(MarketException.Kind.PARSE, "清单缺少 apps 数组")
        if (arr.length() == 0) throw MarketException(MarketException.Kind.EMPTY, "清单里没有任何应用")

        val warnings = ArrayList<ParseWarning>()
        val entries = ArrayList<AppEntry>(arr.length())
        val seenIds = HashSet<String>()
        var companionTaken = false

        for (i in 0 until arr.length()) {
            val ordinal = i + 1
            val obj = arr.optJSONObject(i)
            if (obj == null) {
                warnings += ParseWarning("第 $ordinal 项不是对象，已跳过")
                continue
            }

            val entry = try {
                parseEntry(obj) { warnings += ParseWarning(it) }
            } catch (e: Exception) {
                warnings += ParseWarning("第 $ordinal 项无效（${e.message}），已跳过")
                continue
            }

            if (!seenIds.add(entry.id)) {
                warnings += ParseWarning("id=${entry.id} 重复，已跳过后一条")
                continue
            }

            // 02 §2.8 规则 9：kind:"companion" 至多一条，多于一条取第一条
            if (entry.kind == AppEntry.KIND_COMPANION) {
                if (companionTaken) {
                    warnings += ParseWarning("kind=\"companion\" 多于一条，只取第一条")
                    continue
                }
                companionTaken = true
            }

            if (entry.url != SENTINEL_PREFIX + entry.id) {
                warnings += ParseWarning("${entry.id} 的 url 不是哨兵地址（02 规则 8），仍按原值导入")
            }

            entries += entry
        }

        if (entries.isEmpty()) {
            throw MarketException(MarketException.Kind.PARSE, "清单里没有一条可用条目")
        }

        return ParseResult(
            manifest = Manifest(
                schemaVersion = schema,
                exportedAt = root.optStringOrNull("exportedAt"),
                generatedBy = root.optStringOrNull("generatedBy"),
                apps = entries,
            ),
            warnings = warnings,
        )
    }

    private fun parseEntry(o: JSONObject, warn: (String) -> Unit): AppEntry {
        val id = o.requireString("id")
        val name = o.requireString("name")
        val author = o.requireString("author")
        val url = o.requireString("url")
        val latestVersion = o.requireString("latestVersion")

        val apkUrls = parsePairs(o.requireString("apkUrls"), "apkUrls")
        if (apkUrls.isEmpty()) throw IllegalArgumentException("apkUrls 为空")

        val settingsRaw = o.requireString("additionalSettings")
        val settings = try {
            JSONObject(settingsRaw)
        } catch (e: JSONException) {
            throw IllegalArgumentException("additionalSettings 不是合法 JSON 对象")
        }
        if (settings.optLong(AppEntry.KEY_VERSION_CODE, -1L) <= 0L) {
            warn("$id 缺少 versionCode（02 规则 6 要求必填），自更新判定对它无效")
        }

        // kind 是清单侧元数据（01 §3.11）。未知取值按普通条目处理，但不能静默。
        val rawKind = o.optStringOrNull("kind")
        val kind = when (rawKind) {
            null -> null
            AppEntry.KIND_OBTAINIUM, AppEntry.KIND_COMPANION -> rawKind
            else -> {
                warn("$id 的 kind=\"$rawKind\" 不是已知取值，按普通条目处理")
                null
            }
        }

        return AppEntry(
            id = id,
            name = name,
            author = author,
            url = url,
            // 02 §2.2：缺失会让每行都走一次失败的源解析（异常被吞，但纯属白费）
            overrideSource = o.optStringOrNull("overrideSource") ?: "HTML",
            latestVersion = latestVersion,
            apkUrls = apkUrls,
            otherAssetUrls = o.optStringOrNull("otherAssetUrls")?.let { parsePairs(it, "otherAssetUrls") }
                ?: emptyList(),
            additionalSettings = settings,
            releaseDate = if (o.has("releaseDate") && !o.isNull("releaseDate")) o.optLong("releaseDate") else null,
            changeLog = o.optString("changeLog", ""),
            categories = o.optJSONArray("categories").toStringList(),
            kind = kind,
        )
    }

    /** `[["name","url"], …]` —— 清单里它是**字符串**，不是数组（02 §2.2）。 */
    private fun parsePairs(raw: String, field: String): List<ApkRef> {
        val arr = try {
            JSONArray(raw)
        } catch (e: JSONException) {
            throw IllegalArgumentException("$field 不是合法 JSON 数组")
        }
        val out = ArrayList<ApkRef>(arr.length())
        for (i in 0 until arr.length()) {
            val pair = arr.optJSONArray(i)
                ?: throw IllegalArgumentException("$field[$i] 不是 [name,url] 对")
            if (pair.length() < 2) throw IllegalArgumentException("$field[$i] 长度不足 2")
            val n = pair.optString(0, "")
            val u = pair.optString(1, "")
            if (n.isBlank() || u.isBlank()) throw IllegalArgumentException("$field[$i] 的 name/url 为空")
            out += ApkRef(n, u)
        }
        return out
    }

    private fun JSONObject.requireString(key: String): String {
        if (!has(key) || isNull(key)) throw IllegalArgumentException("缺少字段 $key")
        val v = optString(key, "")
        if (v.isBlank()) throw IllegalArgumentException("字段 $key 为空")
        return v
    }
}

internal fun JSONObject?.optStringOrNull(key: String): String? =
    if (this == null || !has(key) || isNull(key)) null else optString(key, "").takeIf { it.isNotEmpty() }

internal fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    val out = ArrayList<String>(length())
    for (i in 0 until length()) {
        val s = optString(i, "")
        if (s.isNotEmpty()) out += s
    }
    return out
}
