package com.nattonattino.pricewatch

import kotlin.math.min

object PriceComparisonService {
    private val fakeStores = listOf(
        "Amazon",
        "MediaWorld",
        "Euronics",
        "Shopitalia",
        " Unieuro",
        "PCComponentes"
    )

    fun lookupBestOffer(product: Product): Offer {
        val marketPrices = fakeStores.mapIndexed { index, store ->
            val basePrice = when {
                product.code.contains("iphone", ignoreCase = true) -> 799.0 + index * 11.5
                product.code.contains("tv", ignoreCase = true) -> 649.0 + index * 15.0
                else -> 129.0 + index * 9.0
            }
            val shipping = if (index % 3 == 0) 5.99 else 0.0
            Offer(store = store, price = basePrice, shipping = shipping, url = "https://example.com/$store/${product.code}")
        }

        return marketPrices.minByOrNull { it.totalPrice } ?: Offer("Nessun store", 0.0, 0.0, "")
    }
}

data class Offer(
    val store: String,
    val price: Double,
    val shipping: Double,
    val url: String
) {
    val totalPrice: Double
        get() = price + shipping
}
