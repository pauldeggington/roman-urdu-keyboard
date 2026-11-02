package com.paul.urdukb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.paul.urdukb.R
import java.io.File

/**
 * The voice sheet that opens over another app when an Urdu voice note is shared to this one. It shows the same
 * [VoiceBody] as the Voice tab, in a dark sheet with the app's grab handle and a close button.
 */
@Composable
fun VoiceScreen(
    model: VoiceModel,
    onRecord: () -> Unit,
    onShareAudio: (File) -> Unit,
    onVoiceSettings: () -> Unit,
    onClose: () -> Unit,
) {
    val state = model.state
    val speak = model.mode == VoiceModel.Mode.ENGLISH_TO_URDU
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Brand.card)
                .border(1.dp, Brand.line, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 22.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 14.dp).fillMaxWidth(0.12f).heightIn(min = 4.dp).clip(RoundedCornerShape(2.dp)).background(Brand.line))
            SectionLabel(stringResource(if (speak) R.string.voice_title_speak else R.string.voice_title_share))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 6.dp)) {
                VoiceBody(model, speak, onShareAudio, onVoiceSettings)
            }
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (speak && state !is VoiceModel.UiState.NoModel && state !is VoiceModel.UiState.NeedsGemma4) {
                    val busy = state is VoiceModel.UiState.Listening || state is VoiceModel.UiState.Comparing || state is VoiceModel.UiState.Checking || state is VoiceModel.UiState.WritingUrdu || state is VoiceModel.UiState.WritingEnglish
                    PillButton(
                        if (state is VoiceModel.UiState.Recording) stringResource(R.string.voice_stop)
                        else if (state is VoiceModel.UiState.Done) stringResource(R.string.voice_record_again)
                        else stringResource(R.string.voice_record),
                        Modifier.weight(1f).fillMaxHeight(), filled = true, enabled = !busy, icon = R.drawable.kb_mic, loading = busy,
                    ) { onRecord() }
                }
                PillButton(stringResource(R.string.sheet_close), Modifier.weight(1f).fillMaxHeight()) { onClose() }
            }
        }
    }
}
