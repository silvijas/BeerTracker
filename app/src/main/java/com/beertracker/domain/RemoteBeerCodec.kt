package com.beertracker.domain

/**
 * The shape of a beer inside the shared cellar: every TriedBeer field except
 * the id (which is the document id) and photoUri (a file on one phone).
 * Absent values are written as explicit nulls, so a whole-record write
 * clears a value the other phone had set.
 */
object RemoteBeerCodec {

    fun toFields(beer: TriedBeer): Map<String, Any?> = mapOf(
        "name" to beer.name,
        "brewery" to beer.brewery,
        "type" to beer.type,
        "alcoholPercent" to beer.alcoholPercent,
        "volumeMl" to beer.volumeMl,
        "price" to beer.price,
        "grade" to beer.grade,
        "tried" to beer.tried,
        "note" to beer.note,
        "aftertaste" to beer.aftertaste,
        "goesWellWith" to beer.goesWellWith,
        "buyAgain" to beer.buyAgain,
        "favourite" to beer.favourite,
        "dateAdded" to beer.dateAdded,
        "catalogArticleNumber" to beer.catalogArticleNumber,
        "addedBy" to beer.addedBy,
        "imageUrl" to beer.imageUrl,
    )

    /**
     * Null when the record cannot become a legal TriedBeer: no usable name,
     * a grade outside 1 to 5, or a grade on a beer that is not tried. Numbers
     * are accepted as any Number because Firestore returns Long or Double.
     */
    fun fromFields(id: String, fields: Map<String, Any?>): TriedBeer? {
        val name = fields.text("name")?.takeIf { it.isNotBlank() } ?: return null
        return try {
            TriedBeer(
                id = id,
                name = name,
                brewery = fields.text("brewery") ?: "",
                type = fields.text("type") ?: "",
                alcoholPercent = fields.decimal("alcoholPercent"),
                volumeMl = fields.whole("volumeMl")?.toInt(),
                price = fields.decimal("price"),
                grade = fields.whole("grade")?.toInt(),
                tried = fields.flag("tried"),
                note = fields.text("note") ?: "",
                aftertaste = fields.text("aftertaste") ?: "",
                goesWellWith = fields.strings("goesWellWith"),
                buyAgain = fields.flag("buyAgain"),
                favourite = fields.flag("favourite"),
                dateAdded = fields.whole("dateAdded") ?: 0L,
                catalogArticleNumber = fields.text("catalogArticleNumber"),
                addedBy = fields.text("addedBy"),
                imageUrl = fields.text("imageUrl"),
                photoUri = null,
            )
        } catch (error: IllegalArgumentException) {
            null
        }
    }

    private fun Map<String, Any?>.text(key: String): String? = this[key] as? String

    private fun Map<String, Any?>.whole(key: String): Long? = (this[key] as? Number)?.toLong()

    private fun Map<String, Any?>.decimal(key: String): Double? = (this[key] as? Number)?.toDouble()

    private fun Map<String, Any?>.flag(key: String): Boolean = this[key] as? Boolean ?: false

    private fun Map<String, Any?>.strings(key: String): List<String> =
        (this[key] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
}
