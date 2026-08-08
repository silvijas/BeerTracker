package com.beertracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [BeerEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class BeerDatabase : RoomDatabase() {
    abstract fun beerDao(): BeerDao

    companion object {
        fun build(context: Context): BeerDatabase =
            Room.databaseBuilder(context, BeerDatabase::class.java, "beertracker.db").build()
    }
}
