package com.example.album.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicateScanUiTest {
    @Test
    fun commandBecomesRescanAfterACompletedPass() {
        assertEquals("查重", duplicateScanCommandLabel(scanCompleted = false))
        assertEquals("重新扫描", duplicateScanCommandLabel(scanCompleted = true))
    }
}
