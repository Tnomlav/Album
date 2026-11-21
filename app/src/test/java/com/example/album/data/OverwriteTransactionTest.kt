package com.example.album.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Failure injection for the overwrite transaction shared by the MediaStore
 * insert path and the SAF copy/move paths.
 *
 * The fake models the two things a user cares about: the old target is either
 * still under its original name or gone, and the uncommitted new file is not
 * left behind. Each test fails one provider step on purpose.
 */
class OverwriteTransactionTest {
    private class FakeTransaction(
        private val failMoveAside: Boolean = false,
        private val failCommit: Boolean = false,
        private val failDeleteOld: Boolean = false,
        private val throwOnMoveAside: Boolean = false,
        private val throwOnCommit: Boolean = false
    ) : OverwriteTransaction {
        var oldAtTarget = true
        var oldInBackup = false
        var newExists = true
        var newAtTarget = false
        var discardCalls = 0
        var restoreCalls = 0

        override fun moveOldAside(): Boolean {
            if (throwOnMoveAside) error("move failed")
            if (failMoveAside) return false
            oldAtTarget = false
            oldInBackup = true
            return true
        }

        override fun commitNew(): Boolean {
            if (throwOnCommit) error("commit failed")
            if (failCommit) return false
            newAtTarget = true
            return true
        }

        override fun deleteOld(): Boolean {
            if (failDeleteOld) return false
            oldInBackup = false
            return true
        }

        override fun discardNew() {
            discardCalls++
            newExists = false
            newAtTarget = false
        }

        override fun restoreOld() {
            restoreCalls++
            if (oldInBackup) {
                oldInBackup = false
                oldAtTarget = true
            }
        }
    }

    @Test
    fun successCommitsNewAndDeletesOld() {
        val transaction = FakeTransaction()
        assertTrue(runOverwriteTransaction(transaction))
        assertFalse(transaction.oldAtTarget)
        assertFalse(transaction.oldInBackup)
        assertTrue(transaction.newAtTarget)
        assertEquals(0, transaction.discardCalls)
        assertEquals(0, transaction.restoreCalls)
    }

    @Test
    fun mediaStoreRenameFailureLeavesOldTargetAndDiscardsNewCopy() {
        val transaction = FakeTransaction(failMoveAside = true)
        assertFalse(runOverwriteTransaction(transaction))
        assertTrue(transaction.oldAtTarget)
        assertFalse(transaction.newExists)
        assertEquals(1, transaction.discardCalls)
        assertEquals(0, transaction.restoreCalls)
    }

    @Test
    fun mediaStoreCommitFailureRestoresOldTarget() {
        val transaction = FakeTransaction(failCommit = true)
        assertFalse(runOverwriteTransaction(transaction))
        assertTrue("the old MediaStore target must still be under its name", transaction.oldAtTarget)
        assertFalse(transaction.newExists)
        assertEquals(1, transaction.restoreCalls)
    }

    @Test
    fun mediaStoreOldDeleteFailureRestoresOldTarget() {
        val transaction = FakeTransaction(failDeleteOld = true)
        assertFalse(runOverwriteTransaction(transaction))
        assertTrue(transaction.oldAtTarget)
        assertFalse(transaction.oldInBackup)
        assertFalse(transaction.newExists)
        assertEquals(1, transaction.restoreCalls)
    }

    @Test
    fun safCommitFailureRestoresOldTarget() {
        val transaction = FakeTransaction(failCommit = true)
        assertFalse(runOverwriteTransaction(transaction))
        assertTrue("the old SAF document must still be under its name", transaction.oldAtTarget)
        assertFalse(transaction.newExists)
        assertEquals(1, transaction.restoreCalls)
    }

    @Test
    fun safOldDeleteFailureRestoresOldTarget() {
        val transaction = FakeTransaction(failDeleteOld = true)
        assertFalse(runOverwriteTransaction(transaction))
        assertTrue(transaction.oldAtTarget)
        assertFalse(transaction.oldInBackup)
        assertFalse(transaction.newExists)
    }

    @Test
    fun moveKeepsTheBackupUntilTheSourceDeleteHasSucceeded() {
        val transaction = FakeTransaction()
        assertTrue(runOverwriteTransaction(transaction, deleteOld = false))
        assertTrue("the old target is still available for rollback", transaction.oldInBackup)
        assertTrue(transaction.newAtTarget)

        // The caller deletes the source here; only then is the backup removed.
        assertTrue(transaction.deleteOld())
        assertFalse(transaction.oldAtTarget)
        assertFalse(transaction.oldInBackup)
    }

    @Test
    fun providerExceptionIsTreatedAsFailureAndDiscardsTheNewCopy() {
        val transaction = FakeTransaction(throwOnMoveAside = true)
        assertFalse(runOverwriteTransaction(transaction))
        assertTrue(transaction.oldAtTarget)
        assertFalse(transaction.newExists)
    }

    @Test
    fun providerExceptionDuringCommitRestoresTheOldTarget() {
        val transaction = FakeTransaction(throwOnCommit = true)
        assertFalse(runOverwriteTransaction(transaction))
        assertTrue(transaction.oldAtTarget)
        assertFalse(transaction.newExists)
        assertEquals(1, transaction.restoreCalls)
    }
}
