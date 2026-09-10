package com.obtainium.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.obtainium.companion.ui.BuildFacts
import com.obtainium.companion.ui.PhoneTheme
import com.obtainium.companion.ui.SettingsScreen

/**
 * 设置页入口。
 *
 * 用的是**自己作用域内的** [MarketViewModel] 实例（与 MainActivity 各一份）——
 * 两者只共享 `Prefs` 落盘状态与 [MarketCache]，不共享内存状态。
 * 这是刻意的：设置页可能在主界面还活着时被打开，两处各自持有状态比强行共享一个单例简单得多，
 * 而唯一需要「看见彼此改动」的东西（清单地址）已经落在 `Prefs` 里了。
 */
class SettingsActivity : ComponentActivity() {

    private val viewModel: MarketViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 指纹只查一次：PackageManager 调用不该跟着重组跑。
        val buildInfo = BuildFacts(
            versionName = BuildInfo.versionName,
            versionCode = BuildInfo.versionCode,
            fingerprintShort = BuildInfo.shortSigningFingerprint(this),
            fingerprintFull = BuildInfo.signingSha256(this),
            device = BuildInfo.deviceSummary(),
        )

        setContent {
            PhoneTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                SettingsScreen(
                    state = state,
                    buildInfo = buildInfo,
                    defaultUrl = viewModel.defaultManifestUrl(),
                    isDebug = viewModel.isDebugBuild(),
                    usingFixture = viewModel.isUsingBundledFixture(),
                    onSaveUrl = viewModel::setManifestUrl,
                    onResetUrl = viewModel::resetManifestUrl,
                    onRescan = viewModel::rescanTargets,
                    onChooseTarget = viewModel::chooseTarget,
                    onCheckUpdate = viewModel::refresh,
                    onSelfUpdate = viewModel::startSelfUpdate,
                    onInstallCandidate = viewModel::chooseInstallCandidate,
                    onToggleFixture = viewModel::setUseBundledFixture,
                    onBack = { finish() },
                )
            }
        }
    }
}
