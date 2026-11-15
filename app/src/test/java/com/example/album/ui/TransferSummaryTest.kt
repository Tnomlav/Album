package com.example.album.ui

import com.example.album.data.TransferFailure
import com.example.album.data.TransferMode
import org.junit.Assert.assertEquals
import org.junit.Test

class TransferSummaryTest {
    @Test
    fun `a plain move reports the moved count`() {
        val text = transferOutcomeText(
            TransferOutcome(mode = TransferMode.Move, completed = 3, skipped = 0, failed = 0),
            english = false
        )
        assertEquals("移动 3 项", text)
    }

    @Test
    fun `a copy says copied even when the source delete failed`() {
        val text = transferOutcomeText(
            TransferOutcome(
                mode = TransferMode.Move,
                completed = 2,
                skipped = 0,
                failed = 0,
                sourceDeleteRequestFailed = true
            ),
            english = false
        )
        assertEquals("复制 2 项", text)
    }

    @Test
    fun `a pending source deletion is called out`() {
        val text = transferOutcomeText(
            TransferOutcome(
                mode = TransferMode.Move,
                completed = 2,
                skipped = 0,
                failed = 0,
                sourceDeletePending = true
            ),
            english = false
        )
        assertEquals("等待确认删除源文件 2 项", text)
    }

    @Test
    fun `skipped and failed rows are appended with reasons`() {
        val text = transferOutcomeText(
            TransferOutcome(
                mode = TransferMode.Copy,
                completed = 1,
                skipped = 2,
                failed = 1,
                failures = mapOf(TransferFailure.SourceUnreadable to 1)
            ),
            english = true
        )
        assertEquals(
            "Copied 1 items, Skipped 2 items, Failed 1: The source file cannot be read×1",
            text
        )
    }

    @Test
    fun `english and chinese summaries differ`() {
        val outcome = TransferOutcome(mode = TransferMode.Move, completed = 1, skipped = 0, failed = 0)
        assertEquals("Moved 1 items", transferOutcomeText(outcome, english = true))
        assertEquals("移动 1 项", transferOutcomeText(outcome, english = false))
    }
}
