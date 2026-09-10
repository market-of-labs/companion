package com.obtainium.companion.ui

import com.obtainium.companion.AppEntry
import com.obtainium.companion.MarketViewModel
import com.obtainium.companion.ObtainiumLauncher

/**
 * 界面上所有可触发的动作。手机与电视两套渲染接的是**同一组**回调，
 * 保证「同一个按钮在两处做同一件事」。
 */
data class ScreenActions(
    /** 主按钮：有目标则同步，无目标则安装 Obtainium（由 ViewModel 分派）。 */
    val onPrimary: () -> Unit,
    val onRescan: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onDismissNotice: () -> Unit,
    val onSelfUpdate: () -> Unit,
    val onChooseTarget: (ObtainiumLauncher.Target) -> Unit,
    val onChooseInstall: (AppEntry) -> Unit,
    val onDismissInstallChoice: () -> Unit,
)

/**
 * 把 ViewModel 的三个职责接到界面动作上。手机与电视共用这一份，
 * `onOpenSettings` 由宿主 Activity 提供 —— ui 包不引用任何 `Context` 相关的导航细节。
 */
fun screenActions(viewModel: MarketViewModel, onOpenSettings: () -> Unit): ScreenActions =
    ScreenActions(
        onPrimary = viewModel::primaryAction,
        onRescan = viewModel::rescanTargets,
        onOpenSettings = onOpenSettings,
        onDismissNotice = viewModel::dismissNotice,
        onSelfUpdate = viewModel::startSelfUpdate,
        onChooseTarget = viewModel::chooseTarget,
        onChooseInstall = viewModel::chooseInstallCandidate,
        onDismissInstallChoice = viewModel::dismissInstallCandidates,
    )
