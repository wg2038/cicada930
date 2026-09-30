package dev.x.opusone.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * 典籍语义数据色与图表色相令牌。
 *
 * 区别于 MaterialTheme.colorScheme 界面角色色（表面/文本/边框等），
 * 本文件仅定义两类独立于界面的特定色彩：
 * 1. 语义数据色：16 类 OAM 实体、三家注疏（集解/索隐/正义）及语法动词色彩。
 * 2. 图表分类色：知识图谱世系节点与编年长河纪元等图表色相。
 */

// 实体标注体系 · 浅色模式
internal val TagPersonColorLight = Color(0xFF8B4513)
internal val TagPlaceColorLight = Color(0xFF8A6100)
internal val TagOfficialColorLight = Color(0xFFA1121C)
internal val TagDynastyColorLight = Color(0xFF6C3FA8)
internal val TagIdentityColorLight = Color(0xFF34568F)
internal val TagBiologyColorLight = Color(0xFF1B6E2E)
internal val TagArtifactColorLight = Color(0xFF9A5B22)
internal val TagAstronomyColorLight = Color(0xFF3F4C9C)
internal val TagConceptColorLight = Color(0xFF2F4F4F)
internal val TagQuantityColorLight = Color(0xFF1F6F45)
internal val TagIdiomColorLight = Color(0xFFB3261E)

// 实体标注体系 · 深色模式
internal val TagPersonColorDark = Color(0xFFE5A876)
internal val TagPlaceColorDark = Color(0xFFE5C158)
internal val TagOfficialColorDark = Color(0xFFFF7B72)
internal val TagDynastyColorDark = Color(0xFFD2A8FF)
internal val TagIdentityColorDark = Color(0xFF79C0FF)
internal val TagBiologyColorDark = Color(0xFF7EE787)
internal val TagArtifactColorDark = Color(0xFFFFA657)
internal val TagAstronomyColorDark = Color(0xFFA5D6FF)
internal val TagConceptColorDark = Color(0xFF80CBC4)
internal val TagQuantityColorDark = Color(0xFF56D364)
internal val TagIdiomColorDark = Color(0xFFFF9B94)

// 动词类标注（背景与前景色）
internal val VerbMilitaryTextLight = Color(0xFF8E1F28)
internal val VerbMilitaryBgLight = Color(0x1FB3261E)
internal val VerbMilitaryTextDark = Color(0xFFFF7B72)
internal val VerbMilitaryBgDark = Color(0x33B02A37)

internal val VerbPenaltyTextLight = Color(0xFF0A4FA8)
internal val VerbPenaltyBgLight = Color(0x1F3D8BFD)
internal val VerbPenaltyTextDark = Color(0xFF79C0FF)
internal val VerbPenaltyBgDark = Color(0x330A58CA)

internal val VerbPoliticalTextLight = Color(0xFF6B4E00)
internal val VerbPoliticalBgLight = Color(0x1FD9A406)
internal val VerbPoliticalTextDark = Color(0xFFFFD700)
internal val VerbPoliticalBgDark = Color(0x337A5A00)

internal val VerbEconomicTextLight = Color(0xFF0F5C2E)
internal val VerbEconomicBgLight = Color(0x1F28A745)
internal val VerbEconomicTextDark = Color(0xFF7EE787)
internal val VerbEconomicBgDark = Color(0x33146C2E)

// 三家注三色出处徽标（集解 / 索隐 / 正义）
internal val SanJiaJijieLight = Color(0xFF1B6E2E)
internal val SanJiaSuoyinLight = Color(0xFF9C3D96)
internal val SanJiaZhengyiLight = Color(0xFF8B4513)
internal val SanJiaJijieDark = Color(0xFF7EE787)
internal val SanJiaSuoyinDark = Color(0xFFF0A0E8)
internal val SanJiaZhengyiDark = Color(0xFFE5A876)

/**
 * 图表与画布分类色板，各色相提供明暗两套对比度校准值。
 */
object ChartTones {
    val CrimsonLight = Color(0xFFB3261E)
    val CrimsonDark = Color(0xFFFFB4AB)

    val AmberLight = Color(0xFF8B5000)
    val AmberDark = Color(0xFFFFB951)

    val OchreLight = Color(0xFF7A4B25)
    val OchreDark = Color(0xFFE6B98A)

    val IndigoLight = Color(0xFF34568F)
    val IndigoDark = Color(0xFFA8C7FF)

    val JadeLight = Color(0xFF146C43)
    val JadeDark = Color(0xFF6FDB95)

    val VioletLight = Color(0xFF6C3FA8)
    val VioletDark = Color(0xFFD0AAFF)

    val TealLight = Color(0xFF00696B)
    val TealDark = Color(0xFF6FD8DB)
}

/**
 * 图表色相类别。数据模型仅声明色相类型，运行时通过 [resolve] 解析具体主题色彩。
 */
enum class ChartTone {
    CRIMSON,
    AMBER,
    OCHRE,
    INDIGO,
    JADE,
    VIOLET,
    TEAL
}

@Composable
@ReadOnlyComposable
fun ChartTone.resolve(): Color = when (this) {
    ChartTone.CRIMSON -> chartColor(ChartTones.CrimsonLight, ChartTones.CrimsonDark)
    ChartTone.AMBER -> chartColor(ChartTones.AmberLight, ChartTones.AmberDark)
    ChartTone.OCHRE -> chartColor(ChartTones.OchreLight, ChartTones.OchreDark)
    ChartTone.INDIGO -> chartColor(ChartTones.IndigoLight, ChartTones.IndigoDark)
    ChartTone.JADE -> chartColor(ChartTones.JadeLight, ChartTones.JadeDark)
    ChartTone.VIOLET -> chartColor(ChartTones.VioletLight, ChartTones.VioletDark)
    ChartTone.TEAL -> chartColor(ChartTones.TealLight, ChartTones.TealDark)
}

@Composable
@ReadOnlyComposable
fun chartColor(light: Color, dark: Color): Color =
    if (LocalIsDarkTheme.current) dark else light

@Composable
@ReadOnlyComposable
fun sanJiaJijieColor(): Color =
    if (LocalIsDarkTheme.current) SanJiaJijieDark else SanJiaJijieLight

@Composable
@ReadOnlyComposable
fun sanJiaSuoyinColor(): Color =
    if (LocalIsDarkTheme.current) SanJiaSuoyinDark else SanJiaSuoyinLight

@Composable
@ReadOnlyComposable
fun sanJiaZhengyiColor(): Color =
    if (LocalIsDarkTheme.current) SanJiaZhengyiDark else SanJiaZhengyiLight
