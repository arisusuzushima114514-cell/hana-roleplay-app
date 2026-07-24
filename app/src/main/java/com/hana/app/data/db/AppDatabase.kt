package com.hana.app.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 16,
    exportSchema = true
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
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16
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

        private val MIGRATION_1_2 = compatibilityMigration(1, 2)
        private val MIGRATION_2_3 = compatibilityMigration(2, 3)
        private val MIGRATION_3_4 = compatibilityMigration(3, 4)
        private val MIGRATION_4_5 = compatibilityMigration(4, 5)
        private val MIGRATION_5_6 = compatibilityMigration(5, 6)
        private val MIGRATION_6_7 = compatibilityMigration(6, 7)
        private val MIGRATION_7_8 = compatibilityMigration(7, 8)
        private val MIGRATION_8_9 = compatibilityMigration(8, 9)

        private val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                ensureCompatibleSchema(db)
            }
        }

        private val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                ensureCompatibleSchema(db)
            }
        }

        private val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                ensureCompatibleSchema(db)
            }
        }

        private val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Some older builds partially applied this migration before being interrupted.
                // Check each column so an update never crashes on a half-migrated database.
                addColumnIfMissing(db, "conversations", "historySummary", "TEXT")
                addColumnIfMissing(db, "conversations", "authorNote", "TEXT")
                addColumnIfMissing(db, "conversations", "worldInfo", "TEXT")
                addColumnIfMissing(db, "conversations", "summaryUpToMessageId", "INTEGER")
            }
        }

        private val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                addColumnIfMissing(db, "chat_messages", "roundId", "TEXT")
                addColumnIfMissing(db, "chat_messages", "turnIndex", "INTEGER")
                addColumnIfMissing(db, "chat_messages", "replyToMessageId", "INTEGER")
                addColumnIfMissing(db, "chat_messages", "replyToSpeakerCharacterId", "TEXT")
                addColumnIfMissing(db, "chat_messages", "replyToSpeakerName", "TEXT")
                addColumnIfMissing(db, "chat_messages", "replyToContent", "TEXT")
            }
        }

        private val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                addColumnIfMissing(db, "conversations", "groupScene", "TEXT")
                addColumnIfMissing(db, "conversations", "groupSceneLocked", "INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                addColumnIfMissing(db, "character_cards", "characterMode", "TEXT NOT NULL DEFAULT 'single'")
                addColumnIfMissing(db, "character_cards", "subCharactersJson", "TEXT NOT NULL DEFAULT '{\"version\":1,\"profiles\":[]}'")
            }
        }

        private fun compatibilityMigration(from: Int, to: Int) = object : androidx.room.migration.Migration(from, to) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureCompatibleSchema(db)
            }
        }

        private fun ensureCompatibleSchema(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS character_cards (
                    id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, avatarUrl TEXT NOT NULL,
                    description TEXT NOT NULL, greeting TEXT NOT NULL, userPersona TEXT NOT NULL DEFAULT '',
                    tags TEXT NOT NULL DEFAULT '', modelId TEXT NOT NULL DEFAULT '', temperature REAL NOT NULL DEFAULT 0.9,
                    createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, lastMessageAt INTEGER NOT NULL DEFAULT 0,
                    lastMessagePreview TEXT NOT NULL DEFAULT '', characterMode TEXT NOT NULL DEFAULT 'single',
                    subCharactersJson TEXT NOT NULL DEFAULT '{"version":1,"profiles":[]}'
                )
            """.trimIndent())
            addColumnIfMissing(db, "character_cards", "userPersona", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "character_cards", "tags", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "character_cards", "modelId", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "character_cards", "temperature", "REAL NOT NULL DEFAULT 0.9")
            addColumnIfMissing(db, "character_cards", "lastMessageAt", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "character_cards", "lastMessagePreview", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "character_cards", "characterMode", "TEXT NOT NULL DEFAULT 'single'")
            addColumnIfMissing(db, "character_cards", "subCharactersJson", "TEXT NOT NULL DEFAULT '{\"version\":1,\"profiles\":[]}'")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS conversations (
                    id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, conversationType TEXT NOT NULL DEFAULT 'normal',
                    characterId TEXT, participantCharacterIds TEXT, characterName TEXT, characterAvatar TEXT,
                    createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, lastMessage TEXT, isNamed INTEGER NOT NULL DEFAULT 0,
                    modelName TEXT, temperature REAL NOT NULL DEFAULT 0.7, topP REAL NOT NULL DEFAULT 1.0,
                    maxTokens INTEGER NOT NULL DEFAULT 8192, contextLimit INTEGER NOT NULL DEFAULT 36,
                     systemPrompt TEXT, historySummary TEXT, authorNote TEXT, worldInfo TEXT,
                     groupScene TEXT, groupSceneLocked INTEGER NOT NULL DEFAULT 0,
                    summaryUpToMessageId INTEGER, totalTokens INTEGER NOT NULL DEFAULT 0, isPinned INTEGER NOT NULL DEFAULT 0,
                    isFavorite INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            addColumnIfMissing(db, "conversations", "conversationType", "TEXT NOT NULL DEFAULT 'normal'")
            addColumnIfMissing(db, "conversations", "characterId", "TEXT")
            addColumnIfMissing(db, "conversations", "participantCharacterIds", "TEXT")
            addColumnIfMissing(db, "conversations", "characterName", "TEXT")
            addColumnIfMissing(db, "conversations", "characterAvatar", "TEXT")
            addColumnIfMissing(db, "conversations", "lastMessage", "TEXT")
            addColumnIfMissing(db, "conversations", "isNamed", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "conversations", "modelName", "TEXT")
            addColumnIfMissing(db, "conversations", "temperature", "REAL NOT NULL DEFAULT 0.7")
            addColumnIfMissing(db, "conversations", "topP", "REAL NOT NULL DEFAULT 1.0")
            addColumnIfMissing(db, "conversations", "maxTokens", "INTEGER NOT NULL DEFAULT 8192")
            addColumnIfMissing(db, "conversations", "contextLimit", "INTEGER NOT NULL DEFAULT 36")
            addColumnIfMissing(db, "conversations", "systemPrompt", "TEXT")
            addColumnIfMissing(db, "conversations", "historySummary", "TEXT")
            addColumnIfMissing(db, "conversations", "authorNote", "TEXT")
            addColumnIfMissing(db, "conversations", "worldInfo", "TEXT")
            addColumnIfMissing(db, "conversations", "groupScene", "TEXT")
            addColumnIfMissing(db, "conversations", "groupSceneLocked", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "conversations", "summaryUpToMessageId", "INTEGER")
            addColumnIfMissing(db, "conversations", "totalTokens", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "conversations", "isPinned", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "conversations", "isFavorite", "INTEGER NOT NULL DEFAULT 0")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS chat_messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, conversationId TEXT NOT NULL, role TEXT NOT NULL,
                    speakerCharacterId TEXT, speakerName TEXT, roundId TEXT, turnIndex INTEGER,
                    replyToMessageId INTEGER, replyToSpeakerCharacterId TEXT, replyToSpeakerName TEXT,
                    replyToContent TEXT, content TEXT NOT NULL, thinkingContent TEXT,
                    thinkingDuration INTEGER, timestamp INTEGER NOT NULL, tokenCount INTEGER, isError INTEGER NOT NULL DEFAULT 0,
                    isFavorite INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(conversationId) REFERENCES conversations(id) ON DELETE CASCADE
                )
            """.trimIndent())
            addColumnIfMissing(db, "chat_messages", "speakerCharacterId", "TEXT")
            addColumnIfMissing(db, "chat_messages", "speakerName", "TEXT")
            addColumnIfMissing(db, "chat_messages", "roundId", "TEXT")
            addColumnIfMissing(db, "chat_messages", "turnIndex", "INTEGER")
            addColumnIfMissing(db, "chat_messages", "replyToMessageId", "INTEGER")
            addColumnIfMissing(db, "chat_messages", "replyToSpeakerCharacterId", "TEXT")
            addColumnIfMissing(db, "chat_messages", "replyToSpeakerName", "TEXT")
            addColumnIfMissing(db, "chat_messages", "replyToContent", "TEXT")
            addColumnIfMissing(db, "chat_messages", "thinkingContent", "TEXT")
            addColumnIfMissing(db, "chat_messages", "thinkingDuration", "INTEGER")
            addColumnIfMissing(db, "chat_messages", "tokenCount", "INTEGER")
            addColumnIfMissing(db, "chat_messages", "isError", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "chat_messages", "isFavorite", "INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_conversationId ON chat_messages(conversationId)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS saved_models (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, apiKey TEXT NOT NULL,
                    baseUrl TEXT NOT NULL, createdAt INTEGER NOT NULL, isActive INTEGER NOT NULL DEFAULT 0,
                    modelCount INTEGER NOT NULL DEFAULT 0, lastRefreshAt INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            addColumnIfMissing(db, "saved_models", "createdAt", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "saved_models", "isActive", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "saved_models", "modelCount", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "saved_models", "lastRefreshAt", "INTEGER NOT NULL DEFAULT 0")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS cached_models (
                    id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, provider TEXT NOT NULL, baseUrl TEXT NOT NULL,
                    isFavorite INTEGER NOT NULL DEFAULT 0, capabilities TEXT NOT NULL DEFAULT '', cachedAt INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            addColumnIfMissing(db, "cached_models", "provider", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "cached_models", "baseUrl", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "cached_models", "isFavorite", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "cached_models", "capabilities", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "cached_models", "cachedAt", "INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_models_baseUrl ON cached_models(baseUrl)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS memory_entries (
                    id TEXT NOT NULL PRIMARY KEY, scope TEXT NOT NULL, type TEXT NOT NULL, content TEXT NOT NULL,
                    sourceConversationId TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                    confidence REAL NOT NULL DEFAULT 0.7, isPinned INTEGER NOT NULL DEFAULT 0,
                    isArchived INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_entries_scope ON memory_entries(scope)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_entries_updatedAt ON memory_entries(updatedAt)")
        }

        private fun addColumnIfMissing(db: SupportSQLiteDatabase, table: String, column: String, definition: String) {
            if (!hasColumn(db, table, column)) {
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $definition")
            }
        }

        private fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0 && cursor.getString(nameIndex) == column) return true
                }
            }
            return false
        }
    }
}
