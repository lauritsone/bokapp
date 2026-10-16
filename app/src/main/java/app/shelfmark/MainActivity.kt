package app.shelfmark

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
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

private fun NavBackStackEntry.shelf() = Shelf.fromCode(arguments?.getInt("shelf") ?: Shelf.Read.code)

@Composable
private fun ShelfmarkNavHost() {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onScan = { shelf -> nav.navigate(Routes.scan(shelf)) },
                onAddByHand = { shelf -> nav.navigate(Routes.newByHand(shelf)) },
                onOpen = { id -> nav.navigate(Routes.detail(id)) },
                onReleases = { nav.navigate(Routes.RELEASES) },
            )
        }

        composable(Routes.RELEASES) {
            ReleasesScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.SCAN, arguments = listOf(shelfArgument)) { entry ->
            val shelf = entry.shelf()
            ScanScreen(
                onBack = { nav.popBackStack() },
                // Replace the scanner with the new-book screen, so Back returns to the library.
                onIsbn = { isbn ->
                    nav.navigate(Routes.newFromIsbn(isbn, shelf)) { popUpTo(Routes.SCAN) { inclusive = true } }
                },
                onPhoto = { path ->
                    nav.navigate(Routes.newFromPhoto(path, shelf)) { popUpTo(Routes.SCAN) { inclusive = true } }
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
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.editExisting(id)) },
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
                onBack = { nav.popBackStack() },
                onSaved = { savedId ->
                    if (id == 0L) {
                        nav.navigate(Routes.detail(savedId)) { popUpTo(Routes.LIBRARY) }
                    } else {
                        nav.popBackStack()
                    }
                },
                onOpenExisting = { existingId ->
                    nav.navigate(Routes.detail(existingId)) { popUpTo(Routes.LIBRARY) }
                },
            )
        }
    }
}
