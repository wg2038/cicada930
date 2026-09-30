package dev.x.opusone.util

import android.util.Log
import kotlinx.coroutines.CancellationException

/**
 * 协程异常安全包装工具函数。
 *
 * 捕获并记录业务异常，执行 [onError] 回调；
 * 保持重抛 [CancellationException] 以保证协程取消机制正常生效。
 *
 * @param tag 日志标签
 * @param what 操作名称
 * @param onError 异常回调（如复位加载状态）
 * @param block 执行代码块
 */
inline fun guardCoroutine(
    tag: String,
    what: String,
    onError: () -> Unit = {},
    block: () -> Unit
) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(tag, "$what failed", e)
        onError()
    }
}
