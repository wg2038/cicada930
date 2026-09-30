package dev.x.opusone

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.x.opusone.theme.DarkMode
import dev.x.opusone.theme.resolveDark
import dev.x.opusone.theme.OpusOneTheme
import dev.x.opusone.theme.ThemeActions
import dev.x.opusone.theme.ThemeStyle
import dev.x.opusone.theme.LocalThemeActions
import dev.x.opusone.data.OpusOneDatabaseHelper
import dev.x.opusone.theme.ChineseScriptMode
import dev.x.opusone.theme.LocalChineseScript
import dev.x.opusone.util.ChineseConverter
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 应用程序主入口 Activity。
 * 负责全局主题与简繁模式状态持有，以及数据库和离线词典的异步预热。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            launch(Dispatchers.IO) {
                OpusOneDatabaseHelper.getInstance(applicationContext).prewarm()
            }
            launch {
                ChineseConverter.initialize(applicationContext)
            }
        }

        val prefs = getSharedPreferences("opusone_prefs", Context.MODE_PRIVATE)

        val initialStyle = readEnumPref(prefs, "theme_style", ThemeStyle.PARCHMENT)
        val initialDark = readEnumPref(prefs, "dark_mode", DarkMode.FOLLOW_SYSTEM)
        val initialScript = try {
            ChineseScriptMode.valueOf(prefs.getString("script_mode", ChineseScriptMode.SIMPLIFIED.name) ?: ChineseScriptMode.SIMPLIFIED.name)
        } catch (e: Exception) {
            ChineseScriptMode.SIMPLIFIED
        }

        setContent {
            val isConverterReady by ChineseConverter.isReady.collectAsStateWithLifecycle()
            var themeStyle by remember { mutableStateOf(initialStyle) }
            var darkMode by remember { mutableStateOf(initialDark) }
            var scriptMode by remember { mutableStateOf(initialScript) }

            val systemDark = isSystemInDarkTheme()
            val isDark = darkMode.resolveDark(systemDark)

            fun setTheme(style: ThemeStyle) {
                themeStyle = style
                prefs.edit().putString("theme_style", style.name).apply()
            }

            fun setDarkMode(mode: DarkMode) {
                darkMode = mode
                prefs.edit().putString("dark_mode", mode.name).apply()
            }

            fun setScript(mode: ChineseScriptMode) {
                scriptMode = mode
                prefs.edit().putString("script_mode", mode.name).apply()
            }

            CompositionLocalProvider(
                LocalChineseScript provides scriptMode,
                LocalThemeActions provides ThemeActions(
                    currentStyle = themeStyle,
                    isDark = isDark,
                    setStyle = ::setTheme,
                    setDarkMode = ::setDarkMode
                )
            ) {
                OpusOneTheme(
                    themeStyle = themeStyle,
                    darkMode = darkMode
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        key(isConverterReady) {
                            MainNavigation(
                                currentThemeStyle = themeStyle,
                                currentDarkMode = darkMode,
                                onSetTheme = { setTheme(it) },
                                onSetDarkMode = { setDarkMode(it) },
                                currentScript = scriptMode,
                                onSetScript = { setScript(it) }
                            )
                        }
                    }
                }
            }
        }
    }

    private inline fun <reified T : Enum<T>> readEnumPref(
        prefs: android.content.SharedPreferences,
        key: String,
        default: T
    ): T = try {
        val saved = prefs.getString(key, null) ?: return default
        enumValues<T>().firstOrNull { it.name == saved } ?: default
    } catch (e: Exception) {
        default
    }
}
