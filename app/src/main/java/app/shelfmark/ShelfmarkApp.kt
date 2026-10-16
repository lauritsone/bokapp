package app.shelfmark

import android.app.Application
import androidx.room.Room
import app.shelfmark.data.AppDatabase
import app.shelfmark.data.BookLookup
import app.shelfmark.data.BookRepository

class ShelfmarkApp : Application() {
    private val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "shelfmark.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6)
            .build()
    }

    val repository by lazy {
        BookRepository(database.bookDao(), BookLookup(), filesDir)
    }

    override fun onCreate() {
        super.onCreate()
        // Fill in covers that earlier imports or lookups couldn't find.
        repository.repairCovers()
    }
}
