package com.android.shaftschematic.ui.input

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.android.shaftschematic.util.parseFractionOrDecimal

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommitTextField(
    label: String,
    initial: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 5,
    minHeight: Dp = Dp.Unspecified
) {
    val bringer = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    var text by remember(initial) { mutableStateOf(initial) }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommit(text) }),
        modifier = modifier
            .let { if (minHeight != Dp.Unspecified) it.heightIn(min = minHeight) else it }
            .bringIntoViewRequester(bringer)
            .onFocusChanged { f ->
                if (!f.isFocused) onCommit(text) else scope.launch {
                    delay(100); bringer.bringIntoView()
                }
            }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommitNum(
    label: String,
    initialDisplay: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val bringer = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    NumericInputField(
        label = label,
        initialText = initialDisplay,
        modifier = modifier
            .bringIntoViewRequester(bringer)
            .onFocusChanged { f ->
                if (f.isFocused) scope.launch {
                    delay(100); bringer.bringIntoView()
                }
            },
        allowNegative = false,
        allowFraction = true,
        parseValid = { parseFractionOrDecimal(it) != null },
        onCommit = onCommit
    )
}
