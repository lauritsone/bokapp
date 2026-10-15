package app.shelfmark.ui.components

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import app.shelfmark.R

/** App languages. An empty tag follows the phone's language. */
private enum class AppLanguage(val tag: String, @StringRes val label: Int) {
    System("", R.string.language_system),
    English("en", R.string.language_english),
    Norwegian("nb", R.string.language_norwegian),
}

/**
 * Language choices for a dropdown menu. Android stores the choice and recreates the
 * screen in the new language; on Android 13+ it also shows up in the app's system settings.
 */
@Composable
fun LanguageMenuItems(onChosen: () -> Unit) {
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    val selected = AppLanguage.entries.firstOrNull { it.tag.isNotEmpty() && current.startsWith(it.tag) }
        ?: AppLanguage.System

    Text(
        stringResource(R.string.language),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )
    AppLanguage.entries.forEach { language ->
        DropdownMenuItem(
            text = { Text(stringResource(language.label)) },
            trailingIcon = {
                if (language == selected) Icon(Icons.Rounded.Check, contentDescription = null)
            },
            onClick = {
                onChosen()
                if (language != selected) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
                }
            },
        )
    }
}
