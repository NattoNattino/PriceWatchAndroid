package com.nattonattino.pricewatch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureNotificationChannel(this)
        setContent {
            PriceWatchApp()
        }
    }
}

@Composable
fun PriceWatchApp(viewModel: PriceWatchViewModel = viewModel()) {
    val searches by viewModel.searches.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val selectedSearch by viewModel.selectedSearch.collectAsState()
    val context = LocalContext.current

    val darkTheme = when (settings.theme) {
        ThemeMode.SYSTEM -> false
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()

    MaterialTheme(colorScheme = colorScheme) {
        Scaffold { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HeaderSection(settings)

                SearchForm(
                    onSearch = { keyword, code, maxPrice, hour, minute ->
                        val result = viewModel.addSearch(keyword, code, maxPrice, hour, minute)
                        if (result != null) {
                            scheduleOneTimeCheck(context, result, hour, minute)
                        }
                    }
                )

                SettingsPanel(
                    settings = settings,
                    onLanguageChanged = viewModel::setLanguage,
                    onThemeChanged = viewModel::setTheme,
                    onNotificationTimeChanged = viewModel::setNotificationTime,
                    onClearAll = viewModel::clearAllSearches
                )

                if (selectedSearch != null) {
                    OfferDetailScreen(
                        search = selectedSearch!!,
                        onDeleteOffer = { offer -> viewModel.deleteOffer(selectedSearch!!.id, offer) },
                        onBack = { viewModel.selectSearch(null) }
                    )
                } else {
                    SearchListSection(
                        searches = searches,
                        onSelect = { viewModel.selectSearch(it.id) },
                        onDeleteSearch = { viewModel.deleteSearch(it.id) },
                        onDeleteAll = viewModel::clearAllSearches
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderSection(settings: AppSettings) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "PriceWatch",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = when (settings.language) {
                    Language.IT -> "Confronta offerte europee e ricevi alert"
                    Language.EN -> "Compare European offers and get price alerts"
                    Language.FR -> "Comparez les offres européennes et recevez des alertes"
                    Language.ES -> "Compara ofertas europeas y recibe alertas"
                    Language.DE -> "Vergleiche europäische Angebote und erhalte Benachrichtigungen"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(Icons.Default.Notifications, contentDescription = null)
    }
}

@Composable
private fun SearchForm(
    onSearch: (String, String?, Double?, Int, Int) -> Unit
) {
    var keyword by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var threshold by rememberSaveable { mutableStateOf("") }
    var useCode by rememberSaveable { mutableStateOf(false) }
    var usePrice by rememberSaveable { mutableStateOf(false) }
    var hour by rememberSaveable { mutableStateOf("9") }
    var minute by rememberSaveable { mutableStateOf("0") }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                label = { Text("Nome prodotto o keyword") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Usa codice / SKU / EAN")
                Switch(checked = useCode, onCheckedChange = { useCode = it })
            }

            if (useCode) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Codice SKU / EAN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Usa prezzo massimo")
                Switch(checked = usePrice, onCheckedChange = { usePrice = it })
            }

            if (usePrice) {
                OutlinedTextField(
                    value = threshold,
                    onValueChange = { threshold = it },
                    label = { Text("Prezzo massimo (€)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = hour,
                    onValueChange = { hour = it },
                    label = { Text("Ora") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = minute,
                    onValueChange = { minute = it },
                    label = { Text("Min") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = {
                    onSearch(
                        keyword.trim(),
                        if (useCode) code.trim().ifBlank { null } else null,
                        if (usePrice) threshold.toDoubleOrNull() else null,
                        hour.toIntOrNull() ?: 9,
                        minute.toIntOrNull() ?: 0
                    )
                    keyword = ""
                    code = ""
                    threshold = ""
                    useCode = false
                    usePrice = false
                },
                enabled = keyword.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Text("Cerca offerte")
            }
        }
    }
}

@Composable
private fun SettingsPanel(
    settings: AppSettings,
    onLanguageChanged: (Language) -> Unit,
    onThemeChanged: (ThemeMode) -> Unit,
    onNotificationTimeChanged: (Int, Int) -> Unit,
    onClearAll: () -> Unit
) {
    var showDeleteAll by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Text("Impostazioni", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Lingua")
                Text(settings.language.label)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Language.entries.forEach { language ->
                    TextButton(onClick = { onLanguageChanged(language) }) {
                        Text(language.label)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tema")
                Text(settings.theme.label)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { theme ->
                    TextButton(onClick = { onThemeChanged(theme) }) {
                        Text(theme.label)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Orario notifica migliore offerta")
                Text("${settings.notificationHour}:${String.format(Locale.ROOT, "%02d", settings.notificationMinute)}")
            }

            Button(
                onClick = { onNotificationTimeChanged(9, 0) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Imposta default 09:00")
            }

            Button(
                onClick = { showDeleteAll = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Elimina tutte le ricerche")
            }
        }
    }

    if (showDeleteAll) {
        AlertDialog(
            onDismissRequest = { showDeleteAll = false },
            title = { Text("Conferma eliminazione") },
            text = { Text("Vuoi rimuovere tutte le ricerche salvate?") },
            confirmButton = {
                TextButton(onClick = {
                    onClearAll()
                    showDeleteAll = false
                }) {
                    Text("Elimina")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAll = false }) {
                    Text("Annulla")
                }
            }
        )
    }
}

@Composable
private fun SearchListSection(
    searches: List<SearchResult>,
    onSelect: (SearchResult) -> Unit,
    onDeleteSearch: (SearchResult) -> Unit,
    onDeleteAll: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Ricerche salvate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onDeleteAll) {
                Text("Cancella tutto")
            }
        }

        if (searches.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Nessuna ricerca ancora.")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(searches, key = { it.id }) { search ->
                    SearchSummaryCard(
                        search = search,
                        onClick = { onSelect(search) },
                        onDelete = { onDeleteSearch(search) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSummaryCard(
    search: SearchResult,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDelete by remember { mutableStateOf(false) }

    SwipeToDeleteRow(
        onDelete = { showDelete = true },
        content = {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(search.keyword, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (!search.code.isNullOrBlank()) Text("Codice: ${search.code}")
                    if (search.maxPrice != null) Text("Prezzo target: €${"%.2f".format(search.maxPrice)}")
                    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                        Text("Vedi offerte")
                    }
                }
            }
        }
    )

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Elimina ricerca") },
            text = { Text("Vuoi rimuovere questa ricerca?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDelete = false
                }) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Annulla") }
            }
        )
    }
}

@Composable
private fun OfferDetailScreen(
    search: SearchResult,
    onDeleteOffer: (OfferResult) -> Unit,
    onBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(search.keyword, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onBack) { Text("Indietro") }
            }

            if (!search.code.isNullOrBlank()) {
                Text("Codice: ${search.code}")
            }
            if (search.maxPrice != null) {
                Text("Prezzo soglia: €${"%.2f".format(search.maxPrice)}")
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(search.offers, key = { it.shopName }) { offer ->
                    OfferItemCard(
                        offer = offer,
                        onDelete = { onDeleteOffer(offer) },
                        onOpen = { uriHandler.openUri(offer.url) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OfferItemCard(
    offer: OfferResult,
    onDelete: () -> Unit,
    onOpen: () -> Unit
) {
    SwipeToDeleteRow(
        onDelete = onDelete,
        content = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(offer.shopName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Prezzo: €${"%.2f".format(offer.price)}")
                    Text("Spedizione: €${"%.2f".format(offer.shipping)}")
                    Text("Prezzo totale: €${"%.2f".format(offer.totalPrice)}")
                    Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                        Text("Apri sito ${offer.shopName}")
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteRow(
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            value == SwipeToDismissBoxValue.EndToStart
        }
    )

    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFB3261E))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        },
        content = content,
        modifier = Modifier.fillMaxWidth()
    )

    if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TextButton(onClick = onDelete) {
                Text("Elimina")
            }
        }
    }
}

private fun scheduleOneTimeCheck(context: Context, search: SearchResult, hour: Int, minute: Int) {
    val delaySeconds = computeDelayUntilTime(hour, minute)
    val workRequest = androidx.work.OneTimeWorkRequestBuilder<PriceMonitorWorker>()
        .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
        .setInputData(
            workDataOf(
                PriceMonitorWorker.KEY_KEYWORD to search.keyword,
                PriceMonitorWorker.KEY_CODE to (search.code ?: ""),
                PriceMonitorWorker.KEY_TARGET_PRICE to (search.maxPrice ?: Double.MAX_VALUE)
            )
        )
        .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
        "monitor_${search.id}",
        ExistingPeriodicWorkPolicy.UPDATE,
        workRequest
    )
}

private fun computeDelayUntilTime(hour: Int, minute: Int): Long {
    val now = java.time.LocalDateTime.now()
    val target = now.withHour(hour.coerceIn(0, 23)).withMinute(minute.coerceIn(0, 59)).withSecond(0).withNano(0)
    var next = target
    if (!target.isAfter(now)) {
        next = target.plusDays(1)
    }
    return java.time.Duration.between(now, next).seconds
}

private fun ensureNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            "price_alerts",
            "Price Alerts",
            NotificationManager.IMPORTANCE_HIGH
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }
}

enum class Language(val label: String) {
    IT("Italiano"), EN("English"), FR("Français"), ES("Español"), DE("Deutsch")
}

enum class ThemeMode(val label: String) {
    SYSTEM("Sistema"), LIGHT("Chiaro"), DARK("Scuro")
}

data class AppSettings(
    val language: Language = Language.IT,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val notificationHour: Int = 9,
    val notificationMinute: Int = 0
)
