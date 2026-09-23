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
        LocalChargingSession::class,
        LocalProviderFee::class
    ],
    version = 4,
    exportSchema = false
)
abstract class LademonitorDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun providerDao(): ProviderDao
    abstract fun locationDao(): LocationDao
    abstract fun sessionDao(): SessionDao
    abstract fun providerFeeDao(): ProviderFeeDao
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

/**
 * 2 -> 3: `sessions.outsideTempSource` (Herkunft der Temperatur, Server ab 0.24.0).
 *
 * Dieselben Ueberlegungen wie bei 1 -> 2: keine destruktive Migration, und Bestandszeilen
 * bekommen NULL. Sie stammen alle aus dem Fahrzeug oder von Hand, nur eben nicht mehr
 * unterscheidbar - eine geratene Angabe waere schlimmer als gar keine.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sessions ADD COLUMN outsideTempSource TEXT")
    }
}

/**
 * 3 -> 4: neue Tabelle `provider_fees` (Grundgebuehren/Abos, Server ab 0.27.0).
 *
 * Das SQL muss exakt dem entsprechen, was Room aus [LocalProviderFee] erzeugen wuerde - Room
 * vergleicht beim Oeffnen das Schema und bricht bei jeder Abweichung ab (Spaltentyp, NOT NULL,
 * Primaerschluessel). Kotlin-`String`/`Double`/`Long`/`Boolean` ohne `?` sind NOT NULL, Boolean
 * liegt als INTEGER.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `provider_fees` (
                `localId` TEXT NOT NULL,
                `serverId` TEXT,
                `providerId` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `interval` TEXT NOT NULL,
                `startDate` INTEGER NOT NULL,
                `endDate` INTEGER,
                `label` TEXT,
                `notes` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `isDirty` INTEGER NOT NULL,
                `pendingDelete` INTEGER NOT NULL,
                PRIMARY KEY(`localId`)
            )
            """.trimIndent()
        )
    }
}

object LocalStore {
    lateinit var db: LademonitorDatabase
        private set

    val vehicles: VehicleDao get() = db.vehicleDao()
    val providers: ProviderDao get() = db.providerDao()
    val locations: LocationDao get() = db.locationDao()
    val sessions: SessionDao get() = db.sessionDao()
    val fees: ProviderFeeDao get() = db.providerFeeDao()

    fun init(context: Context) {
        db = Room.databaseBuilder(
            context.applicationContext,
            LademonitorDatabase::class.java,
            "lademonitor.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
    }
}
