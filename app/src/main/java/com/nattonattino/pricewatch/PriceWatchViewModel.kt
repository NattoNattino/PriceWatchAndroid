package com.nattonattino.pricewatch

import java.util.UUID

data class SearchResult(
    val id: String = UUID.randomUUID().toString(),
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
