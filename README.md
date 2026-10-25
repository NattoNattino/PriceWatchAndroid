package com.nattonattino.pricewatch

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PriceWatchViewModel : ViewModel() {
    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products.asStateFlow()

    fun addProduct(name: String, code: String, targetPrice: Double) {
        if (name.isBlank() || code.isBlank()) return
        val product = Product(
            name = name.trim(),
            code = code.trim(),
            targetPrice = targetPrice,
            currentBestPrice = PriceComparisonService.lookupBestOffer(
                Product(name = name, code = code, targetPrice = targetPrice)
            ).totalPrice,
            shippingCost = PriceComparisonService.lookupBestOffer(
                Product(name = name, code = code, targetPrice = targetPrice)
            ).shipping,
            lastChecked = "Ora"
        )

        _products.value = _products.value + product
    }

    fun refreshProduct(product: Product) {
        val offer = PriceComparisonService.lookupBestOffer(product)
        val updated = product.copy(
            currentBestPrice = offer.totalPrice,
            shippingCost = offer.shipping,
            lastChecked = "Adesso"
        )
        _products.value = _products.value.map { if (it.id == product.id) updated else it }
    }

    companion object {
        val demoProducts = listOf(
            Product(
                name = "iPhone 15",
                code = "iphone15",
                targetPrice = 799.0,
                currentBestPrice = 799.0,
                shippingCost = 0.0,
                lastChecked = "1 min fa"
            ),
            Product(
                name = "TV 55" ,
                code = "tv55",
                targetPrice = 649.0,
                currentBestPrice = 649.0,
                shippingCost = 0.0,
                lastChecked = "5 min fa"
            )
        )
    }
}
