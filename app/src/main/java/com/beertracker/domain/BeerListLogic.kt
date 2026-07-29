package com.beertracker.domain

enum class BeerSort { GRADE, PRICE, NAME_BREWERY, DATE_ADDED }

data class BeerFilter(
    val query: String = "",
    val buyAgainOnly: Boolean = false,
    val favouritesOnly: Boolean = false,
    val notTriedOnly: Boolean = false,
    val types: Set<String> = emptySet(),
)

fun filterAndSort(beers: List<TriedBeer>, filter: BeerFilter, sort: BeerSort): List<TriedBeer> {
    val query = filter.query.trim()
    val filtered = beers.filter { beer ->
        val matchesQuery = query.isEmpty() ||
            listOf(beer.name, beer.brewery, beer.type).any { it.contains(query, ignoreCase = true) }
        matchesQuery &&
            (!filter.buyAgainOnly || beer.buyAgain) &&
            (!filter.favouritesOnly || beer.favourite) &&
            (!filter.notTriedOnly || !beer.tried) &&
            (filter.types.isEmpty() || beer.type in filter.types)
    }
    return when (sort) {
        // Descending grade with ungraded beers last: nullsFirst inverted by compareByDescending.
        BeerSort.GRADE -> filtered.sortedWith(
            compareByDescending<TriedBeer, Int?>(nullsFirst(naturalOrder<Int>())) { it.grade }
                .thenByDescending { it.dateAdded })
        BeerSort.PRICE -> filtered.sortedWith(
            compareBy(nullsLast(naturalOrder<Double>())) { it.price })
        BeerSort.NAME_BREWERY -> filtered.sortedWith(
            compareBy({ it.name.lowercase() }, { it.brewery.lowercase() }))
        BeerSort.DATE_ADDED -> filtered.sortedByDescending { it.dateAdded }
    }
}
