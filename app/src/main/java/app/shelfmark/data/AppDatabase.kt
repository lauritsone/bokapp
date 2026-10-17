package app.shelfmark.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Book::class], version = 8, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        /** Adds shelves; every book logged so far counts as read. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN shelf INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Adds genre tags for match scores; filled in later from the catalogues. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN genres TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN profiledAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Tracks which books have been through the cover search. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN coverCheckedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Adds the date a book was finished. Books read before this have none. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN finishedAt INTEGER")
            }
        }

        /** Lets books be left out of new-release suggestions; all count until the user says otherwise. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN ignoreForSuggestions INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Reading dates and progress, series, format, re-reads, lending and the user's own tags. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN startedAt INTEGER")
                db.execSQL("ALTER TABLE books ADD COLUMN currentPage INTEGER")
                db.execSQL("ALTER TABLE books ADD COLUMN series TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN seriesNumber TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN format INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE books ADD COLUMN rereads INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE books ADD COLUMN lentTo TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN lentAt INTEGER")
                db.execSQL("ALTER TABLE books ADD COLUMN tags TEXT")
            }
        }

        /** An index on ISBN, the lookup behind every scan and import. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_books_isbn ON books (isbn)")
            }
        }
    }
}
