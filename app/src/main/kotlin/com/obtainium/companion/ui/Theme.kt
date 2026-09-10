package com.obtainium.companion.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
 * 电视主题。只给 `colorScheme`，其余走默认 ——
 * 这里依赖 `MaterialTheme` 三个参数都有默认值（Material3 与 tv-material3 都是这样）。
 */
@Composable
fun TvTheme(content: @Composable () -> Unit) {
    androidx.tv.material3.MaterialTheme(colorScheme = TvDark, content = content)
}
