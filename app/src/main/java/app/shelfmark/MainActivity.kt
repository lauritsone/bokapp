package app.shelfmark

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.shelfmark.ui.detail.BookDetailScreen
import app.shelfmark.ui.edit.BookEditScreen
import app.shelfmark.ui.library.LibraryScreen
import app.shelfmark.ui.scan.ScanScreen
import app.shelfmark.ui.theme.ShelfmarkTheme

class MainActivity : ComponentActivity() {
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
    const val SCAN = "scan"
    const val DETAIL = "book/{id}"
    const val EDIT = "edit?id={id}&isbn={isbn}&photo={photo}"

    fun detail(id: Long) = "book/$id"
    fun editExisting(id: Long) = "edit?id=$id"
    fun newFromIsbn(isbn: String) = "edit?isbn=$isbn"
    fun newFromPhoto(path: String) = "edit?photo=${Uri.encode(path)}"
    const val NEW_BY_HAND = "edit"
}

@Composable
private fun ShelfmarkNavHost() {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onScan = { nav.navigate(Routes.SCAN) },
                onAddByHand = { nav.navigate(Routes.NEW_BY_HAND) },
                onOpen = { id -> nav.navigate(Routes.detail(id)) },
            )
        }

        composable(Routes.SCAN) {
            ScanScreen(
                onBack = { nav.popBackStack() },
                // Replace the scanner with the new-book screen, so Back returns to the library.
                onIsbn = { isbn ->
                    nav.navigate(Routes.newFromIsbn(isbn)) { popUpTo(Routes.SCAN) { inclusive = true } }
                },
                onPhoto = { path ->
                    nav.navigate(Routes.newFromPhoto(path)) { popUpTo(Routes.SCAN) { inclusive = true } }
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
            ),
        ) { entry ->
            val args = entry.arguments
            val id = args?.getLong("id") ?: 0L
            BookEditScreen(
                bookId = id,
                isbn = args?.getString("isbn"),
                photoPath = args?.getString("photo"),
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
