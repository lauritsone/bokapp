package app.shelfmark.ui.scan

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
        title = { Text("Type the ISBN", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Text(
                    "It's the 10 or 13 digits printed above the barcode, or on the copyright page.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() || it in "xX- " }.take(17) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("ISBN") },
                    isError = showError,
                    supportingText = {
                        if (showError) Text("Check the digits. This isn't a valid ISBN.")
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { isbn?.let(onSubmit) }),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { isbn?.let(onSubmit) }, enabled = isbn != null) { Text("Look up book") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
