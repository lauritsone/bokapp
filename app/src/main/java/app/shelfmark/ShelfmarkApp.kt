package app.shelfmark

import android.app.Application
import androidx.room.Room
import app.shelfmark.data.AppDatabase
import app.shelfmark.data.BookLookup
import app.shelfmark.data.BookRepository

class ShelfmarkApp : Application() {
    private val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "shelfmark.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
    }

    val repository by lazy {
        BookRepository(database.bookDao(), BookLookup(), filesDir)
    }
}
