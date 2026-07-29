package com.beertracker

import com.beertracker.domain.TriedBeer

fun beer(
    id: String = "id",
    name: String = "Beer $id",
    brewery: String = "Brewery",
    type: String = "Lager",
    alcoholPercent: Double? = 5.0,
    volumeMl: Int? = 330,
    price: Double? = 25.0,
    grade: Int = 7,
    note: String = "",
    aftertaste: String = "",
    goesWellWith: List<String> = emptyList(),
    buyAgain: Boolean = false,
    favourite: Boolean = false,
    dateAdded: Long = 0L,
) = TriedBeer(
    id = id,
    name = name,
    brewery = brewery,
    type = type,
    alcoholPercent = alcoholPercent,
    volumeMl = volumeMl,
    price = price,
    grade = grade,
    note = note,
    aftertaste = aftertaste,
    goesWellWith = goesWellWith,
    buyAgain = buyAgain,
    favourite = favourite,
    dateAdded = dateAdded,
    catalogArticleNumber = null,
    addedBy = null,
)
