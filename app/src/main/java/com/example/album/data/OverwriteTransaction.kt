package com.example.album.data

/**
 * The four operations an overwrite needs, in the order the user cares about:
 * the old target is moved aside, the new file is committed under the real
 * name, and only then may the old copy be deleted.
 *
 * The interface is deliberately tiny so MediaStore rows and SAF documents can
 * share one transaction and so tests can fail each step without a device.
 */
internal interface OverwriteTransaction {
    /** Moves the old target to a temporary/backup name. */
    fun moveOldAside(): Boolean

    /** Renames the completed new file/target to its final name. */
    fun commitNew(): Boolean

    /** Deletes the old target after the new one has been committed. */
    fun deleteOld(): Boolean

    /** Removes the uncommitted new file/target. */
    fun discardNew()

    /** Puts the old target back under its original name. */
    fun restoreOld()
}

/**
 * Runs an overwrite transaction without ever deleting the old target before
 * the new one is committed.
 *
 * [deleteOld] is false for a Move that still has to delete its source: the
 * caller keeps the backup until the source removal has succeeded, so a failed
 * source delete cannot destroy both the source and the old destination.
 *
 * Returns true only when the new target is committed. On any failure the new
 * target is discarded and, unless the old target was never moved aside, it is
 * restored. Exceptions from the provider are treated as failures rather than
 * escaping with a half-finished transaction.
 */
internal fun runOverwriteTransaction(
    transaction: OverwriteTransaction,
    deleteOld: Boolean = true
): Boolean {
    if (!runCatching { transaction.moveOldAside() }.getOrDefault(false)) {
        runCatching { transaction.discardNew() }
        return false
    }
    if (!runCatching { transaction.commitNew() }.getOrDefault(false)) {
        runCatching { transaction.discardNew() }
        runCatching { transaction.restoreOld() }
        return false
    }
    if (deleteOld && !runCatching { transaction.deleteOld() }.getOrDefault(false)) {
        runCatching { transaction.discardNew() }
        runCatching { transaction.restoreOld() }
        return false
    }
    return true
}
