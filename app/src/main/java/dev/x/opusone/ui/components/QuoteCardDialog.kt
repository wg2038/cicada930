package dev.x.opusone.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.x.opusone.theme.ClassicalFontFamily
import dev.x.opusone.theme.LocalChineseScript
import dev.x.opusone.util.toScript

/**
 * 引文卡片对话框 —— 用于《史记》段落摘录的复制与本地卡片保存。
 */
@Composable
fun QuoteCardDialog(
    chapterTitle: String,
    pnIndex: String,
    quoteText: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scriptMode = LocalChineseScript.current
    val convertedChapterTitle = chapterTitle.toScript(scriptMode)
    val convertedQuoteText = quoteText.toScript(scriptMode)
    val fullShareText = "「$convertedQuoteText」\n——《${"史记".toScript(scriptMode)} · $convertedChapterTitle》 (${"段落".toScript(scriptMode)} [$pnIndex])"
    val cs = MaterialTheme.colorScheme

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "《史记》典籍摘录".toScript(scriptMode),
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface
                    )
                    Text(
                        text = "$convertedChapterTitle · ${"段落".toScript(scriptMode)} [$pnIndex]",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }

                HorizontalDivider(color = cs.outlineVariant)

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = cs.secondaryContainer,
                    contentColor = cs.onSecondaryContainer
                ) {
                    Text(
                        text = convertedQuoteText,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = ClassicalFontFamily
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("史记摘录".toScript(scriptMode), fullShareText)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "引文已复制到剪贴板".toScript(scriptMode), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("复制".toScript(scriptMode), style = MaterialTheme.typography.labelLarge)
                    }

                    Button(
                        onClick = {
                            val cardTheme = CardThemeColors(
                                background = cs.background.toArgb(),
                                surface = cs.surfaceContainerLow.toArgb(),
                                onSurface = cs.onSurface.toArgb(),
                                primary = cs.primary.toArgb(),
                                onSurfaceVariant = cs.onSurfaceVariant.toArgb(),
                                outlineVariant = cs.outlineVariant.toArgb()
                            )
                            ClassicalCardExporter.saveCardToDownloads(
                                context = context,
                                scope = scope,
                                chapterTitle = convertedChapterTitle,
                                quoteText = convertedQuoteText,
                                cardTheme = cardTheme,
                                scriptMode = scriptMode
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("保存".toScript(scriptMode), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
