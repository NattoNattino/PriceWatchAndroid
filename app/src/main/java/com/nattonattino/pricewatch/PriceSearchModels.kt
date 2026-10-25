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
import androidx.compose.material.icons.filled.Search
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
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureNotificationChannel(this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PriceWatchScreen()
                }
            }
        }
    }
}

@Composable
fun PriceWatchScreen(viewModel: PriceWatchViewModel = viewModel()) {
    val context = LocalContext.current
    val searches by viewModel.searches.collectAsState()

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeaderSection()
            SearchForm(
                onSearch = { keyword, code, maxPrice ->
                    val search = viewModel.addSearch(keyword, code, maxPrice)
                    if (search != null) {
                        schedulePeriodicCheck(context, search)
                    }
                }
            )

            Text(
                text = "Ricerche salvate",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (searches.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Nessuna ricerca ancora.")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(searches, key = { it.id }) { search ->
                        SwipeToDeleteRow(
                            onDelete = { viewModel.deleteSearch(search.id) },
                            content = { SearchResultCard(search) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderSection() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "PriceWatch",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Confronta offerte europee e ricevi alert in background",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SearchForm(
    onSearch: (String, String?, Double?) -> Unit
) {
    var keyword by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var threshold by rememberSaveable { mutableStateOf("") }
    var useCode by rememberSaveable { mutableStateOf(false) }
    var usePrice by rememberSaveable { mutableStateOf(false) }

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
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
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
                    label = { Text("Codice / SKU / EAN") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
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
                    label = { Text("Prezzo massimo desiderato (€)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Button(
                onClick = {
                    val parsedPrice = threshold.toDoubleOrNull()
                    onSearch(
                        keyword.trim(),
                        if (useCode) code.trim().ifBlank { null } else null,
                        if (usePrice) parsedPrice else null
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
private fun SearchResultCard(search: SearchResult) {
    val uriHandler = LocalUriHandler.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(search.keyword, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (!search.code.isNullOrBlank()) {
                    Text("Codice: ${search.code}")
                }
                if (search.maxPrice != null) {
                    Text("Prezzo target: €${"%.2f".format(search.maxPrice)}")
                }
            }

            search.offers.forEach { offer ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(offer.shopName, fontWeight = FontWeight.Bold)
                            Text("Totale: €${"%.2f".format(offer.totalPrice)}")
                            Text("Spedizione: €${"%.2f".format(offer.shipping)}")
                        }
                        TextButton(onClick = { uriHandler.openUri(offer.url) }) {
                            Text("Apri sito")
                        }
                    }
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
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFB3261E))
                    .padding(16.dp),
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
}

private fun schedulePeriodicCheck(context: Context, search: SearchResult) {
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
