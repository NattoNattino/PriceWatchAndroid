package com.nattonattino.pricewatch

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class PriceMonitorWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val targetProducts = PriceWatchViewModel.demoProducts
        if (targetProducts.isEmpty()) {
            return Result.success()
        }

        for (product in targetProducts) {
            val bestOffer = PriceComparisonService.lookupBestOffer(product)
            val total = bestOffer.totalPrice
            if (total <= product.targetPrice) {
                sendPriceAlert(product, bestOffer)
            }
        }

        Log.d("PriceMonitorWorker", "Background check completed")
        return Result.success()
    }

    private fun sendPriceAlert(product: Product, offer: Offer) {
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        val builder = NotificationCompat.Builder(applicationContext, "price_alerts")
            .setContentTitle("Prezzo raggiunto per ${product.name}")
            .setContentText("${offer.store}: €${offer.totalPrice} incl. spedizione")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager?.notify(product.id.hashCode(), builder.build())
    }
}
