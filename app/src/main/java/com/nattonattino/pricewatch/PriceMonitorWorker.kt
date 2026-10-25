package com.nattonattino.pricewatch

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PriceWatchViewModel : ViewModel() {
    private val _searches = MutableStateFlow<List<SearchResult>>(emptyList())
    val searches: StateFlow<List<SearchResult>> = _searches.asStateFlow()

    fun addSearch(keyword: String, code: String?, maxPrice: Double?): SearchResult? {
        if (keyword.isBlank()) return null

        val result = PriceOfferRepository.search(keyword, code, maxPrice)
        _searches.value = listOf(result) + _searches.value
        return result
    }

    fun deleteSearch(id: String) {
        _searches.value = _searches.value.filterNot { it.id == id }
    }
}

object PriceOfferRepository {
    fun search(keyword: String, code: String?, maxPrice: Double?): SearchResult {
        val query = if (code.isNullOrBlank()) keyword.trim() else code.trim()
        val basePrice = when {
            query.lowercase().contains("iphone") -> 799.0
            query.lowercase().contains("tv") || query.lowercase().contains("televisore") -> 649.0
            query.lowercase().contains("laptop") || query.lowercase().contains("notebook") -> 899.0
            query.lowercase().contains("tablet") -> 349.0
            else -> 129.0
        }

        val shops = listOf(
            "Amazon.it",
            "MediaMarkt",
            "Euronics",
            "Unieuro",
            "PC Componentes",
            "Raiway Shop"
        )

        val offers = shops.mapIndexed { index, shop ->
            val adjusted = basePrice + (index * 23.0) + (query.hashCode().absoluteValue % 21) * 2.5
            val shipping = if (index % 3 == 0) 6.99 else 0.0
            val url = "https://www.google.com/search?q=${query.replace(" ", "+")}&tbm=shop"
            OfferResult(
                shopName = shop,
                price = adjusted,
                shipping = shipping,
                url = url
            )
        }.sortedBy { it.totalPrice }

        return SearchResult(
            keyword = keyword.trim(),
            code = code?.trim()?.ifBlank { null },
            maxPrice = maxPrice,
            offers = offers
        )
    }
}
