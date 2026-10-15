package app.shelfmark.ui.components

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import app.shelfmark.R

/** App languages. An empty tag follows the phone's language. */
private enum class AppLanguage(val tag: String, @StringRes val label: Int) {
    System("", R.string.language_system),
    English("en", R.string.language_english),
    Norwegian("nb", R.string.language_norwegian),
}

/**
 * Picks the app's language. Android stores the choice and recreates the screen in
 * the new language; on Android 13+ it also shows up under the app's system settings.
 */
@Composable
fun LanguageButton(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    val selected = AppLanguage.entries.firstOrNull { it.tag.isNotEmpty() && current.startsWith(it.tag) }
        ?: AppLanguage.System

    Box(modifier) {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.Translate, contentDescription = stringResource(R.string.language))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AppLanguage.entries.forEach { language ->
                DropdownMenuItem(
                    text = { Text(stringResource(language.label)) },
                    trailingIcon = {
                        if (language == selected) Icon(Icons.Rounded.Check, contentDescription = null)
                    },
                    onClick = {
                        open = false
                        if (language != selected) {
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
                        }
                    },
                )
            }
        }
    }
}
