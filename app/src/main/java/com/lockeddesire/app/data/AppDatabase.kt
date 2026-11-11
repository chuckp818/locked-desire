package com.lockeddesire.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lockeddesire.app.security.Crypto
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        UserProfile::class, UserPreferences::class, ConsentSettings::class, AiPersona::class,
        AiConversation::class, AiMessage::class, AiMemory::class, Task::class, TaskCategory::class,
        TaskCompletion::class, DungeonSession::class, DungeonPhase::class, DungeonChallenge::class,
        ChastitySession::class, FetishCategory::class, UserFetishPreference::class, PetProfile::class,
        PetSession::class, Toy::class, ToySession::class, PersonalCounter::class, CounterEntry::class,
        FitnessActivity::class, Achievement::class, UserAchievement::class, PartnerProfile::class,
        NotificationSettings::class, SessionEntry::class,
        HardcoreTask::class, HardcoreRun::class, HardcoreSettings::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun aiDao(): AiDao
    abstract fun taskDao(): TaskDao
    abstract fun dungeonDao(): DungeonDao
    abstract fun chastityDao(): ChastityDao
    abstract fun fetishDao(): FetishDao
    abstract fun petDao(): PetDao
    abstract fun toyDao(): ToyDao
    abstract fun statsDao(): StatsDao
    abstract fun hardcoreDao(): HardcoreDao

    companion object {
        const val NAME = "lockeddesire.db"

        /**
         * Migrationen: Bei jeder Schemaänderung die Version erhöhen und hier eine
         * Migration(alt, neu) ergänzen. So bleiben Nutzerdaten bei Updates erhalten.
         */
        /** SQL der Migration 1→2. Ein Unit-Test vergleicht es mit dem von Room exportierten Schema. */
        val MIGRATION_1_2_SQL = listOf(
            "CREATE TABLE IF NOT EXISTS `HardcoreTask` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `code` TEXT NOT NULL, `title` TEXT NOT NULL, `categoryKey` TEXT NOT NULL, `description` TEXT NOT NULL, `difficulty` INTEGER NOT NULL, `durationMin` INTEGER NOT NULL, `xp` INTEGER NOT NULL, `physical` INTEGER NOT NULL, `jointLoad` INTEGER NOT NULL, `partnerRequired` INTEGER NOT NULL, `toyCategory` TEXT NOT NULL, `anatomy` TEXT NOT NULL, `roles` TEXT NOT NULL, `requirements` TEXT NOT NULL, `safetyClass` TEXT NOT NULL, `status` TEXT NOT NULL, `custom` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_HardcoreTask_code` ON `HardcoreTask` (`code`)",
            "CREATE INDEX IF NOT EXISTS `index_HardcoreTask_categoryKey` ON `HardcoreTask` (`categoryKey`)",
            "CREATE TABLE IF NOT EXISTS `HardcoreRun` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `taskIds` TEXT NOT NULL, `code` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `categoryKey` TEXT NOT NULL, `categories` TEXT NOT NULL, `difficulty` INTEGER NOT NULL, `xp` INTEGER NOT NULL, `durationMin` INTEGER NOT NULL, `stepsTotal` INTEGER NOT NULL, `stepsDone` INTEGER NOT NULL, `toyId` INTEGER NOT NULL, `toyName` TEXT NOT NULL, `partner` INTEGER NOT NULL, `personaId` INTEGER NOT NULL, `source` TEXT NOT NULL, `status` TEXT NOT NULL, `verdict` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `acceptedAt` INTEGER NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_HardcoreRun_status` ON `HardcoreRun` (`status`)",
            "CREATE INDEX IF NOT EXISTS `index_HardcoreRun_createdAt` ON `HardcoreRun` (`createdAt`)",
            "CREATE TABLE IF NOT EXISTS `HardcoreSettings` (`id` INTEGER NOT NULL, `confirmed` INTEGER NOT NULL, `difficulty` INTEGER NOT NULL, `categories` TEXT NOT NULL, `personaId` INTEGER NOT NULL, `anatomy` TEXT NOT NULL, `includeIn24h` INTEGER NOT NULL, `notify` INTEGER NOT NULL, `chains` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) { MIGRATION_1_2_SQL.forEach { db.execSQL(it) } }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

        private fun build(context: Context): AppDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = Crypto.databasePassphrase(context)
            return Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .addMigrations(*MIGRATIONS)
                .build()
        }

        fun close() {
            synchronized(this) { instance?.close(); instance = null }
        }
    }
}
