package com.nattonattino.pricewatch

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlin.math.abs

class PriceMonitorWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val keyword = inputData.getString(KEY_KEYWORD) ?: return Result.failure()
        val code = inputData.getString(KEY_CODE)?.takeIf { it.isNotBlank() }
        val target = inputData.getDouble(KEY_TARGET_PRICE, Double.MAX_VALUE)

        val search = SearchRepository.search(keyword, code, if (target == Double.MAX_VALUE) null else target)
        val bestOffer = search.bestOffer ?: return Result.success()

        if (target != Double.MAX_VALUE && bestOffer.totalPrice <= target) {
            notifyPriceFound(search.keyword, bestOffer)
        }

        Log.d("PriceMonitorWorker", "Check completed: ${search.keyword} -> ${bestOffer.totalPrice}")
        return Result.success()
    }

    private fun notifyPriceFound(keyword: String, offer: OfferResult) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(applicationContext, "price_alerts")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Prezzo trovato per $keyword")
            .setContentText("${offer.shopName}: €${"%.2f".format(offer.totalPrice)} incl. spedizione")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager?.notify(abs(keyword.hashCode()), notification)
    }

    companion object {
        const val KEY_KEYWORD = "keyword"
        const val KEY_CODE = "code"
        const val KEY_TARGET_PRICE = "target_price"
    }
}
