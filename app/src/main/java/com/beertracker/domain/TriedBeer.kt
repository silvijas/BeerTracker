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
    /** A local file URI for a photo the user took or picked themselves. */
    val photoUri: String? = null,
) {
    init {
        require(grade == null || grade in 1..5) { "Grade must be between 1 and 5, was $grade" }
        require(grade == null || tried) { "A graded beer must be tried, grade was $grade" }
    }

    /**
     * The picture to show for this beer: the user's own photo if they
     * attached one, otherwise the catalog product image. Two separate fields
     * on purpose, so removing a photo falls back to the catalog picture
     * instead of leaving the beer blank.
     */
    val displayImageUrl: String?
        get() = photoUri ?: imageUrl
}
