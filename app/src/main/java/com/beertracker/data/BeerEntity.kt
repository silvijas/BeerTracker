package com.beertracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.beertracker.domain.TriedBeer

@Entity(tableName = "tried_beers")
data class BeerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val brewery: String,
    val type: String,
    val alcoholPercent: Double?,
    val volumeMl: Int?,
    val price: Double?,
    val grade: Int,
    val note: String,
    val aftertaste: String,
    val goesWellWith: List<String>,
    val buyAgain: Boolean,
    val favourite: Boolean,
    val dateAdded: Long,
    val catalogArticleNumber: String?,
    val addedBy: String?,
)

class Converters {
    @TypeConverter
    fun listToString(value: List<String>): String = value.joinToString("")

    @TypeConverter
    fun stringToList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split("")
}

fun BeerEntity.toDomain() = TriedBeer(
    id, name, brewery, type, alcoholPercent, volumeMl, price, grade,
    note, aftertaste, goesWellWith, buyAgain, favourite, dateAdded,
    catalogArticleNumber, addedBy,
)

fun TriedBeer.toEntity() = BeerEntity(
    id, name, brewery, type, alcoholPercent, volumeMl, price, grade,
    note, aftertaste, goesWellWith, buyAgain, favourite, dateAdded,
    catalogArticleNumber, addedBy,
)
