package com.obtainium.companion

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 三个职责（同步 / 引导安装 / 自更新）的唯一状态机。手机与电视共用。
 *
 * 几个刻意的选择：
 * - **每次「同步」都重新拉清单**（Q3）。不复用启动时那份 —— 一个按钮永远做同一件事，
 *   永远可强制重推（例如某条被误删后想拉回来）。代价是白点一次「继续」，而 L1 本来就要付。
 * - **下载与安装链路只有一条**，安装 Obtainium 与自更新共用（§3.12 / §3.13）。
 * - **同步结果不可知**（L4）：拉起 Obtainium 后拿不到任何 result，
 *   所以状态只说「已发送 N 条」，并把「还没导入完」写进提示里，让用户知道还要点一次。
 */
class MarketViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val client = MarketClient(app, prefs)
    private val launcher = ObtainiumLauncher(app, prefs)
    private val downloader = DownloadInstaller(app)
    private val store = ApkStore(app)

    private val _state = MutableStateFlow(
        UiState(
            manifestUrl = prefs.manifestUrl,
            lastSyncAt = prefs.lastSyncAt,
            lastSentCount = prefs.lastSentCount,
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** 因缺少「安装未知应用」授权而搁置的下载；授权返回后自动续上（C15）。 */
    private var pendingInstall: PendingInstall? = null

    /** 是否刚把用户送去系统安装器 —— 用来区分「返回」与「首次进入」（§3.12 步骤 6）。 */
    private var awaitingInstallerReturn = false

    private data class PendingInstall(val ref: ApkRef, val label: String, val version: String)

    init {
        refresh()
    }

    // ---------------------------------------------------------------- 拉取

    /** 启动时走一遍：拉清单 → 自更新比对 → 目标发现（§2 启动时序）。 */
    fun refresh() {
        if (_state.value.busy != null) return
        viewModelScope.launch {
            _state.update { it.copy(busy = Busy.Fetching, notice = null) }
            try {
                val parsed = withContext(Dispatchers.IO) {
                    ManifestParser.parse(client.fetchRaw())
                }
                MarketCache.last = parsed
                _state.update {
                    it.copy(
                        busy = null,
                        manifest = parsed.manifest,
                        warnings = parsed.warnings.map { w -> w.message },
                        selfUpdate = SelfUpdate.check(parsed.manifest, appId(), BuildInfo.versionCode),
                        manifestUrl = prefs.manifestUrl,
                    )
                }
            } catch (e: Exception) {
                // C9：拉不到清单时，自更新检查静默跳过（不覆盖已有的 selfUpdate），其余给出可读错误。
                _state.update { it.copy(busy = null, notice = noticeFor(e)) }
            }
            rescanTargets()
        }
    }

    // ---------------------------------------------------------------- 同步

    /** 主按钮：有目标就同步，没目标就转为「安装 Obtainium」（C8/C14）。 */
    fun primaryAction() {
        val target = _state.value.target
        if (target == null) installObtainium() else sync(target)
    }

    fun sync(target: ObtainiumLauncher.Target) {
        if (_state.value.busy != null) return
        viewModelScope.launch {
            _state.update { it.copy(busy = Busy.Fetching, notice = null) }
            try {
                val (parsed, link) = withContext(Dispatchers.IO) {
                    val fresh = ManifestParser.parse(client.fetchRaw())
                    val built = DeepLinkBuilder.build(fresh.manifest)
                    fresh to built
                }
                MarketCache.last = parsed

                // 投递必须回主线程：startActivity 对 ViewModel 而言是外部副作用，别在 IO 线程上发。
                withContext(Dispatchers.Main) { launcher.deliver(target.packageName, link.uri) }

                prefs.lastSyncAt = System.currentTimeMillis()
                prefs.lastSentCount = link.sentCount
                _state.update {
                    it.copy(
                        busy = null,
                        manifest = parsed.manifest,
                        warnings = parsed.warnings.map { w -> w.message },
                        selfUpdate = SelfUpdate.check(parsed.manifest, appId(), BuildInfo.versionCode),
                        lastSyncAt = prefs.lastSyncAt,
                        lastSentCount = link.sentCount,
                        notice = Notice(
                            text = "已向「${target.label}」发送 ${link.sentCount} 条",
                            kind = Notice.Kind.INFO,
                            detail = buildString {
                                append("Obtainium 会弹确认框，点「继续」才算导入完成。")
                                if (link.strippedCount > 0) {
                                    append("已剔除 ${link.strippedCount} 条 kind 条目（启动器/自更新元数据不推给 Obtainium）。")
                                }
                            },
                        ),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = null, notice = noticeFor(e)) }
            }
        }
    }

    // ---------------------------------------------------------------- 目标发现

    fun rescanTargets() {
        when (val r = launcher.resolve()) {
            is ObtainiumLauncher.Resolution.Ready ->
                _state.update { it.copy(target = r.target, targetCandidates = emptyList()) }
            is ObtainiumLauncher.Resolution.None ->
                _state.update { it.copy(target = null, targetCandidates = emptyList()) }
            is ObtainiumLauncher.Resolution.Ambiguous ->
                _state.update { it.copy(target = null, targetCandidates = r.candidates) }
        }
    }

    fun chooseTarget(target: ObtainiumLauncher.Target) {
        launcher.remember(target)
        _state.update { it.copy(target = target, targetCandidates = emptyList()) }
    }

    // ---------------------------------------------------------------- 安装

    fun installObtainium() {
        val candidates = _state.value.manifest?.obtainiumKinds.orEmpty()
        when {
            candidates.isEmpty() -> _state.update {
                it.copy(
                    notice = Notice(
                        text = "清单里没有 kind=\"obtainium\" 的条目",
                        kind = Notice.Kind.WARN,
                        detail = "无法自动安装。请手动侧载一个 Obtainium，或让市场维护者在清单里加上该条目。",
                    )
                )
            }
            // Q10：只有一个候选就直接下装，不弹选择框。
            candidates.size == 1 -> startDownloadInstall(candidates.first(), "Obtainium")
            else -> _state.update { it.copy(installCandidates = candidates) }
        }
    }

    fun chooseInstallCandidate(entry: AppEntry) {
        _state.update { it.copy(installCandidates = emptyList()) }
        startDownloadInstall(entry, entry.name)
    }

    fun dismissInstallCandidates() {
        _state.update { it.copy(installCandidates = emptyList()) }
    }

    /**
     * 「安装其他版本」入口（Q12）：只在清单里有多于一个 `kind:"obtainium"` 时才有意义。
     * 交由设置页复用 [chooseInstallCandidate]。
     */
    fun requestOtherObtainiumVersion() {
        val candidates = _state.value.manifest?.obtainiumKinds.orEmpty()
        if (candidates.size > 1) _state.update { it.copy(installCandidates = candidates) }
    }

    fun startSelfUpdate() {
        val s = _state.value.selfUpdate
        if (s !is SelfUpdate.State.Available) return
        startDownloadInstall(
            PendingInstall(s.ref, "伴侣应用", s.versionName),
        )
    }

    private fun startDownloadInstall(entry: AppEntry, label: String) {
        val ref = AbiFilter.fold(entry.apkUrls).firstOrNull()
        if (ref == null) {
            _state.update {
                it.copy(
                    notice = Notice(
                        text = "「$label」没有可用的 APK 地址",
                        kind = Notice.Kind.ERROR,
                    )
                )
            }
            return
        }
        startDownloadInstall(PendingInstall(ref, label, entry.latestVersion))
    }

    private fun startDownloadInstall(job: PendingInstall) {
        if (_state.value.busy != null) return

        // C15：Android 8+ 的「安装未知应用」是每个来源应用单独授权的特殊权限。
        if (!downloader.canRequestPackageInstalls()) {
            pendingInstall = job
            getApplication<Application>().startActivity(downloader.unknownSourcesSettingsIntent())
            _state.update {
                it.copy(
                    notice = Notice(
                        text = "请先允许本应用安装未知应用",
                        kind = Notice.Kind.WARN,
                        detail = "在系统设置里打开开关后返回，会自动继续下载「${job.label}」。",
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(busy = Busy.Downloading(job.label, 0, -1), notice = null) }
            try {
                val apk = withContext(Dispatchers.IO) {
                    // 上一次安装的残留在这里清掉：此刻它一定已经用完（§3.12 步骤 6）。
                    store.sweepAll()
                    var lastBucket = -1L
                    downloader.download(job.ref.url, store.fileFor(job.ref.name)) { read, total ->
                        val bucket = if (total > 0) read * 100 / total else read / (256 * 1024)
                        if (bucket != lastBucket) {
                            lastBucket = bucket
                            _state.update {
                                it.copy(busy = Busy.Downloading(job.label, read, total))
                            }
                        }
                    }
                }

                _state.update { it.copy(busy = Busy.Installing) }
                awaitingInstallerReturn = true
                withContext(Dispatchers.Main) {
                    getApplication<Application>().startActivity(downloader.installIntent(apk))
                }
                _state.update {
                    it.copy(
                        busy = null,
                        notice = Notice(
                            text = "已交给系统安装器：${job.label}",
                            kind = Notice.Kind.INFO,
                            detail = "在安装器里确认。返回本应用后会自动重新扫描。",
                        ),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = null, notice = noticeFor(e)) }
            }
        }
    }

    // ---------------------------------------------------------------- 生命周期

    /**
     * 从安装器返回时：清掉临时 APK、必要时续上被权限拦下的下载、重扫目标。
     *
     * **「装成功了吗」以重新扫描 `PackageManager` 为准**，不信安装器的返回值 ——
     * 系统安装器根本不返回结果，我们只知道「用户离开了安装器」（§3.12 注意事项）。
     */
    fun onResume() {
        val returning = awaitingInstallerReturn
        awaitingInstallerReturn = false
        if (!returning) return

        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.sweepAll() }
            pendingInstall?.let { job ->
                if (downloader.canRequestPackageInstalls()) {
                    pendingInstall = null
                    startDownloadInstall(job)
                }
            }
            rescanTargets()
        }
    }

    // ---------------------------------------------------------------- 其它

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    /**
     * 设置页可能改过清单地址，而它跑在另一个 Activity、另一个 ViewModel 实例上。
     * 回到主界面时把显示对齐（**只对齐显示，不重新拉取** —— 拉取是「同步」按钮的职责）。
     */
    fun refreshManifestUrlDisplay() {
        _state.update { it.copy(manifestUrl = prefs.manifestUrl) }
    }

    fun setManifestUrl(url: String) {
        prefs.manifestUrl = url
        _state.update { it.copy(manifestUrl = prefs.manifestUrl) }
    }

    fun resetManifestUrl() {
        prefs.manifestUrl = BuildConfig.DEFAULT_MANIFEST_URL
        _state.update { it.copy(manifestUrl = prefs.manifestUrl) }
    }

    fun defaultManifestUrl(): String = BuildConfig.DEFAULT_MANIFEST_URL

    fun isDebugBuild(): Boolean = BuildConfig.DEBUG

    /** 设置页的 debug 开关。release 构建下恒为 false —— 样例清单只存在于 debug 的 assets 里。 */
    fun isUsingBundledFixture(): Boolean = BuildConfig.DEBUG && prefs.useBundledFixture

    fun setUseBundledFixture(enabled: Boolean) {
        prefs.useBundledFixture = enabled
        refresh()
    }

    private fun appId(): String = getApplication<Application>().packageName

    private fun noticeFor(e: Throwable): Notice {
        if (e is MarketException) {
            val kind = when (e.kind) {
                MarketException.Kind.NO_TARGET, MarketException.Kind.NO_ENTRY -> Notice.Kind.WARN
                else -> Notice.Kind.ERROR
            }
            return Notice(e.message, kind)
        }
        return Notice(e.message ?: e.javaClass.simpleName, Notice.Kind.ERROR)
    }
}

/** 电视判定：优先看 UiModeManager，再退到 leanback 特性（有些盒子只报其中一个）。 */
fun Context.isTelevision(): Boolean {
    val uiMode = getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager
    if (uiMode?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION) return true
    return packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
}
