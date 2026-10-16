package app.shelfmark.ui.scan

import app.shelfmark.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.shelfmark.data.Isbn

@Composable
fun IsbnDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val isbn = Isbn.normalize(text)
    val digits = text.count { it.isDigit() || it == 'x' || it == 'X' }
    val showError = digits >= 10 && isbn == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.type_isbn_title), style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Text(
                    stringResource(R.string.type_isbn_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() || it in "xX- " }.take(17) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.isbn)) },
                    isError = showError,
                    supportingText = {
                        if (showError) Text(stringResource(R.string.isbn_invalid))
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { isbn?.let(onSubmit) }),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { isbn?.let(onSubmit) }, enabled = isbn != null) { Text(stringResource(R.string.look_up_book)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
