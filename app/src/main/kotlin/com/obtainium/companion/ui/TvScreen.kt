package com.obtainium.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.obtainium.companion.Notice

/**
 * 电视界面：10 尺 UI（大字号、深色、D-pad 焦点态、5% 过扫描留白）。
 *
 * **刻意只用 `androidx.tv.material3` 的三个成员：`MaterialTheme` / `Text` / `Button`。**
 * 其余一律用 Compose foundation 原语（`Box`/`Row`/`background`/`border`）拼。
 * 理由：tv-material3 是独立于 material3 演进的一套库，版本对齐的构件越多、
 * 撞上 API 改动的概率越大；而 10 尺 UI 真正需要的「大字号 + 焦点可见 + 深色」,
 * 用 foundation 一样能表达，且不受任何版本漂移影响。
 *
 * 对话框也自己做（覆盖层而非 `AlertDialog`）—— tv-material3 没有对话框组件。
 */
@Composable
fun TvScreen(content: ScreenContent, actions: ScreenActions) {
    val choiceShown =
        content.targetCandidates.size > 1 || content.installCandidates.isNotEmpty()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                // 覆盖层在时，下层列表必须彻底退出焦点遍历：否则 D-pad 还能把焦点移到被遮住的行上，
                // 用户在一片「变暗的、点不动的」区域里游走，却不知道自己在哪。
                .then(if (choiceShown) Modifier.focusProperties { canFocus = false } else Modifier),
            // 5% 过扫描留白：部分电视会裁掉边缘，48/32 是安全值。
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column {
                    Text(content.title, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text(
                        content.statusLine,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        content.manifestUrlLine,
                        fontSize = 14.sp,
                        color = Color(0xFF8A90A2),
                    )
                }
            }

            content.busyText?.let { text ->
                item { TvBusy(text, content.busyFraction) }
            }

            item {
                Button(onClick = actions.onPrimary, enabled = content.primaryEnabled) {
                    Text(content.primaryLabel, fontSize = 22.sp)
                }
            }

            content.targetLine?.let { line ->
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(line, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(16.dp))
                        Button(onClick = actions.onRescan) { Text("重新扫描", fontSize = 18.sp) }
                    }
                }
            }

            content.selfUpdate?.let { line ->
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Text(line.text, fontSize = 20.sp, modifier = Modifier.weight(1f))
                        if (line.actionLabel.isNotEmpty()) {
                            Spacer(Modifier.width(16.dp))
                            Button(onClick = actions.onSelfUpdate) {
                                Text(line.actionLabel, fontSize = 18.sp)
                            }
                        }
                    }
                }
            }

            content.hint?.let { hint ->
                item { Text(hint, fontSize = 16.sp, color = Color(0xFF8A90A2)) }
            }

            content.notice?.let { notice -> item { TvNotice(notice, actions.onDismissNotice) } }

            if (content.warnings.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(20.dp),
                    ) {
                        Text("清单里有 ${content.warnings.size} 处被跳过", fontSize = 18.sp)
                        content.warnings.forEach {
                            Text("· $it", fontSize = 14.sp, color = Color(0xFF8A90A2))
                        }
                    }
                }
            }

            item {
                Text(
                    content.appCountLine,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            items(content.appRows, key = { it.id }) { row -> TvAppRow(row) }

            item {
                Button(onClick = actions.onOpenSettings) { Text("设置", fontSize = 18.sp) }
            }
        }

        // 覆盖层画在最上层
        if (content.targetCandidates.size > 1) {
            TvChoiceOverlay(
                title = "选择要同步到哪个 Obtainium",
                subtitle = "本机有多个应用能接收导入链接。选定后会记住，之后不再询问。",
                options = content.targetCandidates.map { it.label to it.packageName },
                onPick = { index -> actions.onChooseTarget(content.targetCandidates[index]) },
                onDismiss = null,
            )
        } else if (content.installCandidates.isNotEmpty()) {
            TvChoiceOverlay(
                title = "安装哪一个 Obtainium",
                subtitle = "清单里有多个 kind=\"obtainium\" 条目。只有官方的能保证响应 obtainium:// 导入链接。",
                options = content.installCandidates.map { "${it.name} · v${it.latestVersion}" to it.id },
                onPick = { index -> actions.onChooseInstall(content.installCandidates[index]) },
                onDismiss = actions.onDismissInstallChoice,
            )
        }
    }
}

@Composable
private fun TvBusy(text: String, fraction: Float?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF2A2E3A)),
        ) {
            if (fraction != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Text(text, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun TvNotice(notice: Notice, onDismiss: () -> Unit) {
    val accent = when (notice.kind) {
        Notice.Kind.INFO -> MaterialTheme.colorScheme.primary
        Notice.Kind.WARN -> Color(0xFFFFC46B)
        Notice.Kind.ERROR -> Color(0xFFFFB4AB)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
    ) {
        Text(notice.text, fontSize = 20.sp, color = accent, fontWeight = FontWeight.Medium)
        notice.detail?.let {
            Text(it, fontSize = 15.sp, color = Color(0xFFB9BECD), modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onDismiss) { Text("知道了", fontSize = 16.sp) }
    }
}

/** 行本身可获焦：这样 D-pad 上下键能自然滚动列表，而不是只有一个可点的按钮。 */
@Composable
private fun TvAppRow(row: AppRow) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) Color(0xFF262B38) else MaterialTheme.colorScheme.surface)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.name, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text(row.detail, fontSize = 14.sp, color = Color(0xFF8A90A2))
        }
        row.badge?.let { badge ->
            Text(badge, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * 全屏选择覆盖层。`onDismiss == null` 表示必须选一个（目标发现不能替用户猜，§3.3）。
 */
@Composable
private fun TvChoiceOverlay(
    title: String,
    subtitle: String,
    options: List<Pair<String, String>>,
    onPick: (Int) -> Unit,
    onDismiss: (() -> Unit)?,
) {
    // 覆盖层弹出时把焦点抢过来，否则焦点还留在（已被禁用的）下层，遥控器按下去没有任何反应。
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE60A0B0F))
            .padding(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 16.sp, color = Color(0xFFB9BECD), modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(24.dp))
            options.forEachIndexed { index, (label, sub) ->
                Column(modifier = Modifier.padding(bottom = 14.dp)) {
                    Button(
                        onClick = { onPick(index) },
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    ) {
                        Text(label, fontSize = 20.sp)
                    }
                    Text(sub, fontSize = 13.sp, color = Color(0xFF8A90A2), modifier = Modifier.padding(start = 4.dp))
                }
            }
            if (onDismiss != null) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onDismiss) { Text("取消", fontSize = 18.sp) }
            }
        }
    }
}
