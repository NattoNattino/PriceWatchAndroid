package com.nattonattino.pricewatch

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

@Composable
fun PriceWatchApp(viewModel: PriceWatchViewModel = viewModel()) {
    val context = LocalContext.current
    val products by viewModel.products.collectAsState()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    Scaffold { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TitleRow()

                AddProductForm(
                    onAddProduct = { name, code, targetPrice, frequency ->
                        val threshold = targetPrice.toDoubleOrNull() ?: 0.0
                        val intervalHours = frequency.toIntOrNull() ?: 6
                        viewModel.addProduct(name, code, threshold)
                        scheduleBackgroundCheck(context, intervalHours)

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )

                Divider()

                Text(
                    text = "Prodotti monitorati",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (products.isEmpty()) {
                    Text("Nessun prodotto inserito. Aggiungine uno per iniziare.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(products) { product ->
                            ProductCard(product = product) {
                                viewModel.refreshProduct(product)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TitleRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "PriceWatch",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Confronta offerte e ricevi alert in background",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun AddProductForm(
    onAddProduct: (String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var targetPrice by remember { mutableStateOf("149.99") }
    var frequency by remember { mutableStateOf("6") }

    Card(shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Aggiungi prodotto",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome prodotto") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("SKU / codice / EAN") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = targetPrice,
                    onValueChange = { targetPrice = it },
                    label = { Text("Soglia €") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = frequency,
                    onValueChange = { frequency = it },
                    label = { Text("Orologio (h)") },
                    modifier = Modifier.weight(1f)
                )
            }
            Button(
                onClick = { onAddProduct(name, code, targetPrice, frequency) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Salva monitoraggio")
            }
        }
    }
}

@Composable
private fun ProductCard(
    product: Product,
    onRefresh: () -> Unit
) {
    Card(shape = RoundedCornerShape(16.dp)) {
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
                Column {
                    Text(product.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(product.code, style = MaterialTheme.typography.bodyMedium)
                }
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Text("Prezzo target: €${product.targetPrice}")
            Text("Offerta migliore attuale: €${product.currentBestPrice}")
            Text("Spedizione: €${product.shippingCost}")
            Text("Ultimo controllo: ${product.lastChecked}")

            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text("Aggiorna offerte")
            }
        }
    }
}

private fun scheduleBackgroundCheck(context: Context, frequencyHours: Int) {
    val request = PeriodicWorkRequestBuilder<PriceMonitorWorker>(frequencyHours.toLong(), TimeUnit.HOURS)
        .setInitialDelay(15, TimeUnit.MINUTES)
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "price_monitor",
        androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
        request
    )
    Log.d("PriceWatch", "Background checks scheduled every $frequencyHours hours")
}

fun ensureNotificationChannel(context: Context) {
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
