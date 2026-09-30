package dev.x.opusone.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * 全局汉字正字法模式（简体中文 / 繁體中文）。
 */
enum class ChineseScriptMode {
    SIMPLIFIED,
    TRADITIONAL;

    fun toggle(): ChineseScriptMode = when (this) {
        SIMPLIFIED -> TRADITIONAL
        TRADITIONAL -> SIMPLIFIED
    }

    val label: String
        get() = when (this) {
            SIMPLIFIED -> "简"
            TRADITIONAL -> "繁"
        }

    val fullLabel: String
        get() = when (this) {
            SIMPLIFIED -> "简体中文"
            TRADITIONAL -> "繁體中文"
        }
}

val LocalChineseScript = compositionLocalOf { ChineseScriptMode.SIMPLIFIED }
