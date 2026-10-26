package com.cableops.stbexpiry.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cableops.stbexpiry.data.local.AppDatabase
import com.cableops.stbexpiry.data.model.ExpiryRecord
import com.cableops.stbexpiry.parser.ReportParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Determines which set of records is shown in the list. */
enum class FilterTab { ALL, TODAY, UPCOMING }

/** Sealed class representing the one-time import operation result. */
sealed class ImportResult {
    data object Idle : ImportResult()
    data object Loading : ImportResult()
    data class Success(val count: Int) : ImportResult()
    data class Error(val message: String) : ImportResult()
    /** Emitted when DB already has records – UI should ask the user what to do. */
    data class ConfirmOverwrite(val uri: Uri, val parsedRecords: List<ExpiryRecord>) : ImportResult()
}

class MainViewModel : ViewModel() {

    private lateinit var dao: com.cableops.stbexpiry.data.local.dao.RecordDao

    // ── UI State ──────────────────────────────────────────────────────────────────────

    private val _importResult = MutableStateFlow<ImportResult>(ImportResult.Idle)
    val importResult: StateFlow<ImportResult> = _importResult.asStateFlow()

    private val _activeFilter = MutableStateFlow(FilterTab.ALL)
    val activeFilter: StateFlow<FilterTab> = _activeFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** All records from DB, filtered and searched in real-time. */
    lateinit var filteredRecords: StateFlow<List<ExpiryRecord>>
        private set

    // ── Initialisation ────────────────────────────────────────────────────────────────

    fun init(context: Context) {
        if (::dao.isInitialized) return
        dao = AppDatabase.getInstance(context).recordDao()

        val allFlow = dao.getAllRecords()

        filteredRecords = combine(allFlow, _activeFilter, _searchQuery) { records, filter, query ->
            val filtered = when (filter) {
                FilterTab.ALL     -> records
                FilterTab.TODAY   -> records.filter { isExpiringToday(it.endDate) }
                FilterTab.UPCOMING -> records.filter { isUpcomingInDays(it.endDate, 3) }
            }
            if (query.isBlank()) filtered
            else filtered.filter { record ->
                record.name.contains(query, ignoreCase = true) ||
                record.stbNumber.contains(query, ignoreCase = true)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )
    }

    // ── Public actions ────────────────────────────────────────────────────────────────

    fun setFilter(tab: FilterTab) {
        _activeFilter.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Entry point for a file import.
     * - Parses the file on IO dispatcher.
     * - If DB has existing records → emits [ImportResult.ConfirmOverwrite] so the UI
     *   can ask the user whether to overwrite or append.
     * - If DB is empty → inserts immediately.
     */
    fun importFile(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _importResult.value = ImportResult.Loading
            try {
                val records = ReportParser.parseReport(context, uri)
                if (records.isEmpty()) {
                    _importResult.value = ImportResult.Error("No valid records found in the file.")
                    return@launch
                }
                val existingCount = dao.getCount()
                if (existingCount > 0) {
                    // Ask user: overwrite or append?
                    _importResult.value = ImportResult.ConfirmOverwrite(uri, records)
                } else {
                    dao.insertAll(records)
                    _importResult.value = ImportResult.Success(records.size)
                }
            } catch (e: Exception) {
                _importResult.value = ImportResult.Error(e.message ?: "Unknown parse error.")
            }
        }
    }

    /** Called after user confirms Overwrite dialog. Clears DB then inserts new records. */
    fun confirmOverwrite(records: List<ExpiryRecord>) {
        viewModelScope.launch(Dispatchers.IO) {
            _importResult.value = ImportResult.Loading
            try {
                dao.deleteAll()
                dao.insertAll(records)
                _importResult.value = ImportResult.Success(records.size)
            } catch (e: Exception) {
                _importResult.value = ImportResult.Error(e.message ?: "Failed to overwrite records.")
            }
        }
    }

    /** Called after user confirms Append dialog. Inserts new records alongside existing ones. */
    fun confirmAppend(records: List<ExpiryRecord>) {
        viewModelScope.launch(Dispatchers.IO) {
            _importResult.value = ImportResult.Loading
            try {
                dao.insertAll(records)
                _importResult.value = ImportResult.Success(records.size)
            } catch (e: Exception) {
                _importResult.value = ImportResult.Error(e.message ?: "Failed to append records.")
            }
        }
    }

    fun clearImportResult() {
        _importResult.value = ImportResult.Idle
    }

    // ── Date helpers ──────────────────────────────────────────────────────────────────

    private val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun isExpiringToday(dateStr: String): Boolean {
        return runCatching {
            LocalDate.parse(dateStr, formatter) == LocalDate.now()
        }.getOrDefault(false)
    }

    fun isExpiredOrToday(dateStr: String): Boolean {
        return runCatching {
            !LocalDate.parse(dateStr, formatter).isAfter(LocalDate.now())
        }.getOrDefault(false)
    }

    /** True if [dateStr] falls within the next [days] days (exclusive of today). */
    fun isUpcomingInDays(dateStr: String, days: Int): Boolean {
        return runCatching {
            val date = LocalDate.parse(dateStr, formatter)
            val today = LocalDate.now()
            date.isAfter(today) && !date.isAfter(today.plusDays(days.toLong()))
        }.getOrDefault(false)
    }
}
