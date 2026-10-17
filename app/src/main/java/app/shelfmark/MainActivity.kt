package app.shelfmark

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.shelfmark.data.Shelf
import app.shelfmark.ui.detail.BookDetailScreen
import app.shelfmark.ui.edit.BookEditScreen
import app.shelfmark.ui.library.LibraryScreen
import app.shelfmark.ui.releases.ReleasesScreen
import app.shelfmark.ui.scan.ScanScreen
import app.shelfmark.ui.theme.ShelfmarkTheme

// AppCompatActivity applies the in-app language choice on Android versions before 13.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShelfmarkTheme {
                ShelfmarkNavHost()
            }
        }
    }
}

private object Routes {
    const val LIBRARY = "library"
    const val RELEASES = "releases"
    const val SCAN = "scan?shelf={shelf}"
    const val DETAIL = "book/{id}"
    const val EDIT = "edit?id={id}&isbn={isbn}&photo={photo}&shelf={shelf}"

    // New books go on the shelf the user was looking at.
    fun scan(shelf: Shelf) = "scan?shelf=${shelf.code}"
    fun detail(id: Long) = "book/$id"
    fun editExisting(id: Long) = "edit?id=$id"
    fun newFromIsbn(isbn: String, shelf: Shelf) = "edit?isbn=$isbn&shelf=${shelf.code}"
    fun newFromPhoto(path: String, shelf: Shelf) = "edit?photo=${Uri.encode(path)}&shelf=${shelf.code}"
    fun newByHand(shelf: Shelf) = "edit?shelf=${shelf.code}"
}

private val shelfArgument = navArgument("shelf") { type = NavType.IntType; defaultValue = Shelf.Read.code }

/**
 * Runs [block] only while this screen is the one showing, so a double tap can't navigate
 * twice (popping past the library to a blank screen, or opening a screen twice).
 */
private fun NavBackStackEntry.once(block: () -> Unit) {
    if (lifecycle.currentState == Lifecycle.State.RESUMED) block()
}

private fun NavBackStackEntry.shelf() = Shelf.fromCode(arguments?.getInt("shelf") ?: Shelf.Read.code)

@Composable
private fun ShelfmarkNavHost() {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) { entry ->
            LibraryScreen(
                onScan = { shelf -> entry.once { nav.navigate(Routes.scan(shelf)) } },
                onAddByHand = { shelf -> entry.once { nav.navigate(Routes.newByHand(shelf)) } },
                onOpen = { id -> entry.once { nav.navigate(Routes.detail(id)) } },
                onReleases = { entry.once { nav.navigate(Routes.RELEASES) } },
            )
        }

        composable(Routes.RELEASES) { entry ->
            ReleasesScreen(
                onBack = { entry.once { nav.popBackStack() } },
                onOpenBook = { id -> entry.once { nav.navigate(Routes.detail(id)) } },
            )
        }

        composable(Routes.SCAN, arguments = listOf(shelfArgument)) { entry ->
            val shelf = entry.shelf()
            ScanScreen(
                onBack = { entry.once { nav.popBackStack() } },
                // Replace the scanner with the new-book screen, so Back returns to the library.
                onIsbn = { isbn ->
                    entry.once { nav.navigate(Routes.newFromIsbn(isbn, shelf)) { popUpTo(Routes.SCAN) { inclusive = true } } }
                },
                onPhoto = { path ->
                    // A photo finishing after the user left the scanner is dropped (its file too).
                    if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                        nav.navigate(Routes.newFromPhoto(path, shelf)) { popUpTo(Routes.SCAN) { inclusive = true } }
                    } else {
                        java.io.File(path).delete()
                    }
                },
            )
        }

        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            BookDetailScreen(
                bookId = id,
                onBack = { entry.once { nav.popBackStack() } },
                onEdit = { entry.once { nav.navigate(Routes.editExisting(id)) } },
                onOpenBook = { other -> entry.once { nav.navigate(Routes.detail(other)) } },
            )
        }

        composable(
            Routes.EDIT,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                navArgument("isbn") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("photo") { type = NavType.StringType; nullable = true; defaultValue = null },
                shelfArgument,
            ),
        ) { entry ->
            val args = entry.arguments
            val id = args?.getLong("id") ?: 0L
            BookEditScreen(
                bookId = id,
                isbn = args?.getString("isbn"),
                photoPath = args?.getString("photo"),
                shelf = entry.shelf(),
                onBack = { entry.once { nav.popBackStack() } },
                // Sent once by the screen after saving, not by a tap, so it isn't guarded.
                onSaved = { savedId ->
                    if (id == 0L) {
                        nav.navigate(Routes.detail(savedId)) { popUpTo(Routes.LIBRARY) }
                    } else {
                        nav.popBackStack()
                    }
                },
                onOpenExisting = { existingId ->
                    entry.once { nav.navigate(Routes.detail(existingId)) { popUpTo(Routes.LIBRARY) } }
                },
            )
        }
    }
}
