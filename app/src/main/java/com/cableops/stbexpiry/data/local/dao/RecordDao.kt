package com.cableops.stbexpiry.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cableops.stbexpiry.data.model.ExpiryRecord
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [ExpiryRecord].
 * All write operations are suspend functions; live queries return a [Flow] for reactive UI updates.
 */
@Dao
interface RecordDao {

    /**
     * Insert or replace a list of records. Using REPLACE strategy means that on a
     * unique-index collision the old row is deleted and the new one inserted,
     * effectively updating stale data from re-imports.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<ExpiryRecord>)

    /** Wipe the entire table – called when the user chooses "Overwrite" during import. */
    @Query("DELETE FROM expiry_records")
    suspend fun deleteAll()

    /**
     * Return all records whose [ExpiryRecord.endDate] matches [dateString] ("DD/MM/YYYY").
     * Used by [DailyExpiryWorker] to check today's expiries.
     */
    @Query("SELECT * FROM expiry_records WHERE endDate = :dateString")
    suspend fun getRecordsByDate(dateString: String): List<ExpiryRecord>

    /**
     * Live stream of all records ordered by newest-inserted first.
     * Collected by the ViewModel so the UI reacts to every database change.
     */
    @Query("SELECT * FROM expiry_records ORDER BY id DESC")
    fun getAllRecords(): Flow<List<ExpiryRecord>>

    /**
     * Count helper – used to decide whether the DB already has data on first launch
     * so we can show the empty-state illustration immediately.
     */
    @Query("SELECT COUNT(*) FROM expiry_records")
    suspend fun getCount(): Int
}
