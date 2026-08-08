package com.beertracker.domain

data class TriedBeer(
    val id: String,
    val name: String,
    val brewery: String,
    val type: String,
    val alcoholPercent: Double?,
    val volumeMl: Int?,
    val price: Double?,
    val grade: Int?,
    val tried: Boolean,
    val note: String,
    val aftertaste: String,
    val goesWellWith: List<String>,
    val buyAgain: Boolean,
    val favourite: Boolean,
    val dateAdded: Long,
    val catalogArticleNumber: String?,
    val addedBy: String?,
    val imageUrl: String?,
) {
    init {
        require(grade == null || grade in 5..10) { "Grade must be between 5 and 10, was $grade" }
        require(grade == null || tried) { "A graded beer must be tried, grade was $grade" }
    }
}
