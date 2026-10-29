package com.paul.urdukb

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paul.urdukb.ui.RomanUrduTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Translate to English" in the text-selection menu of other apps: select a message someone sent you in Roman Urdu,
 * and a small sheet shows the English. Runs on the phone with the same model as the keyboard.
 */
class TranslateTextActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // any app may send text here, so only a sensible amount is read
        val text = intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim().orEmpty().take(4000)
        if (text.isEmpty()) { finish(); return }
        // emulator preview builds have no model: a finished layout for a screenshot (never in real builds)
        val previewResult = if (BuildConfig.PREVIEW) intent.getStringExtra("preview_result") else null

        setContent {
            RomanUrduTheme {
                TranslateSheet(text, previewResult, onClose = { finish() })
            }
        }
    }
}

private sealed interface SheetState {
    data object Loading : SheetState
    data class Done(val english: String) : SheetState
    data object NoModel : SheetState
    data object Failed : SheetState
}

@Composable
private fun TranslateSheet(source: String, previewResult: String?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val thinking = remember { Words.thinking() }
    var state by remember {
        mutableStateOf<SheetState>(
            when {
                previewResult != null -> SheetState.Done(previewResult)
                !LlmEngine.modelExists(ctx) -> SheetState.NoModel
                else -> SheetState.Loading
            },
        )
    }
    var copied by remember { mutableStateOf(false) }
    val firstLoad = remember { !LlmEngine.isLoaded() }

    LaunchedEffect(Unit) {
        if (state != SheetState.Loading) return@LaunchedEffect
        val out = withContext(Dispatchers.IO) {
            try {
                LlmEngine.translate(ctx, source, Style.TO_ENGLISH)
            } catch (t: Throwable) {
                LlmEngine.saveError(ctx, t)
                null
            }
        }
        state = if (out.isNullOrBlank()) SheetState.Failed else SheetState.Done(out)
    }

    val nothing = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().background(Color(0x66000000)).clickable(interactionSource = nothing, indication = null) { onClose() },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }   // taps on the sheet do not close it
                .navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 22.dp),
        ) {
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            Text(
                when {
                    copied -> stringResource(R.string.sheet_copied)
                    state == SheetState.Loading && firstLoad -> stringResource(R.string.sheet_loading_first)
                    else -> stringResource(R.string.sheet_direction)
                },
                color = muted, fontSize = 13.sp,
            )
            Text(source, color = muted, fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
            SelectionContainer {
                Text(
                    when (val s = state) {
                        SheetState.Loading -> thinking
                        is SheetState.Done -> s.english
                        SheetState.NoModel -> stringResource(R.string.sheet_no_model)
                        SheetState.Failed -> stringResource(R.string.sheet_failed)
                    },
                    color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, lineHeight = 26.sp,
                )
            }
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state != SheetState.NoModel) {
                    Button(
                        onClick = {
                            (state as? SheetState.Done)?.let {
                                ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("English", it.english))
                                copied = true
                            }
                        },
                        enabled = state is SheetState.Done,
                        modifier = Modifier.weight(1f).height(46.dp),
                    ) { Text(stringResource(R.string.sheet_copy), fontSize = 15.sp) }
                }
                Button(
                    onClick = onClose,
                    modifier = Modifier.weight(1f).height(46.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) { Text(stringResource(R.string.sheet_close), fontSize = 15.sp) }
            }
        }
    }
}
