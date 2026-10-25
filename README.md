package com.nattonattino.pricewatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.UUID
import kotlin.math.max

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
    val searches by viewModel.searches.collectAsState()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeaderSection()
            SearchForm(viewModel = viewModel)
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
            text = "Confronta offerte europee e ricevi alert su soglia",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SearchForm(viewModel: PriceWatchViewModel) {
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
                    viewModel.addSearch(
                        keyword = keyword.trim(),
                        code = if (useCode) code.trim() else null,
                        maxPrice = if (usePrice) parsedPrice else null
                    )
                    keyword = ""
                    code = ""
                    threshold = ""
                    useCode = false
                    usePrice = false
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = keyword.isNotBlank()
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Text("Cerca offerte")
            }
        }
    }
}

@Composable
private fun SearchResultCard(search: PriceSearch) {
    val uriHandler = LocalUriHandler.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(search.keyword, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (search.code != null) {
                        Text("Codice: ${search.code}")
                    }
                    if (search.maxPrice != null) {
                        Text("Prezzo target: €${"%.2f".format(search.maxPrice)}")
                    }
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
                            Text("Prezzo: €${"%.2f".format(offer.price + offer.shipping)}")
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
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
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

data class PriceSearch(
    val id: String = UUID.randomUUID().toString(),
    val keyword: String,
    val code: String? = null,
    val maxPrice: Double? = null,
    val offers: List<ProductOffer>
)

data class ProductOffer(
    val shopName: String,
    val price: Double,
    val shipping: Double,
    val url: String
)

class PriceWatchViewModel : androidx.lifecycle.ViewModel() {
    private val _searches = kotlinx.coroutines.flow.MutableStateFlow<List<PriceSearch>>(emptyList())
    val searches: kotlinx.coroutines.flow.StateFlow<List<PriceSearch>> = _searches

    fun addSearch(keyword: String, code: String?, maxPrice: Double?) {
        if (keyword.isBlank()) return

        val offerList = generateOffers(keyword, code, maxPrice)
        val newSearch = PriceSearch(
            keyword = keyword,
            code = code,
            maxPrice = maxPrice,
            offers = offerList
        )
        _searches.value = listOf(newSearch) + _searches.value
    }

    fun deleteSearch(id: String) {
        _searches.value = _searches.value.filterNot { it.id == id }
    }

    private fun generateOffers(
        keyword: String,
        code: String?,
        maxPrice: Double?
    ): List<ProductOffer> {
        val normalized = keyword.lowercase()
        val seed = if (!code.isNullOrBlank()) code.hashCode() else normalized.hashCode()
        val stores = listOf(
            "Amazon",
            "MediaMarkt",
            "Euronics",
            "Unieuro",
            "PC Componentes",
            "Digitalocean Euro Shop"
        )

        val basePrice = when {
            normalized.contains("iphone") -> 799.0
            normalized.contains("tv") || normalized.contains("televisore") -> 649.0
            normalized.contains("laptop") || normalized.contains("notebook") -> 899.0
            else -> 129.0
        }

        return stores.mapIndexed { index, shop ->
            val priceVariance = (seed % 13) * 7.5 + index * 11.0
            val price = max(1.0, basePrice + priceVariance / 10.0)
            val shipping = if (index % 3 == 0) 6.99 else 0.0
            val url = when {
                !code.isNullOrBlank() -> "https://www.google.com/search?q=${code.replace(" ", "+")}&tbm=shop"
                else -> "https://www.google.com/search?q=${keyword.replace(" ", "+")}&tbm=shop"
            }

            ProductOffer(
                shopName = shop,
                price = price,
                shipping = shipping,
                url = url
            )
        }.sortedBy { it.price + it.shipping }
            .take(5)
    }
}

private fun ensureNotificationChannel(context: android.content.Context) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        val channel = android.app.NotificationChannel(
            "price_alerts",
            "Price Alerts",
            android.app.NotificationManager.IMPORTANCE_HIGH
        )
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }
}
