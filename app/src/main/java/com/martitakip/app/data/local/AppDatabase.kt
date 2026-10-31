package com.martitakip.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

/** Persists [VehicleType] by name so the raw database stays human readable. */
class Converters {

    @TypeConverter
    fun vehicleTypeToString(type: VehicleType): String = type.name

    @TypeConverter
    fun stringToVehicleType(raw: String): VehicleType = VehicleType.fromRaw(raw)
}

@Database(
    entities = [SwapEvent::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun swapDao(): SwapDao

    companion object {
        private const val DATABASE_NAME = "marti_takip.db"

        @Volatile
        private var instance: AppDatabase? = null

        /** Process-wide singleton; Room itself is thread safe once built. */
        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                // Write-ahead logging keeps single-row inserts instant while a
                // map query is reading the whole table.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Room enables foreign keys only when requested; we do
                        // not use them yet but keep the pragma consistent.
                        db.execSQL("PRAGMA foreign_keys = ON")
                    }
                })
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
    }
}
