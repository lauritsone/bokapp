package app.shelfmark.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Book::class], version = 3, exportSchema = false)
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
    }
}
