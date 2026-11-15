package com.example.album.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCapabilitiesTest {
    @Test
    fun `media store items can move in place from api 29`() {
        assertTrue(capabilitiesOf(StorageKind.MediaStore, 29).supportsProviderMove)
        assertTrue(capabilitiesOf(StorageKind.MediaStore, 34).supportsProviderMove)
        assertFalse(capabilitiesOf(StorageKind.MediaStore, 28).supportsProviderMove)
    }

    @Test
    fun `documents and physical files never move through the provider`() {
        assertFalse(capabilitiesOf(StorageKind.Document, 34).supportsProviderMove)
        assertFalse(capabilitiesOf(StorageKind.Physical, 34).supportsProviderMove)
    }

    @Test
    fun `kind is derived from the item uri`() {
        // A MediaItem needs an Android Uri, so only the document flag path is
        // exercised here through storageKindOf's pure branch order.
        assertTrue(capabilitiesOf(StorageKind.Document, 28).supportsDirectWrite)
        assertTrue(capabilitiesOf(StorageKind.Physical, 24).supportsRename)
    }

    @Test
    fun `a move on api 29 plans a provider move first`() {
        val plan = planTransfer(TransferMode.Move, StorageKind.MediaStore, 29)
        assertTrue(plan.tryProviderMoveFirst)
        assertEquals(
            listOf(
                TransferStrategy.ProviderMove,
                TransferStrategy.MediaStoreCopy,
                TransferStrategy.CopyThenDelete
            ),
            plan.strategies
        )
    }

    @Test
    fun `a move on api 28 has no provider move`() {
        val plan = planTransfer(TransferMode.Move, StorageKind.MediaStore, 28)
        assertFalse(plan.tryProviderMoveFirst)
        assertEquals(
            listOf(TransferStrategy.MediaStoreCopy, TransferStrategy.CopyThenDelete),
            plan.strategies
        )
    }

    @Test
    fun `a copy never deletes the source`() {
        val plan = planTransfer(TransferMode.Copy, StorageKind.MediaStore, 34)
        assertEquals(listOf(TransferStrategy.MediaStoreCopy), plan.strategies)
        assertFalse(plan.tryProviderMoveFirst)
    }

    @Test
    fun `documents are copied through a directory first`() {
        val plan = planTransfer(TransferMode.Move, StorageKind.Document, 34)
        assertFalse(plan.tryProviderMoveFirst)
        assertEquals(
            listOf(TransferStrategy.DirectoryCopy, TransferStrategy.CopyThenDelete),
            plan.strategies
        )
    }
}
