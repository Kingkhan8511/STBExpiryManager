package com.cableops.stbexpiry.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.cableops.stbexpiry.data.model.ExpiryRecord
import com.cableops.stbexpiry.ui.components.RecordCard
import com.cableops.stbexpiry.ui.theme.STBExpiryTheme
import com.cableops.stbexpiry.viewmodel.FilterTab
import com.cableops.stbexpiry.viewmodel.ImportResult
import com.cableops.stbexpiry.viewmodel.MainViewModel
import com.cableops.stbexpiry.worker.DailyExpiryWorker
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.time.LocalTime

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel.init(this)
        scheduleDailyWorker()
        requestNotificationPermissionIfNeeded()

        setContent {
            STBExpiryTheme {
                STBExpiryApp(viewModel = viewModel)
            }
        }
    }

    // ── WorkManager scheduling ────────────────────────────────────────────────────────

    /**
     * Schedules [DailyExpiryWorker] to run once every 24 hours.
     * [ExistingPeriodicWorkPolicy.KEEP] ensures re-opening the app doesn't reset the timer.
     *
     * Initial delay is calculated to fire at 09:00 AM local time on the next occurrence.
     */
    private fun scheduleDailyWorker() {
        val now = LocalTime.now()
        val targetHour = 9
        val secondsUntilTarget = if (now.hour < targetHour) {
            ((targetHour - now.hour) * 3600 - now.minute * 60 - now.second).toLong()
        } else {
            ((24 - now.hour + targetHour) * 3600 - now.minute * 60 - now.second).toLong()
        }

        val workRequest = PeriodicWorkRequestBuilder<DailyExpiryWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setInitialDelay(secondsUntilTarget, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DailyExpiryWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    // ── Notification permission (Android 13+) ─────────────────────────────────────────

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────────────
// Composable UI
// ─────────────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun STBExpiryApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val importResult by viewModel.importResult.collectAsState()
    val activeFilter by viewModel.activeFilter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val records by viewModel.filteredRecords.collectAsState()

    // Tracks records pending overwrite confirmation dialog
    var pendingRecords by remember { mutableStateOf<List<ExpiryRecord>>(emptyList()) }
    var showOverwriteDialog by remember { mutableStateOf(false) }

    // ── File picker launcher ──────────────────────────────────────────────────────────
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importFile(context, it) }
    }

    // ── Handle import state changes ───────────────────────────────────────────────────
    LaunchedEffect(importResult) {
        when (val result = importResult) {
            is ImportResult.Success -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("✔ ${result.count} records imported successfully")
                }
                viewModel.clearImportResult()
            }
            is ImportResult.Error -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("✘ ${result.message}")
                }
                viewModel.clearImportResult()
            }
            is ImportResult.ConfirmOverwrite -> {
                pendingRecords = result.parsedRecords
                showOverwriteDialog = true
            }
            else -> {}
        }
    }

    // ── Overwrite / Append dialog ─────────────────────────────────────────────────────
    if (showOverwriteDialog) {
        AlertDialog(
            onDismissRequest = {
                showOverwriteDialog = false
                viewModel.clearImportResult()
            },
            title   = { Text("Records Already Exist") },
            text    = {
                Text(
                    "The database already has records. Would you like to replace all existing " +
                    "records with the new import, or add new records alongside existing ones?"
                )
            },
            confirmButton = {
                Button(onClick = {
                    showOverwriteDialog = false
                    viewModel.confirmOverwrite(pendingRecords)
                }) {
                    Text("Overwrite")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showOverwriteDialog = false
                    viewModel.confirmAppend(pendingRecords)
                }) {
                    Text("Append")
                }
            }
        )
    }

    // ── Main Scaffold ─────────────────────────────────────────────────────────────────
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = "STB Expiry Manager",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            filePicker.launch(
                                arrayOf(
                                    "text/html",
                                    "application/vnd.ms-excel",
                                    "*/*"           // fallback for files with .xls extension
                                )
                            )
                        }
                    ) {
                        Icon(
                            imageVector        = Icons.Default.FolderOpen,
                            contentDescription = "Import File"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            // ── Search bar ────────────────────────────────────────────────────────
            OutlinedTextField(
                value         = searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder   = { Text("Search by name or STB number…") },
                leadingIcon   = {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                },
                singleLine    = true,
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )

            // ── Filter chips ──────────────────────────────────────────────────────
            LazyRow(
                contentPadding    = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier          = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChipItem(
                        label    = "All Records",
                        selected = activeFilter == FilterTab.ALL,
                        onClick  = { viewModel.setFilter(FilterTab.ALL) }
                    )
                }
                item {
                    FilterChipItem(
                        label    = "Expiring Today",
                        selected = activeFilter == FilterTab.TODAY,
                        onClick  = { viewModel.setFilter(FilterTab.TODAY) }
                    )
                }
                item {
                    FilterChipItem(
                        label    = "Upcoming (3 Days)",
                        selected = activeFilter == FilterTab.UPCOMING,
                        onClick  = { viewModel.setFilter(FilterTab.UPCOMING) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Loading indicator ─────────────────────────────────────────────────
            AnimatedVisibility(
                visible = importResult is ImportResult.Loading,
                enter   = fadeIn(),
                exit    = fadeOut()
            ) {
                Box(
                    modifier        = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(16.dp)
                            .size(36.dp)
                    )
                }
            }

            // ── Record list / empty state ─────────────────────────────────────────
            if (records.isEmpty() && importResult !is ImportResult.Loading) {
                EmptyState()
            } else {
                LazyColumn(
                    contentPadding    = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier          = Modifier.fillMaxSize()
                ) {
                    items(
                        items = records,
                        key   = { it.id }
                    ) { record ->
                        RecordCard(
                            record           = record,
                            isExpiredOrToday = viewModel.isExpiredOrToday(record.endDate),
                            isUpcoming       = !viewModel.isExpiredOrToday(record.endDate) &&
                                              viewModel.isUpcomingInDays(record.endDate, 3)
                        )
                    }
                }
            }
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────────────

@Composable
private fun FilterChipItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    ElevatedFilterChip(
        selected = selected,
        onClick  = onClick,
        label    = { Text(label) },
        colors   = FilterChipDefaults.elevatedFilterChipColors(
            selectedContainerColor     = MaterialTheme.colorScheme.primary,
            selectedLabelColor         = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
private fun EmptyState() {
    Box(
        modifier        = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier            = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector        = Icons.Default.Inbox,
                contentDescription = "No records",
                modifier           = Modifier.size(80.dp),
                tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text      = "No Records Found",
                style     = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color     = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text      = "Tap the folder icon in the top bar to import an XLS or HTML billing report.",
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}
