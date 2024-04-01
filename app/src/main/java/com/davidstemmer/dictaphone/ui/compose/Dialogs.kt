package com.davidstemmer.dictaphone.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.davidstemmer.dictaphone.R

object Dialogs {

    @Composable
    fun ChooseFilename(onConfirm: (String) -> Unit,
                       onDismiss: () -> Unit) {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    var text by remember { mutableStateOf("") }
                    val focusRequester = remember { FocusRequester() }

                    Text(
                        text = stringResource(R.string.enter_filename),
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentSize(Alignment.Center),
                        textAlign = TextAlign.Center,
                    )
                    OutlinedTextField(
                        modifier = Modifier.focusRequester(focusRequester),
                        singleLine = true,
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Filename") },
                    )
                    TextButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.0f),
                        onClick = {
                            onConfirm(text)
                            onDismiss()
                        },
                    ) {
                        Text(stringResource(R.string.confirm))
                    }
                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                    }
                }
            }
        }
    }
}