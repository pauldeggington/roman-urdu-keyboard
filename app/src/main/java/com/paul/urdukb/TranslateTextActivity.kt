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
import com.paul.urdukb.ui.Working
import com.paul.urdukb.ui.SectionLabel
import com.paul.urdukb.ui.PillButton
import com.paul.urdukb.ui.Brand
import com.paul.urdukb.ui.AppFont
import androidx.compose.foundation.border
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
        Modifier.fillMaxSize().background(Color(0x99000000)).clickable(interactionSource = nothing, indication = null) { onClose() },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Brand.card)
                .border(1.dp, Brand.line, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }   // taps on the sheet do not close it
                .navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 22.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 14.dp).fillMaxWidth(0.12f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Brand.line))
            SectionLabel(
                when {
                    copied -> stringResource(R.string.sheet_copied)
                    state == SheetState.Loading && firstLoad -> stringResource(R.string.sheet_loading_first)
                    else -> stringResource(R.string.sheet_direction)
                },
            )
            Text(source, color = Brand.muted, fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp, bottom = 12.dp))
            when (val s = state) {
                SheetState.Loading -> Working(thinking)
                is SheetState.Done -> SelectionContainer { Text(s.english, color = Brand.text, fontSize = 21.sp, lineHeight = 28.sp, fontFamily = AppFont) }
                SheetState.NoModel -> Text(stringResource(R.string.sheet_no_model), color = Brand.text, fontSize = 17.sp, lineHeight = 24.sp)
                SheetState.Failed -> Text(stringResource(R.string.sheet_failed), color = Brand.text, fontSize = 17.sp, lineHeight = 24.sp)
            }
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state != SheetState.NoModel) {
                    PillButton(
                        stringResource(R.string.sheet_copy), Modifier.weight(1f), filled = true, enabled = state is SheetState.Done,
                        icon = R.drawable.kb_copy, loading = state == SheetState.Loading,
                    ) {
                        (state as? SheetState.Done)?.let {
                            ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("English", it.english))
                            copied = true
                        }
                    }
                }
                PillButton(stringResource(R.string.sheet_close), Modifier.weight(1f)) { onClose() }
            }
        }
    }
}
