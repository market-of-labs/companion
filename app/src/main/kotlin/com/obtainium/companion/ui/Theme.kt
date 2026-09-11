package com.obtainium.companion.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.darkColorScheme

/**
 * 两个设计系统共用同一组品牌色，避免「手机上是这个蓝、电视上是那个蓝」。
 *
 * 手机走 `androidx.compose.material3`（浅色优先），电视走 `androidx.tv.material3`
 * （**深色优先** —— 电视在客厅暗环境里看，浅色大面积白底会刺眼，这也是 Leanback 的默认取向）。
 */
object Brand {
    val Blue = Color(0xFF3B6FF5)
    val BlueDark = Color(0xFF9BB8FF)
    val Green = Color(0xFF1E8E5A)
    val GreenDark = Color(0xFF6FDC9E)
    val Amber = Color(0xFF9A6400)
    val AmberDark = Color(0xFFFFC46B)
    val Red = Color(0xFFB3261E)
    val RedDark = Color(0xFFFFB4AB)

    val SurfaceLight = Color(0xFFFBF9FD)
    val SurfaceDark = Color(0xFF12141A)
    val CardDark = Color(0xFF1D2029)
}

private val PhoneLight = lightColorScheme(
    primary = Brand.Blue,
    background = Brand.SurfaceLight,
    surface = Brand.SurfaceLight,
)

private val TvDark = darkColorScheme(
    primary = Brand.BlueDark,
    background = Brand.SurfaceDark,
    surface = Brand.CardDark,
    onBackground = Color(0xFFE6E8EF),
    onSurface = Color(0xFFE6E8EF),
    onPrimary = Color(0xFF10214F),
)

@Composable
fun PhoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PhoneLight, content = content)
}

/**
 * 电视主题。
 *
 * 除了 `colorScheme`，这里**必须**自己提供 `LocalContentColor`，否则整个电视界面是黑底黑字。
 *
 * 原因是 tv-material3 与 material3 的一处关键差异：`androidx.tv.material3.MaterialTheme`
 * 只提供 `LocalColorScheme` / `LocalShapes` / `LocalTextSelectionColors` / `LocalTypography`
 * 四项，**不含** `LocalContentColor`；而后者的默认值在 `androidx.tv.material3.ContentColor.kt`
 * 里写死为 `Color.Black`。而 `androidx.tv.material3.Text` 取色的顺序是
 * `color -> style.color -> LocalContentColor.current`，所以**没显式传 color 的 `Text` 一律是黑的**。
 * （`Button` 不受影响：它内部走 `ClickableSurface`，那条路会提供 contentColor。）
 *
 * 手机那套之所以从没暴露这个问题，是因为 `PhoneScreen` 的根节点是 material3 的 `Surface` ——
 * `Surface` 会按自己的容器色算出 contentColor 并向下提供。电视这套的根节点是
 * `Box(Modifier.background(...))`，一个纯 foundation 原语，不提供任何 composition local，
 * 于是所有裸 `Text` 都直接落到那个默认的黑色上。
 *
 * 这个值给 `onBackground` 而不是按容器分别给：`TvDark` 里 `onBackground` 与 `onSurface`
 * 同为 `#E6E8EF`，在 `surface` 卡片底上对比度同样足够。
 */
@Composable
fun TvTheme(content: @Composable () -> Unit) {
    androidx.tv.material3.MaterialTheme(colorScheme = TvDark) {
        CompositionLocalProvider(
            androidx.tv.material3.LocalContentColor provides TvDark.onBackground,
            content = content,
        )
    }
}
