package com.example.album.ui

import com.example.album.data.TransferFailure
import com.example.album.data.TransferMode

/**
 * What one transfer run ended up doing, in the words the UI shows.
 *
 * The summary used to be assembled inline inside `AlbumivApp`'s confirm callback,
 * which made the wording impossible to test and easy to get subtly wrong (Copy
 * vs Move, pending source deletion, skipped rows). The composition lives here.
 */
data class TransferOutcome(
    val mode: TransferMode,
    /** Rows that actually landed at the destination. */
    val completed: Int,
    /** Rows skipped by the conflict policy. */
    val skipped: Int,
    /** Rows that failed for any reason. */
    val failed: Int,
    val failures: Map<TransferFailure, Int> = emptyMap(),
    /** The copy worked but removing the source waits for a system dialog. */
    val sourceDeletePending: Boolean = false,
    /** Launching that delete dialog failed, so the sources were kept. */
    val sourceDeleteRequestFailed: Boolean = false
)

internal fun transferFailureLabel(failure: TransferFailure, english: Boolean): String = appText(
    when (failure) {
        TransferFailure.SourceUnreadable -> "源文件无法读取"
        TransferFailure.DestinationUnwritable -> "目标文件夹不可写"
        TransferFailure.ConflictUnresolved -> "同名冲突无法解决"
        TransferFailure.VerificationFailed -> "写入后校验失败"
        TransferFailure.SourceDeleteFailed -> "源文件删除失败"
        TransferFailure.Unknown -> "未知原因"
    },
    english
)

internal fun transferActionLabel(outcome: TransferOutcome, english: Boolean): String = when {
    // The toast reads as a sentence ("Moved 3 items"), so this uses past-tense
    // wording rather than the dictionary's base verbs.
    outcome.mode == TransferMode.Copy || outcome.sourceDeleteRequestFailed ->
        if (english) "Copied" else "复制"
    outcome.sourceDeletePending ->
        if (english) "Awaiting source deletion confirmation" else "等待确认删除源文件"
    else -> if (english) "Moved" else "移动"
}

/** One-line summary for the toast shown after a transfer. */
fun transferOutcomeText(outcome: TransferOutcome, english: Boolean): String {
    val action = transferActionLabel(outcome, english)
    val parts = buildList {
        add(if (english) "$action ${outcome.completed} items" else "$action ${outcome.completed} 项")
        if (outcome.skipped > 0) {
            add(if (english) "Skipped ${outcome.skipped} items" else "跳过 ${outcome.skipped} 项")
        }
        if (outcome.failed > 0) {
            val reasons = outcome.failures.entries.joinToString(if (english) ", " else "、") {
                transferFailureLabel(it.key, english) + "×" + it.value
            }
            add((if (english) "Failed ${outcome.failed}: " else "失败 ${outcome.failed} 项：") + reasons)
        }
    }
    return parts.joinToString(if (english) ", " else "，")
}
