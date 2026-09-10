package com.obtainium.companion

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.obtainium.companion.ui.PhoneTheme
import com.obtainium.companion.ui.PhoneScreen
import com.obtainium.companion.ui.TvScreen
import com.obtainium.companion.ui.TvTheme
import com.obtainium.companion.ui.buildScreenContent
import com.obtainium.companion.ui.screenActions

/**
 * 单屏主界面。
 *
 * **单 APK、运行时自适应**：同一个应用、同一份业务逻辑与状态，
 * 手机走 `androidx.compose.material3`，电视走 `androidx.tv.material3`。
 * 判定只做一次（[isTelevision]）并缓存，不在重组里反复查系统服务。
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MarketViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val isTv = isTelevision()
        val appTitle = getString(R.string.app_name)

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            // 相对时间（「3 分钟前」）只在状态变化时重算 —— 不为此起一个每秒的时钟。
            val content = remember(state) {
                buildScreenContent(state, System.currentTimeMillis(), appTitle)
            }
            val actions = remember {
                screenActions(viewModel) { startActivity(Intent(this, SettingsActivity::class.java)) }
            }

            if (isTv) {
                TvTheme { TvScreen(content, actions) }
            } else {
                PhoneTheme { PhoneScreen(content, actions) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从安装器返回 → 清理 + 重扫；设置页改过清单地址 → 对齐显示。两件事都在这一个入口里。
        viewModel.onResume()
        viewModel.refreshManifestUrlDisplay()
    }
}
