package com.obtainium.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.obtainium.companion.AppEntry
import com.obtainium.companion.BuildInfo
import com.obtainium.companion.ObtainiumLauncher
import com.obtainium.companion.SelfUpdate
import com.obtainium.companion.UiState
import com.obtainium.companion.statusLine

/** 一次算好、不变的构建事实。放进参数是为了避免每次重组都去查 PackageManager。 */
data class BuildFacts(
    val versionName: String,
    val versionCode: Long,
    val fingerprintShort: String,
    val fingerprintFull: String,
    val device: String,
)

/**
 * 设置页。**只有一套（material3）**，手机与电视共用。
 *
 * 电视上它少了 10 尺的焦点放大效果，但功能完整（D-pad 可聚焦输入框，遥控器/外接键盘可输入）。
 * 这里刻意不做双份：设置页是低频入口，双份排版带来的漂移风险大于那点观感收益。
 */
@Composable
fun SettingsScreen(
    state: UiState,
    buildInfo: BuildFacts,
    defaultUrl: String,
    isDebug: Boolean,
    usingFixture: Boolean,
    onSaveUrl: (String) -> Unit,
    onResetUrl: () -> Unit,
    onRescan: () -> Unit,
    onChooseTarget: (ObtainiumLauncher.Target) -> Unit,
    onCheckUpdate: () -> Unit,
    onSelfUpdate: () -> Unit,
    onInstallCandidate: (AppEntry) -> Unit,
    onToggleFixture: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("设置", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onBack) { Text("返回") }
            }

            ManifestUrlSection(state, defaultUrl, onSaveUrl, onResetUrl)
            TargetSection(state, onRescan, onChooseTarget)
            OtherVersionsSection(state, onInstallCandidate)
            SelfUpdateSection(state, onCheckUpdate, onSelfUpdate)
            BuildSection(buildInfo, state)
            if (isDebug) DebugSection(usingFixture, onToggleFixture)
        }
    }
}

@Composable
private fun ManifestUrlSection(
    state: UiState,
    defaultUrl: String,
    onSaveUrl: (String) -> Unit,
    onResetUrl: () -> Unit,
) {
    // 以 state 里的值为键：别处改了地址后，这里会跟着刷新，而不是留着一份过期草稿。
    var draft by remember(state.manifestUrl) { mutableStateOf(state.manifestUrl) }

    Section("清单地址") {
        Text(
            "第一期用 GitHub raw 直链；部署期换成 CF 域名后，只改这一项，客户端无需升级。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text("清单 URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "内置默认：$defaultUrl",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row {
            Button(onClick = { onSaveUrl(draft) }, enabled = draft.isNotBlank()) { Text("保存") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { onResetUrl(); draft = defaultUrl }) { Text("恢复默认") }
        }
        Text(
            "改动在下次点「同步」时生效。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TargetSection(
    state: UiState,
    onRescan: () -> Unit,
    onChooseTarget: (ObtainiumLauncher.Target) -> Unit,
) {
    Section("Obtainium 目标") {
        val current = state.target
        val candidates = state.targetCandidates

        Text(
            when {
                current != null -> "当前：${current.label}（${current.packageName}）"
                candidates.isEmpty() -> "本机没有检测到能接收 obtainium:// 的应用。"
                else -> "发现 ${candidates.size} 个候选，请选一个。"
            },
            style = MaterialTheme.typography.bodyMedium,
        )

        if (candidates.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            candidates.forEach { candidate ->
                val selected = candidate.packageName == current?.packageName
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = selected) { onChooseTarget(candidate) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = { onChooseTarget(candidate) })
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(candidate.label, fontWeight = FontWeight.Medium)
                        Text(
                            candidate.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRescan) { Text("重新扫描") }
    }
}

/**
 * Q12：这个入口**按清单内容显隐** —— 只有一个 `kind:"obtainium"` 条目时根本不显示，
 * 因为「安装其他版本」在那时没有任何可供选择的第二项。
 */
@Composable
private fun OtherVersionsSection(state: UiState, onInstallCandidate: (AppEntry) -> Unit) {
    val candidates = state.manifest?.obtainiumKinds.orEmpty()
    if (candidates.size <= 1) return

    var expanded by remember { mutableStateOf(false) }

    Section("安装其他 Obtainium 版本") {
        Text(
            "清单里有 ${candidates.size} 个 kind=\"obtainium\" 条目。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "收起" else "选择要安装的版本")
        }
        if (expanded) {
            candidates.forEach { entry ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = false) { onInstallCandidate(entry) }
                        .padding(vertical = 10.dp),
                ) {
                    Text("${entry.name} · v${entry.latestVersion}", fontWeight = FontWeight.Medium)
                    Text(
                        entry.id,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelfUpdateSection(state: UiState, onCheckUpdate: () -> Unit, onSelfUpdate: () -> Unit) {
    Section("自更新") {
        val line = SelfUpdate.describe(state.selfUpdate, BuildInfo.versionName)
        Text(line ?: "清单里没有 kind=\"companion\" 条目，自更新未配置。")
        if (state.selfUpdate is SelfUpdate.State.Available) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onSelfUpdate) { Text("下载并安装") }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCheckUpdate) { Text("重新拉清单并比对") }
        }
        Text(
            state.statusLine(System.currentTimeMillis()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BuildSection(buildInfo: BuildFacts, state: UiState) {
    Section("构建信息") {
        InfoLine("版本", "${buildInfo.versionName}（versionCode ${buildInfo.versionCode}）")
        InfoLine("签名指纹", buildInfo.fingerprintShort)
        InfoLine("完整指纹", buildInfo.fingerprintFull)
        InfoLine("设备", buildInfo.device)
        InfoLine("清单地址", state.manifestUrl)

        Spacer(Modifier.height(8.dp))
        Text(
            "自更新依赖「签名恒定」：一旦下次发布换了签名，覆盖安装会被系统拒绝，" +
                "而设备上这个版本就再也升不上去。换 keystore 前请先确认上面这串指纹，" +
                "并把这个 keystore 离线备份。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun DebugSection(usingFixture: Boolean, onToggleFixture: (Boolean) -> Unit) {
    Section("调试（仅 debug 构建）") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("使用内置样例清单")
                Text(
                    "改用随包的 fixture-manifest.json，不联网。用于离线验证导入链路。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = usingFixture, onCheckedChange = onToggleFixture)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Divider()
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
