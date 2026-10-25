package com.nattonattino.pricewatch

import java.util.UUID

data class Product(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val code: String,
    val targetPrice: Double,
    val currentBestPrice: Double = 0.0,
    val shippingCost: Double = 0.0,
    val lastChecked: String = "Mai"
)
