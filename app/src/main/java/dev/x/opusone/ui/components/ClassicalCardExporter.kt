package dev.x.opusone.ui.components

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import dev.x.opusone.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import dev.x.opusone.theme.ChineseScriptMode
import dev.x.opusone.util.toScript

data class CardThemeColors(
    val background: Int,
    val surface: Int,
    val onSurface: Int,
    val primary: Int,
    val onSurfaceVariant: Int,
    val outlineVariant: Int
)

object ClassicalCardExporter {

    fun saveCardToDownloads(
        context: Context,
        scope: CoroutineScope,
        chapterTitle: String,
        quoteText: String,
        cardTheme: CardThemeColors,
        scriptMode: ChineseScriptMode = ChineseScriptMode.SIMPLIFIED
    ) {
        scope.launch(Dispatchers.IO) {
            var bitmap: Bitmap? = null
            try {
                bitmap = createCardBitmap(context, chapterTitle, quoteText, cardTheme, scriptMode)
                val fileName = getNextFileName(context)
                val success = saveBitmap(context, bitmap, fileName)
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(context, "已保存至下载目录：$fileName".toScript(scriptMode), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "保存失败，请稍后重试".toScript(scriptMode), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "保存异常: ${e.message}".toScript(scriptMode), Toast.LENGTH_SHORT).show()
                }
            } finally {
                bitmap?.recycle()
            }
        }
    }

    private fun getNextFileName(context: Context): String {
        val prefs = context.getSharedPreferences("cicada_export_prefs", Context.MODE_PRIVATE)
        var maxIndex = prefs.getInt("last_export_index", 0)

        // 查询 MediaStore.Downloads 已存在文件 (Android 10+)
        try {
            val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE 'Cicada_%.png'"
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null
            )?.use { cursor ->
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val regex = Regex("^Cicada_(\\d+)\\.png$")
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol) ?: continue
                    val match = regex.find(name)
                    if (match != null) {
                        val num = match.groupValues[1].toIntOrNull() ?: 0
                        if (num > maxIndex) {
                            maxIndex = num
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        val nextIndex = maxIndex + 1
        prefs.edit().putInt("last_export_index", nextIndex).apply()
        return String.format(java.util.Locale.US, "Cicada_%03d.png", nextIndex)
    }

    private fun saveBitmap(context: Context, bitmap: Bitmap, fileName: String): Boolean {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Cicada")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues) ?: return false
        val streamOk = try {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: false
        } catch (e: Exception) {
            false
        }
        if (!streamOk) {
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) {}
            return false
        }

        contentValues.clear()
        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)
        return true
    }

    /**
     * 生成古典书页风格引文图片。
     * 根据文本字数反比估算基准字号，并通过二分逼近计算安全边界内的最优字号与断行。
     */
    private fun createCardBitmap(
        context: Context,
        chapterTitle: String,
        quoteText: String,
        theme: CardThemeColors,
        scriptMode: ChineseScriptMode = ChineseScriptMode.SIMPLIFIED
    ): Bitmap {
        val size = 1440
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val serifTypeface = try {
            ResourcesCompat.getFont(context, R.font.noto_serif_sc) ?: Typeface.SERIF
        } catch (_: Exception) {
            Typeface.SERIF
        }

        val bgPaint = Paint().apply {
            color = theme.background
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        val margin = 72f
        val cardRect = RectF(margin, margin, size - margin, size - margin)
        val cardFillPaint = Paint().apply {
            color = theme.surface
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val cardBorderPaint = Paint().apply {
            color = (theme.outlineVariant and 0x00FFFFFF) or (0x59 shl 24)
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        canvas.drawRoundRect(cardRect, 36f, 36f, cardFillPaint)
        canvas.drawRoundRect(cardRect, 36f, 36f, cardBorderPaint)

        val contentLeft = 144f
        val contentRight = 1296f
        val maxTextWidth = contentRight - contentLeft

        val headerLabelPaint = Paint().apply {
            color = theme.primary
            textSize = 50f
            isAntiAlias = true
            isFakeBoldText = true
            typeface = serifTypeface
            letterSpacing = 0.08f
        }
        canvas.drawText("「 一 家 言 」".toScript(scriptMode), contentLeft, 175f, headerLabelPaint)

        val headerSourcePaint = Paint().apply {
            color = theme.onSurfaceVariant
            textSize = 48f
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
            typeface = serifTypeface
            letterSpacing = 0.04f
        }
        canvas.drawText("《史记 · $chapterTitle》".toScript(scriptMode), contentRight, 175f, headerSourcePaint)

        val dividerPaint = Paint().apply {
            color = (theme.outlineVariant and 0x00FFFFFF) or (0x4D shl 24)
            strokeWidth = 2f
            isAntiAlias = true
        }
        canvas.drawLine(contentLeft, 225f, contentRight, 225f, dividerPaint)

        val cleanText = quoteText.trim()
        val textLength = cleanText.length

        val displayAreaTop = 225f
        val cardBottom = 1368f
        val totalDisplayHeight = cardBottom - displayAreaTop

        val bottomSafetyMargin = 57f
        val topSafetyMargin = 70f
        val maxSafeHeight = totalDisplayHeight - topSafetyMargin - bottomSafetyMargin

        val noLineStartChars = "，。！？；：、”’）》〉』〕】…—"
        fun breakTextIntoLines(text: String, paint: Paint, maxWidth: Float): List<String> {
            val result = mutableListOf<String>()
            for (para in text.split("\n")) {
                if (para.isEmpty()) {
                    result.add("")
                    continue
                }
                val codePoints = para.codePoints().toArray()
                val sb = StringBuilder()
                for (cp in codePoints) {
                    val ch = String(intArrayOf(cp), 0, 1)
                    val test = sb.toString() + ch
                    if (sb.isNotEmpty() && paint.measureText(test) > maxWidth) {
                        val lastCp = if (sb.isNotEmpty()) sb.codePointBefore(sb.length) else -1
                        val lastCpCharCount = if (lastCp != -1) Character.charCount(lastCp) else 0
                        if (ch in noLineStartChars && sb.length > lastCpCharCount) {
                            val lastChar = sb.substring(sb.length - lastCpCharCount)
                            sb.delete(sb.length - lastCpCharCount, sb.length)
                            result.add(sb.toString())
                            sb.setLength(0)
                            sb.append(lastChar).append(ch)
                        } else {
                            result.add(sb.toString())
                            sb.setLength(0)
                            sb.append(ch)
                        }
                    } else {
                        sb.append(ch)
                    }
                }
                if (sb.isNotEmpty()) {
                    result.add(sb.toString())
                }
            }
            return result
        }

        val bodyPaint = Paint().apply {
            color = theme.onSurface
            isAntiAlias = true
            typeface = serifTypeface
        }

        val rawEstimatedSize = (820f / Math.sqrt(Math.max(1, textLength).toDouble())).toFloat()
        var fontSize = rawEstimatedSize.coerceIn(28f, 78f)
        var lineHeight = fontSize * 1.72f
        val letterSpacingVal = when {
            textLength <= 30 -> 0.05f
            textLength <= 70 -> 0.04f
            textLength <= 150 -> 0.03f
            textLength <= 250 -> 0.02f
            else -> 0.01f
        }
        var quoteMarkSize = (fontSize * 2.8f).coerceIn(120f, 220f)
        val quoteMarkGap = 28f

        bodyPaint.textSize = fontSize
        bodyPaint.letterSpacing = letterSpacingVal

        var lines = breakTextIntoLines(cleanText, bodyPaint, maxTextWidth)
        var quoteMarkVisualHeight = quoteMarkSize * 0.5f
        var totalBlockHeight = quoteMarkVisualHeight + quoteMarkGap + (lines.size * lineHeight)

        if (totalBlockHeight > maxSafeHeight && fontSize > 24f) {
            var low = 24f
            var high = fontSize
            var bestFontSize = 24f
            var bestLines = breakTextIntoLines(cleanText, bodyPaint.apply { textSize = 24f }, maxTextWidth)
            var bestQuoteMarkSize = (24f * 2.8f).coerceIn(110f, 220f)
            var bestLineHeight = 24f * 1.70f
            var bestBlockHeight = bestQuoteMarkSize * 0.5f + quoteMarkGap + (bestLines.size * bestLineHeight)

            while (high - low >= 0.5f) {
                val mid = Math.round((low + high) / 2f * 2f) / 2f
                val midLineHeight = mid * 1.70f
                val midQuoteMarkSize = (mid * 2.8f).coerceIn(110f, 220f)
                bodyPaint.textSize = mid
                val midLines = breakTextIntoLines(cleanText, bodyPaint, maxTextWidth)
                val midVisualHeight = midQuoteMarkSize * 0.5f
                val midBlockHeight = midVisualHeight + quoteMarkGap + (midLines.size * midLineHeight)

                if (midBlockHeight <= maxSafeHeight) {
                    bestFontSize = mid
                    bestLines = midLines
                    bestQuoteMarkSize = midQuoteMarkSize
                    bestLineHeight = midLineHeight
                    bestBlockHeight = midBlockHeight
                    low = mid + 0.5f
                } else {
                    high = mid - 0.5f
                }
            }

            fontSize = bestFontSize
            lines = bestLines
            quoteMarkSize = bestQuoteMarkSize
            lineHeight = bestLineHeight
            quoteMarkVisualHeight = quoteMarkSize * 0.5f
            totalBlockHeight = bestBlockHeight
            bodyPaint.textSize = fontSize
        }

        if (totalBlockHeight > maxSafeHeight) {
            val maxAllowedLines = ((maxSafeHeight - quoteMarkVisualHeight - quoteMarkGap) / lineHeight).toInt().coerceAtLeast(1)
            lines = lines.take(maxAllowedLines - 1) + "〔节录 · 全文 ${textLength} 字〕"
            totalBlockHeight = quoteMarkVisualHeight + quoteMarkGap + (lines.size * lineHeight)
        }

        val blockTop = displayAreaTop + (totalDisplayHeight - totalBlockHeight) / 2f

        val quoteMarkPaint = Paint().apply {
            color = (theme.primary and 0x00FFFFFF) or (0x59 shl 24)
            textSize = quoteMarkSize
            isAntiAlias = true
            typeface = serifTypeface
        }
        val quoteMarkY = blockTop + quoteMarkSize * 0.75f
        canvas.drawText("“", contentLeft, quoteMarkY, quoteMarkPaint)

        var currentY = blockTop + quoteMarkVisualHeight + quoteMarkGap + fontSize * 0.85f
        for (line in lines) {
            canvas.drawText(line, contentLeft, currentY, bodyPaint)
            currentY += lineHeight
        }

        return bitmap
    }
}
