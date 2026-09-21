package com.dominiqueherbrigpersonalteam.lademonitor.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Central Room stack — the Kotlin port of the SwiftData `LocalStore`. One database for the whole
 * app, holding the offline buffer that both modes read from.
 */
@Database(
    entities = [
        LocalVehicle::class,
        LocalProvider::class,
        LocalChargingLocation::class,
        LocalChargingSession::class
    ],
    version = 2,
    exportSchema = false
)
abstract class LademonitorDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun providerDao(): ProviderDao
    abstract fun locationDao(): LocationDao
    abstract fun sessionDao(): SessionDao
}

/**
 * 1 -> 2: `sessions.outsideTempC` (Aussentemperatur beim Ladebeginn).
 *
 * Room verlangt fuer jede Schema-Aenderung eine Migration - ohne sie wirft es beim ersten Start
 * nach dem Update `IllegalStateException: A migration from 1 to 2 was required but not found`,
 * die App startet also gar nicht mehr. `fallbackToDestructiveMigration()` waere die bequeme
 * Alternative und hier genau die falsche: im Local-Only-Modus ist diese Datenbank die EINZIGE
 * Kopie der Daten, ein Schema-Update wuerde sie kommentarlos loeschen.
 *
 * Der Spaltenname folgt der Kotlin-Eigenschaft (Room leitet ihn ohne `@ColumnInfo` genau so ab),
 * und eine neue nullable Spalte braucht keinen Default - Bestandszeilen bekommen NULL, was hier
 * genau richtig ist: fuer sie gibt es keine Temperatur und eine geratene waere schlimmer als
 * keine.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sessions ADD COLUMN outsideTempC REAL")
    }
}

object LocalStore {
    lateinit var db: LademonitorDatabase
        private set

    val vehicles: VehicleDao get() = db.vehicleDao()
    val providers: ProviderDao get() = db.providerDao()
    val locations: LocationDao get() = db.locationDao()
    val sessions: SessionDao get() = db.sessionDao()

    fun init(context: Context) {
        db = Room.databaseBuilder(
            context.applicationContext,
            LademonitorDatabase::class.java,
            "lademonitor.db"
        ).addMigrations(MIGRATION_1_2).build()
    }
}
