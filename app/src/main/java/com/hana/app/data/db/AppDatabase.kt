package com.hana.app.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hana.app.data.db.dao.CachedModelDao
import com.hana.app.data.db.dao.CharacterCardDao
import com.hana.app.data.db.dao.ChatMessageDao
import com.hana.app.data.db.dao.ConversationDao
import com.hana.app.data.db.dao.MemoryEntryDao
import com.hana.app.data.db.dao.SavedModelDao
import com.hana.app.data.db.entity.MemoryEntryEntity
import com.hana.app.data.db.entity.CharacterCardEntity
import com.hana.app.data.db.entity.ChatMessageEntity
import com.hana.app.data.db.entity.ConversationEntity
import com.hana.app.data.db.entity.SavedModelEntity

@Database(
    entities = [
        CharacterCardEntity::class,
        ConversationEntity::class,
        ChatMessageEntity::class,
        SavedModelEntity::class,
        com.hana.app.data.db.entity.CachedModelEntity::class,
        MemoryEntryEntity::class
    ],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun characterCardDao(): CharacterCardDao
    abstract fun conversationDao(): ConversationDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun savedModelDao(): SavedModelDao
    abstract fun cachedModelDao(): CachedModelDao
    abstract fun memoryEntryDao(): MemoryEntryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private const val DB_NAME = "workspace_chat.db"
        private const val TAG = "AppDatabase"

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildSafe(context).also { INSTANCE = it }
            }
        }

        private fun buildSafe(context: Context): AppDatabase {
            val ctx = context.applicationContext
            Log.i(TAG, "Opening database without destructive fallback")
            return Room.databaseBuilder(ctx, AppDatabase::class.java, DB_NAME)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12
                )
                .addCallback(object : Callback() {
                    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onOpen(db)
                        try { db.execSQL("PRAGMA foreign_keys=ON") }
                        catch (_: Exception) {}
                    }
                })
                .build()
        }

        private val MIGRATION_1_2 = noopMigration(1, 2)
        private val MIGRATION_2_3 = noopMigration(2, 3)
        private val MIGRATION_3_4 = noopMigration(3, 4)
        private val MIGRATION_4_5 = noopMigration(4, 5)
        private val MIGRATION_5_6 = noopMigration(5, 6)
        private val MIGRATION_6_7 = noopMigration(6, 7)
        private val MIGRATION_7_8 = noopMigration(7, 8)
        private val MIGRATION_8_9 = noopMigration(8, 9)

        private val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS memory_entries (
                        id TEXT NOT NULL PRIMARY KEY,
                        scope TEXT NOT NULL,
                        type TEXT NOT NULL,
                        content TEXT NOT NULL,
                        sourceConversationId TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        confidence REAL NOT NULL,
                        isPinned INTEGER NOT NULL,
                        isArchived INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_entries_scope ON memory_entries(scope)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_entries_updatedAt ON memory_entries(updatedAt)")
            }
        }

        private val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_models_baseUrl ON cached_models(baseUrl)")
            }
        }

        private val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN conversationType TEXT NOT NULL DEFAULT 'normal'")
                db.execSQL("ALTER TABLE conversations ADD COLUMN participantCharacterIds TEXT")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN speakerCharacterId TEXT")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN speakerName TEXT")
            }
        }

        private fun noopMigration(from: Int, to: Int) = object : androidx.room.migration.Migration(from, to) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
        }
    }
}
