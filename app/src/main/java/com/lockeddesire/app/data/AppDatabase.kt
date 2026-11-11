package com.lockeddesire.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
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
        NotificationSettings::class, SessionEntry::class
    ],
    version = 1,
    exportSchema = false
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

    companion object {
        const val NAME = "lockeddesire.db"

        /**
         * Migrationen: Bei jeder Schemaänderung die Version erhöhen und hier eine
         * Migration(alt, neu) ergänzen. So bleiben Nutzerdaten bei Updates erhalten.
         */
        val MIGRATIONS: Array<Migration> = arrayOf()

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
