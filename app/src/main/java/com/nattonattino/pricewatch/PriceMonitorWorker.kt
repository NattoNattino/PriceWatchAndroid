package com.nattonattino.pricewatch

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class PriceWatchViewModel : ViewModel() {
    private val _searches = MutableStateFlow<List<SearchResult>>(emptyList())
    val searches: StateFlow<List<SearchResult>> = _searches.asStateFlow()

    private val _selectedSearch = MutableStateFlow<SearchResult?>(null)
    val selectedSearch: StateFlow<SearchResult?> = _selectedSearch.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun addSearch(
        keyword: String,
        code: String?,
        maxPrice: Double?,
        notificationHour: Int,
        notificationMinute: Int
    ): SearchResult? {
        if (keyword.isBlank()) return null
        val search = SearchRepository.search(keyword, code, maxPrice)
        _searches.value = listOf(search) + _searches.value
        _settings.value = _settings.value.copy(
            notificationHour = notificationHour,
            notificationMinute = notificationMinute
        )
        _selectedSearch.value = search
        return search
    }

    fun deleteSearch(id: String) {
        _searches.value = _searches.value.filterNot { it.id == id }
        if (_selectedSearch.value?.id == id) {
            _selectedSearch.value = null
        }
    }

    fun deleteOffer(searchId: String, offer: OfferResult) {
        _searches.value = _searches.value.map { search ->
            if (search.id == searchId) {
                search.copy(offers = search.offers.filterNot { it.shopName == offer.shopName })
            } else {
                search
            }
        }

        val selected = _selectedSearch.value
        if (selected != null && selected.id == searchId) {
            _selectedSearch.value = selected.copy(
                offers = selected.offers.filterNot { it.shopName == offer.shopName }
            )
        }
    }

    fun clearAllSearches() {
        _searches.value = emptyList()
        _selectedSearch.value = null
    }

    fun selectSearch(id: String?) {
        _selectedSearch.value = _searches.value.firstOrNull { it.id == id }
    }

    fun setLanguage(language: Language) {
        _settings.value = _settings.value.copy(language = language)
    }

    fun setTheme(theme: ThemeMode) {
        _settings.value = _settings.value.copy(theme = theme)
    }

    fun setNotificationTime(hour: Int, minute: Int) {
        _settings.value = _settings.value.copy(
            notificationHour = hour,
            notificationMinute = minute
        )
    }
}

object SearchRepository {
    fun search(keyword: String, code: String?, maxPrice: Double?): SearchResult {
        val queryText = if (!code.isNullOrBlank()) "$keyword $code" else keyword
        val normalized = queryText.lowercase(Locale.ROOT)

        val basePrice = when {
            normalized.contains("iphone") -> 799.0
            normalized.contains("tv") || normalized.contains("televisore") -> 649.0
            normalized.contains("laptop") || normalized.contains("notebook") -> 899.0
            normalized.contains("tablet") -> 349.0
            else -> 129.0
        }

        val retailerUrls = listOf(
            Retailer("Amazon.it", "https://www.amazon.it/s?k="),
            Retailer("MediaMarkt", "https://www.mediamarkt.it/it/search.html?query="),
            Retailer("Euronics", "https://www.euronics.it/search?search="),
            Retailer("Unieuro", "https://www.unieuro.it/online/search?search="),
            Retailer("PC Componentes", "https://www.pccomponentes.com/?s="),
            Retailer("Boulanger", "https://www.boulanger.com/resultats?search="),
            Retailer("Shopmania", "https://www.shopmania.it/find?keyword=")
        )

        val offers = retailerUrls.mapIndexed { index, retailer ->
            val variance = (normalized.hashCode().absoluteValue % 31) * 2.5 + index * 18.0
            val shipping = if (index % 3 == 0) 6.99 else 0.0
            val url = retailer.url + queryText.replace(" ", "+")
            OfferResult(
                shopName = retailer.name,
                price = basePrice + variance,
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

    private data class Retailer(
        val name: String,
        val url: String
    )
}
