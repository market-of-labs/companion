package com.obtainium.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.obtainium.companion.AppEntry
import com.obtainium.companion.Notice
import com.obtainium.companion.ObtainiumLauncher

/**
 * 手机界面：`androidx.compose.material3`，浅色优先，单列纵向滚动。
 */
@Composable
fun PhoneScreen(content: ScreenContent, actions: ScreenActions) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column {
                    Text(content.title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        content.statusLine,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        content.manifestUrlLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            content.busyText?.let { text -> item { BusyBlock(text, content.busyFraction) } }

            item {
                Button(
                    onClick = actions.onPrimary,
                    enabled = content.primaryEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(content.primaryLabel, style = MaterialTheme.typography.titleMedium)
                }
            }

            content.targetLine?.let { line ->
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            line,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = actions.onRescan) { Text("重新扫描") }
                    }
                }
            }

            content.selfUpdate?.let { line ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(line.text, modifier = Modifier.weight(1f))
                            if (line.actionLabel.isNotEmpty()) {
                                TextButton(onClick = actions.onSelfUpdate) { Text(line.actionLabel) }
                            }
                        }
                    }
                }
            }

            content.hint?.let { hint ->
                item {
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            content.notice?.let { notice -> item { NoticeCard(notice, actions.onDismissNotice) } }

            if (content.warnings.isNotEmpty()) {
                item { WarningCard(content.warnings) }
            }

            item {
                Text(
                    content.appCountLine,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            items(content.appRows, key = { it.id }) { row -> AppRowCard(row) }

            item {
                TextButton(onClick = actions.onOpenSettings) { Text("设置") }
            }
        }
    }

    if (content.targetCandidates.size > 1) {
        TargetDialog(content.targetCandidates, actions.onChooseTarget)
    }
    if (content.installCandidates.isNotEmpty()) {
        InstallDialog(content.installCandidates, actions.onChooseInstall, actions.onDismissInstallChoice)
    }
}

@Composable
private fun BusyBlock(text: String, fraction: Float?) {
    Column {
        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun NoticeCard(notice: Notice, onDismiss: () -> Unit) {
    val container = when (notice.kind) {
        Notice.Kind.INFO -> MaterialTheme.colorScheme.surfaceVariant
        Notice.Kind.WARN -> MaterialTheme.colorScheme.secondaryContainer
        Notice.Kind.ERROR -> MaterialTheme.colorScheme.errorContainer
    }
    Card(colors = CardDefaults.cardColors(containerColor = container)) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(notice.text, fontWeight = FontWeight.Medium)
            notice.detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    }
}

@Composable
private fun WarningCard(warnings: List<String>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("清单里有 ${warnings.size} 处被跳过", fontWeight = FontWeight.Medium)
            warnings.forEach { w ->
                Text(
                    "· $w",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun AppRowCard(row: AppRow) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                row.badge?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                row.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun TargetDialog(
    candidates: List<ObtainiumLauncher.Target>,
    onChoose: (ObtainiumLauncher.Target) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { /* 必须选一个，不允许点外部关掉（§3.3） */ },
        title = { Text("选择要同步到哪个 Obtainium") },
        text = {
            Column {
                Text(
                    "本机有多个应用能接收导入链接。选定后会记住，之后不再询问。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                candidates.forEach { c ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = false) { onChoose(c) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = false, onClick = { onChoose(c) })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(c.label, fontWeight = FontWeight.Medium)
                            Text(
                                c.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun InstallDialog(
    candidates: List<AppEntry>,
    onChoose: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("安装哪一个 Obtainium") },
        text = {
            Column {
                Text(
                    "清单里有多个 kind=\"obtainium\" 条目。只有官方的能保证响应 obtainium:// 导入链接。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                candidates.forEach { c ->
                    Text(
                        "${c.name} · v${c.latestVersion}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = false) { onChoose(c) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
