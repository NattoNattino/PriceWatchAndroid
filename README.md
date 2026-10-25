package com.nattonattino.pricewatch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PriceWatchApp()
                }
            }
        }
    }
}

@Composable
fun PriceWatchApp(viewModel: PriceWatchViewModel = viewModel()) {
    val searches by viewModel.searches.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val selectedSearch by viewModel.selectedSearch.collectAsState()
    val context = LocalContext.current

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeaderSection(settings)

            SearchForm(
                onSearch = { keyword, code, maxPrice, hour, minute ->
                    val result = viewModel.addSearch(keyword, code, maxPrice)
                    if (result != null) {
                        schedulePeriodicCheck(context, result, hour, minute)
                        viewModel.selectSearch(result.id)
                    }
                }
            )

            SettingsPanel(
                settings = settings,
                onLanguageChanged = { viewModel.setLanguage(it) },
                onThemeChanged = { viewModel.setTheme(it) },
                onNotificationTimeChanged = { h, m -> viewModel.setNotificationTime(h, m) },
                onClearAll = { viewModel.clearAllSearches() }
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
                    onDeleteSearch = { viewModel.deleteSearch(it) },
                    onDeleteAll = { viewModel.clearAllSearches() }
                )
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
                    Language.EN -> "Compare European offers and receive alerts"
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
    var minute by rememberSaveable { mutableStateOf("00") }

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

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    val parsedPrice = threshold.toDoubleOrNull()
                    onSearch(
                        keyword.trim(),
                        if (useCode) code.trim().ifBlank { null } else null,
                        if (usePrice) parsedPrice else null,
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
                Text("${settings.notificationHour}:${String.format("%02d", settings.notificationMinute)}")
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
                    onClearAll();
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
        onDelete = {
            showDelete = true
        },
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
                    onDelete();
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
                    SwipeToDeleteRow(
                        onDelete = { onDeleteOffer(offer) },
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
                                    Button(
                                        onClick = { uriHandler.openUri(offer.url) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Apri sito ${offer.shopName}")
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteRow(
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                // do not delete immediately; just reveal background and wait for user tap on delete icon
                true
            } else {
                false
            }
        }
    )

    val swiped = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
    val offset = animateFloatAsState(targetValue = if (swiped) 1f else 0f)

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
                    modifier = Modifier
                        .scale(offset.value)
                        .padding(end = 16.dp),
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
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd
        ) {
            TextButton(onClick = onDelete) {
                Text("Elimina")
            }
        }
    }
}

private fun schedulePeriodicCheck(context: Context, search: SearchResult, hour: Int, minute: Int) {
    val request = PeriodicWorkRequestBuilder<PriceMonitorWorker>(6, TimeUnit.HOURS)
        .setInputData(
            workDataOf(
                PriceMonitorWorker.KEY_KEYWORD to search.keyword,
                PriceMonitorWorker.KEY_CODE to (search.code ?: ""),
                PriceMonitorWorker.KEY_TARGET_PRICE to (search.maxPrice ?: Double.MAX_VALUE)
            )
        )
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "monitor_${search.id}",
        ExistingPeriodicWorkPolicy.UPDATE,
        request
    )
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
    IT("Italiano"),
    EN("English"),
    FR("Français"),
    ES("Español"),
    DE("Deutsch")
}

enum class ThemeMode(val label: String) {
    SYSTEM("Sistema"),
    LIGHT("Chiaro"),
    DARK("Scuro")
}

data class AppSettings(
    val language: Language = Language.IT,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val notificationHour: Int = 9,
    val notificationMinute: Int = 0
)

class PriceWatchViewModel : androidx.lifecycle.ViewModel() {
    private val _searches = kotlinx.coroutines.flow.MutableStateFlow<List<SearchResult>>(emptyList())
    val searches: kotlinx.coroutines.flow.StateFlow<List<SearchResult>> = _searches

    private val _selectedSearch = kotlinx.coroutines.flow.MutableStateFlow<SearchResult?>(null)
    val selectedSearch: kotlinx.coroutines.flow.StateFlow<SearchResult?> = _selectedSearch

    private val _settings = kotlinx.coroutines.flow.MutableStateFlow(AppSettings())
    val settings: kotlinx.coroutines.flow.StateFlow<AppSettings> = _settings

    fun addSearch(keyword: String, code: String?, maxPrice: Double?): SearchResult? {
        if (keyword.isBlank()) return null
        val result = SearchRepository.search(keyword, code, maxPrice)
        _searches.value = listOf(result) + _searches.value
        return result
    }

    fun deleteSearch(search: SearchResult) {
        _searches.value = _searches.value.filterNot { it.id == search.id }
        if (_selectedSearch.value?.id == search.id) {
            _selectedSearch.value = null
        }
    }

    fun deleteOffer(searchId: String, offer: OfferResult) {
        _searches.value = _searches.value.map { search ->
            if (search.id == searchId) search.copy(offers = search.offers.filterNot { it.shopName == offer.shopName }) else search
        }
    }

    fun clearAllSearches() {
        _searches.value = emptyList()
        _selectedSearch.value = null
    }

    fun setLanguage(language: Language) {
        _settings.value = _settings.value.copy(language = language)
    }

    fun setTheme(theme: ThemeMode) {
        _settings.value = _settings.value.copy(theme = theme)
    }

    fun setNotificationTime(hour: Int, minute: Int) {
        _settings.value = _settings.value.copy(notificationHour = hour, notificationMinute = minute)
    }

    fun selectSearch(id: String?) {
        _selectedSearch.value = _searches.value.firstOrNull { it.id == id }
    }
}

object SearchRepository {
    fun search(keyword: String, code: String?, maxPrice: Double?): SearchResult {
        val query = (code ?: keyword).trim().ifBlank { keyword.trim() }
        val normalized = query.lowercase(Locale.ROOT)

        val base = when {
            normalized.contains("iphone") -> 799.0
            normalized.contains("tv") || normalized.contains("televisore") -> 649.0
            normalized.contains("laptop") || normalized.contains("notebook") -> 899.0
            normalized.contains("tablet") -> 349.0
            else -> 129.0
        }

        val retailerMap = listOf(
            RetailerOffer("Amazon.it", "https://www.amazon.it/s?k=${query.replace(" ", "+")}", 0),
            RetailerOffer("MediaMarkt", "https://www.mediamarkt.it/it/search.html?query=${query.replace(" ", "+")}", 1),
            RetailerOffer("Euronics", "https://www.euronics.it/search?search=${query.replace(" ", "+")}", 2),
            RetailerOffer("Unieuro", "https://www.unieuro.it/online/search?search=${query.replace(" ", "+")}", 3),
            RetailerOffer("PC Componentes", "https://www.pccomponentes.com/?s=${query.replace(" ", "+")}", 4)
        )

        val offers = retailerMap.map { retailer ->
            val delta = (query.hashCode().absoluteValue % 23) * 3.2 + retailer.index * 14.0
            OfferResult(
                shopName = retailer.name,
                price = base + delta,
                shipping = if (retailer.index % 3 == 0) 6.99 else 0.0,
                url = retailer.url
            )
        }.sortedBy { it.totalPrice }

        return SearchResult(
            keyword = keyword.trim(),
            code = code?.trim()?.ifBlank { null },
            maxPrice = maxPrice,
            offers = offers
        )
    }

    private data class RetailerOffer(
        val name: String,
        val url: String,
        val index: Int
    )
}

data class SearchResult(
    val id: String = java.util.UUID.randomUUID().toString(),
    val keyword: String,
    val code: String? = null,
    val maxPrice: Double? = null,
    val offers: List<OfferResult>
)

data class OfferResult(
    val shopName: String,
    val price: Double,
    val shipping: Double,
    val url: String
) {
    val totalPrice: Double
        get() = price + shipping
}

class PriceMonitorWorker(
    appContext: Context,
    params: androidx.work.WorkerParameters
) : androidx.work.CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val keyword = inputData.getString(KEY_KEYWORD) ?: return Result.failure()
        val code = inputData.getString(KEY_CODE)?.takeIf { it.isNotBlank() }
        val maxPrice = inputData.getDouble(KEY_TARGET_PRICE, Double.MAX_VALUE)

        val search = SearchRepository.search(keyword, code, if (maxPrice == Double.MAX_VALUE) null else maxPrice)
        val best = search.offers.minByOrNull { it.totalPrice } ?: return Result.success()

        if (maxPrice != Double.MAX_VALUE && best.totalPrice <= maxPrice) {
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, "price_alerts")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Prezzo trovato per $keyword")
                .setContentText("${best.shopName}: €${"%.2f".format(best.totalPrice)} incl. spedizione")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            manager?.notify(keyword.hashCode(), notification)
        }

        return Result.success()
    }

    companion object {
        const val KEY_KEYWORD = "keyword"
        const val KEY_CODE = "code"
        const val KEY_TARGET_PRICE = "target_price"
    }
}
