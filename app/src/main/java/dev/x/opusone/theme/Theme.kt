package dev.x.opusone.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.dp

/**
 * 主题风格（与明暗无关的「配色气质」维度）。
 */
enum class ThemeStyle(val labelZh: String) {
    DYNAMIC("动态"),     // Material You 动态取色（Android 12+）
    PARCHMENT("宣纸"),  // 澄心宣白·朱砂微红（深色变体为玄青夜读）
    INDIGO("线装"),     // 磁青素月·霁蓝黛墨（深色变体为星夜黛蓝）
    BAMBOO("竹简")      // 天青竹月·苍翠竹青（深色变体为墨绿竹金）
}

/**
 * 明暗模式（独立于风格的第二个维度，支持跟随系统）。
 */
enum class DarkMode(val labelZh: String) {
    LIGHT("浅色"),
    DARK("深色"),
    FOLLOW_SYSTEM("跟随系统")
}

/** 把明暗模式解析为实际是否深色。 */
fun DarkMode.resolveDark(systemDark: Boolean): Boolean = when (this) {
    DarkMode.LIGHT -> false
    DarkMode.DARK -> true
    DarkMode.FOLLOW_SYSTEM -> systemDark
}

/** 供 Canvas 绘制等无法自动响应 colorScheme 的场景读取当前是否深色。 */
val LocalIsDarkTheme = compositionLocalOf { false }

/**
 * 阅读器等深层页面的快捷主题操作入口。
 * 完整的主题设置在全局设置页；这里只暴露阅读场景需要的「冷暖底色」快捷切换。
 */
data class ThemeActions(
    val currentStyle: ThemeStyle,
    val isDark: Boolean,
    val setStyle: (ThemeStyle) -> Unit,
    val setDarkMode: (DarkMode) -> Unit
)

val LocalThemeActions = compositionLocalOf {
    ThemeActions(ThemeStyle.PARCHMENT, false, {}, {})
}

/**
 * Material Design 3 形状尺度（Shape Scale）。
 * 统一提供圆角令牌，避免业务代码硬编码。
 */
val OpusOneShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

/**
 * 黛蓝线装 · 磁青素月配色方案。
 * 浅色方案以温润月白为底，主色采用磁青与霁蓝，搭配暖赭题签与松烟黛墨文字。
 */
val IndigoLightColorScheme = lightColorScheme(
    primary = Color(0xFF1F4675),            // 霁蓝/磁青
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E3F0),   // 霁蓝淡染水色
    onPrimaryContainer = Color(0xFF09203F),
    secondary = Color(0xFF4C6073),          // 瓷青/云水
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB8D0E5), // 澄澈瓷青水色指示器与徽标背景（与底栏拉开明润对比度）
    onSecondaryContainer = Color(0xFF0D2138),
    tertiary = Color(0xFF8C5B36),           // 古籍金丝线/题签暖赭
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFCE9DB),
    onTertiaryContainer = Color(0xFF381F0D),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFECEFF3),         // 雾白云素宣底色（吸光防眩，温和静雅）
    onBackground = Color(0xFF1E232B),       // 松烟黛墨，对比清晰温和
    surface = Color(0xFFECEFF3),
    onSurface = Color(0xFF1E232B),
    surfaceVariant = Color(0xFFDEE3EA),
    onSurfaceVariant = Color(0xFF4C5562),
    surfaceTint = Color(0xFF1F4675),
    inverseSurface = Color(0xFF2A313A),
    inverseOnSurface = Color(0xFFF0F4F8),
    inversePrimary = Color(0xFFA3C5F7),
    scrim = Color(0xFF000000),
    outline = Color(0xFF7A8696),
    outlineVariant = Color(0xFFCAD2DC),     // 纤细柔和的书页裁切线
    surfaceDim = Color(0xFFDCE1E8),
    surfaceBright = Color(0xFFF4F6F9),
    // 浅色容器各层级色阶
    surfaceContainerLowest = Color(0xFFF5F8FC),  // 柔和微亮卡片，不刺目
    surfaceContainerLow = Color(0xFFE6E8EC),     // 纯净底层容器
    surfaceContainer = Color(0xFFDFE3E7),        // 卡片内容区
    surfaceContainerHigh = Color(0xFFD8DBDF),    // 辅助阅读区块
    surfaceContainerHighest = Color(0xFFD0D2D6)  // 徽章微差层级
)

/**
 * 黛蓝线装 · 星夜黛蓝配色方案（深色变体）。
 * 星夜墨青为底，霁蓝夜光为主色，月白为字。
 */
val IndigoDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA3C5F7),
    onPrimary = Color(0xFF0B2B50),
    primaryContainer = Color(0xFF1D3B63),
    onPrimaryContainer = Color(0xFFDCE5F2),
    secondary = Color(0xFFB2C5DA),
    onSecondary = Color(0xFF1B2F44),
    secondaryContainer = Color(0xFF30465E),
    onSecondaryContainer = Color(0xFFD9E2EC),
    tertiary = Color(0xFFF3BD99),
    onTertiary = Color(0xFF462409),
    tertiaryContainer = Color(0xFF623A1D),
    onTertiaryContainer = Color(0xFFF8E5D8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E131A),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF0E131A),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF3B4654),
    onSurfaceVariant = Color(0xFFC0CCD9),
    surfaceTint = Color(0xFFA3C5F7),
    inverseSurface = Color(0xFFE2E8F0),
    inverseOnSurface = Color(0xFF19202B),
    inversePrimary = Color(0xFF244673),
    scrim = Color(0xFF000000),
    outline = Color(0xFF8995A5),
    outlineVariant = Color(0xFF3E4A5A),
    surfaceDim = Color(0xFF0E131A),
    surfaceBright = Color(0xFF354153),
    surfaceContainerLowest = Color(0xFF0A0D12),
    surfaceContainerLow = Color(0xFF141A23),
    surfaceContainer = Color(0xFF19202B),
    surfaceContainerHigh = Color(0xFF212A38),
    surfaceContainerHighest = Color(0xFF2B3647)
)

/**
 * 宣纸·朱砂·玄青配色方案。
 * 浅色方案以温润熟宣纸色为基底，沉香古金为辅，朱砂提神。
 */
val ParchmentColorScheme = lightColorScheme(
    primary = Color(0xFF8E3424),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADBBE),
    onPrimaryContainer = Color(0xFF483010),
    secondary = Color(0xFF565044),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8C2A4),
    onSecondaryContainer = Color(0xFF332210),
    tertiary = Color(0xFF7A5826),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE8DCC0),
    onTertiaryContainer = Color(0xFF382305),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF2ECE1),
    onBackground = Color(0xFF26231E),
    surface = Color(0xFFF2ECE1),
    onSurface = Color(0xFF26231E),
    surfaceVariant = Color(0xFFE6DFD0),
    onSurfaceVariant = Color(0xFF5A544A),
    surfaceTint = Color(0xFF8E3424),
    inverseSurface = Color(0xFF34302A),
    inverseOnSurface = Color(0xFFF5EFE6),
    inversePrimary = Color(0xFFE8988A),
    scrim = Color(0xFF000000),
    outline = Color(0xFF8A8275),
    outlineVariant = Color(0xFFD4CBB9),
    surfaceDim = Color(0xFFE2D9C8),
    surfaceBright = Color(0xFFF7F2E8),
    surfaceContainerLowest = Color(0xFFFBF6EB),
    surfaceContainerLow = Color(0xFFE8E3D8),
    surfaceContainer = Color(0xFFE2DCD1),
    surfaceContainerHigh = Color(0xFFDBD5CA),
    surfaceContainerHighest = Color(0xFFD2CBC1)
)

/**
 * 玄青夜读配色方案（宣纸深色变体）。
 * 墨青底色配合朱砂提亮与古金，保证弱光环境下的舒适阅读。
 */
val InkDarkColorScheme = darkColorScheme(
    primary = Color(0xFFF2B39E),
    onPrimary = Color(0xFF4C150A),
    primaryContainer = Color(0xFF6E2F1F),
    onPrimaryContainer = Color(0xFFFFDAD0),
    secondary = Color(0xFFD8C3A5),
    onSecondary = Color(0xFF362817),
    secondaryContainer = Color(0xFF483A2A),
    onSecondaryContainer = Color(0xFFF5E4D0),
    tertiary = Color(0xFFE2C37E),
    onTertiary = Color(0xFF3C2D06),
    tertiaryContainer = Color(0xFF54431A),
    onTertiaryContainer = Color(0xFFEFE0B8),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF14120F),
    onBackground = Color(0xFFE4E0D5),
    surface = Color(0xFF14120F),
    onSurface = Color(0xFFE4E0D5),
    surfaceVariant = Color(0xFF3D3833),
    onSurfaceVariant = Color(0xFFC4BCA9),
    surfaceTint = Color(0xFFF2B39E),
    inverseSurface = Color(0xFFE4E0D5),
    inverseOnSurface = Color(0xFF322F2A),
    inversePrimary = Color(0xFF9E3D2D),
    scrim = Color(0xFF000000),
    outline = Color(0xFF8E8577),
    outlineVariant = Color(0xFF413C36),
    surfaceDim = Color(0xFF14120F),
    surfaceBright = Color(0xFF3B3935),
    surfaceContainerLowest = Color(0xFF100F0E),
    surfaceContainerLow = Color(0xFF1D1B19),
    surfaceContainer = Color(0xFF211F1D),
    surfaceContainerHigh = Color(0xFF2B2926),
    surfaceContainerHighest = Color(0xFF36332F)
)

/**
 * 竹简雅韵 · 天青竹月配色方案。
 * 以清润豆沙竹青为底、苍翠竹青为主色的护眼方案。
 */
val BambooColorScheme = lightColorScheme(
    primary = Color(0xFF26563F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCCE2D1),
    onPrimaryContainer = Color(0xFF0F3222),
    secondary = Color(0xFF645A46),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB5CEB3),
    onSecondaryContainer = Color(0xFF152E1B),
    tertiary = Color(0xFF386866),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD0E6E4),
    onTertiaryContainer = Color(0xFF143332),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFE9EFE7),
    onBackground = Color(0xFF1F2620),
    surface = Color(0xFFE9EFE7),
    onSurface = Color(0xFF1F2620),
    surfaceVariant = Color(0xFFDDE5DB),
    onSurfaceVariant = Color(0xFF4C554E),
    surfaceTint = Color(0xFF26563F),
    inverseSurface = Color(0xFF2C332D),
    inverseOnSurface = Color(0xFFECF3EA),
    inversePrimary = Color(0xFF8CD2AA),
    scrim = Color(0xFF000000),
    outline = Color(0xFF7A867A),
    outlineVariant = Color(0xFFC5CFC2),
    surfaceDim = Color(0xFFD8E1D5),
    surfaceBright = Color(0xFFF1F6EF),
    surfaceContainerLowest = Color(0xFFF3F8F0),
    surfaceContainerLow = Color(0xFFE1E7DF),
    surfaceContainer = Color(0xFFDAE0D8),
    surfaceContainerHigh = Color(0xFFD2D8D0),
    surfaceContainerHighest = Color(0xFFC9CFC7)
)

/**
 * 竹简雅韵深色变体。
 * 墨绿底色与竹金主色，保持夜间护眼特性。
 */
val BambooDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD9BC8A),
    onPrimary = Color(0xFF3B2E17),
    primaryContainer = Color(0xFF544426),
    onPrimaryContainer = Color(0xFFF1E1BE),
    secondary = Color(0xFFCFCBA8),
    onSecondary = Color(0xFF35331F),
    secondaryContainer = Color(0xFF3F4B37),
    onSecondaryContainer = Color(0xFFE2E8C5),
    tertiary = Color(0xFFA9CFAF),
    onTertiary = Color(0xFF143723),
    tertiaryContainer = Color(0xFF2C4E38),
    onTertiaryContainer = Color(0xFFC5EBC9),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF171A14),
    onBackground = Color(0xFFE2E3D6),
    surface = Color(0xFF171A14),
    onSurface = Color(0xFFE2E3D6),
    surfaceVariant = Color(0xFF43483C),
    onSurfaceVariant = Color(0xFFC3C8B4),
    surfaceTint = Color(0xFFD9BC8A),
    inverseSurface = Color(0xFFE2E3D6),
    inverseOnSurface = Color(0xFF2F3226),
    inversePrimary = Color(0xFF7A5230),
    scrim = Color(0xFF000000),
    outline = Color(0xFF8D927F),
    outlineVariant = Color(0xFF43483C),
    surfaceDim = Color(0xFF171A14),
    surfaceBright = Color(0xFF3D4036),
    surfaceContainerLowest = Color(0xFF121510),
    surfaceContainerLow = Color(0xFF1F221C),
    surfaceContainer = Color(0xFF232720),
    surfaceContainerHigh = Color(0xFF2D312A),
    surfaceContainerHighest = Color(0xFF383C34)
)

fun colorSchemeFor(style: ThemeStyle, isDark: Boolean): androidx.compose.material3.ColorScheme = when (style) {
    ThemeStyle.DYNAMIC,
    ThemeStyle.PARCHMENT ->
        if (isDark) InkDarkColorScheme else ParchmentColorScheme
    ThemeStyle.INDIGO ->
        if (isDark) IndigoDarkColorScheme else IndigoLightColorScheme
    ThemeStyle.BAMBOO ->
        if (isDark) BambooDarkColorScheme else BambooColorScheme
}

/** 在可组合作用域内解析色彩方案（在 Android 12+ 上若选中动态则走系统取色）。 */
@Composable
fun resolveColorScheme(style: ThemeStyle, isDark: Boolean): androidx.compose.material3.ColorScheme {
    val context = LocalContext.current
    return if (style == ThemeStyle.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        colorSchemeFor(style, isDark)
    }
}

/**
 * 主题缩略图预览色彩令牌。
 * 允许设置面板在当前全局主题下展示候选主题的配色样本。
 */
data class ThemePreviewColors(
    val surface: Color,
    val onSurface: Color,
    val primary: Color
)

@Composable
fun previewColorsFor(style: ThemeStyle, isDark: Boolean): ThemePreviewColors {
    val scheme = resolveColorScheme(style, isDark)
    return ThemePreviewColors(scheme.surface, scheme.onSurface, scheme.primary)
}

@Composable
fun OpusOneTheme(
    themeStyle: ThemeStyle = ThemeStyle.PARCHMENT,
    darkMode: DarkMode = DarkMode.LIGHT,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = darkMode.resolveDark(systemDark)
    val colorScheme = resolveColorScheme(themeStyle, isDark)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context.findActivity())?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    CompositionLocalProvider(LocalIsDarkTheme provides isDark) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = OpusOneShapes,
            content = content
        )
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
