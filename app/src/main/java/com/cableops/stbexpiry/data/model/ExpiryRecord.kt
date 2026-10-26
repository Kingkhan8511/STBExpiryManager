package com.cableops.stbexpiry.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a single STB expiry record extracted from the imported report.
 * Only three fields are persisted to keep storage minimal and queries fast.
 *
 * The composite unique index on (stbNumber, endDate) prevents duplicate records when
 * appending from multiple file imports.
 */
@Entity(
    tableName = "expiry_records",
    indices = [Index(value = ["stbNumber", "endDate"], unique = true)]
)
data class ExpiryRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Customer's display name as printed on the billing report. */
    val name: String,
    /** Set-Top Box serial / subscriber number. */
    val stbNumber: String,
    /** Expiry date stored as "DD/MM/YYYY" string, matching the source report format. */
    val endDate: String
)
