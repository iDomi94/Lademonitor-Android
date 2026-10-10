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
        LocalProviderFee::class,
        LocalVehicleCost::class
    ],
    version = 7,
    exportSchema = false
)
abstract class LademonitorDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun providerDao(): ProviderDao
    abstract fun locationDao(): LocationDao
    abstract fun sessionDao(): SessionDao
    abstract fun providerFeeDao(): ProviderFeeDao
    abstract fun vehicleCostDao(): VehicleCostDao
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

/**
 * 4 -> 5: Messort der kWh (Server ab 0.29.0) - `providers.energyMeter` als Standard des
 * Anbieters, `sessions.energyMeter` als Uebersteuerung am Vorgang.
 *
 * Beide nullable ohne Default: beim Anbieter gilt `null` als Ladesaeule (wie serverseitig fuer
 * Bestandszeilen), beim Vorgang heisst `null` "folgt dem Anbieter". Ohne DEFAULT gibt es nichts,
 * was exakt zu Rooms Schemapruefung passen muesste.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE providers ADD COLUMN energyMeter TEXT")
        db.execSQL("ALTER TABLE sessions ADD COLUMN energyMeter TEXT")
    }
}

/**
 * 5 -> 6: Ladeort-Art des Anbieters (Server ab 0.31.0) - `providers.chargingPlace`
 * ("home" | "work" | "public").
 *
 * Nullable ohne Default, genau wie `energyMeter` in 4 -> 5: Bestandszeilen bekommen NULL, das
 * gilt als "unterwegs" (wie serverseitig der Standard), und ohne DEFAULT gibt es nichts, was
 * exakt zu Rooms Schemapruefung passen muesste.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE providers ADD COLUMN chargingPlace TEXT")
    }
}

/**
 * 6 -> 7: Fahrzeugkosten (Server ab 0.32.0) - die Kauf-/Leasingfelder am Fahrzeug und die neue
 * Tabelle `vehicle_costs`.
 *
 * Die Fahrzeugspalten sind alle nullable ohne Default (wie in 4 -> 5 und 5 -> 6): Bestandszeilen
 * bekommen NULL, das heisst "keine Angabe". Typen wie Room sie aus den Kotlin-Typen ableitet:
 * String -> TEXT, Double -> REAL, Long/Int -> INTEGER. Die neue Tabelle muss exakt
 * [LocalVehicleCost] entsprechen, sonst bricht Rooms Schemapruefung ab (siehe 3 -> 4).
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE vehicles ADD COLUMN ownership TEXT")
        db.execSQL("ALTER TABLE vehicles ADD COLUMN purchasePrice REAL")
        db.execSQL("ALTER TABLE vehicles ADD COLUMN purchaseDate INTEGER")
        db.execSQL("ALTER TABLE vehicles ADD COLUMN firstRegistration INTEGER")
        db.execSQL("ALTER TABLE vehicles ADD COLUMN purchaseOdometerKm INTEGER")
        db.execSQL("ALTER TABLE vehicles ADD COLUMN depreciationMonthlyOverride REAL")
        db.execSQL("ALTER TABLE vehicles ADD COLUMN leasingRateMonthly REAL")
        db.execSQL("ALTER TABLE vehicles ADD COLUMN leasingStart INTEGER")
        db.execSQL("ALTER TABLE vehicles ADD COLUMN leasingEnd INTEGER")
        db.execSQL("ALTER TABLE vehicles ADD COLUMN leasingDownPayment REAL")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `vehicle_costs` (
                `localId` TEXT NOT NULL,
                `serverId` TEXT,
                `vehicleId` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `category` TEXT NOT NULL,
                `interval` TEXT NOT NULL,
                `startDate` INTEGER NOT NULL,
                `endDate` INTEGER,
                `odometerKm` INTEGER,
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
    val vehicleCosts: VehicleCostDao get() = db.vehicleCostDao()

    fun init(context: Context) {
        db = Room.databaseBuilder(
            context.applicationContext,
            LademonitorDatabase::class.java,
            "lademonitor.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build()
    }
}
