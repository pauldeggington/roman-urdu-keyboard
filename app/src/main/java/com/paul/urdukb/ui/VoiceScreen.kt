package com.paul.urdukb.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.paul.urdukb.R
import com.paul.urdukb.Words
import java.io.File

/**
 * The voice sheet: Urdu voice note to English, or English speech to an Urdu voice message. What it does is in [VoiceModel].
 */
@Composable
fun VoiceScreen(
    model: VoiceModel,
    onRecord: () -> Unit,
    onShareAudio: (File) -> Unit,
    onVoiceSettings: () -> Unit,
    onClose: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val state = model.state
    val speak = model.mode == VoiceModel.Mode.ENGLISH_TO_URDU
    val thinking = remember { Words.thinking() }
    Box(
        Modifier.fillMaxSize().background(Color(0x66000000)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(cs.surface)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 22.dp),
        ) {
            Muted(
                when {
                    model.copied.isNotEmpty() -> stringResource(R.string.sheet_copied)
                    speak -> stringResource(R.string.voice_title_speak)
                    else -> stringResource(R.string.voice_title_share)
                },
            )
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 10.dp)) {
                when (state) {
                    VoiceModel.UiState.Idle -> if (speak) Body(stringResource(R.string.voice_tap_to_speak))
                    VoiceModel.UiState.NoModel -> Body(stringResource(R.string.sheet_no_model))
                    VoiceModel.UiState.Decoding -> Body(stringResource(R.string.voice_decoding))
                    is VoiceModel.UiState.Listening -> {
                        Body(
                            if (state.of > 1) stringResource(R.string.voice_listening_part, state.part, state.of)
                            else stringResource(R.string.voice_listening),
                        )
                        if (model.firstLoad) Muted(stringResource(R.string.sheet_loading_first), Modifier.padding(top = 6.dp))
                        Muted(thinking, Modifier.padding(top = 6.dp))
                    }
                    VoiceModel.UiState.WritingUrdu -> Body(stringResource(R.string.voice_writing_urdu))
                    VoiceModel.UiState.WritingEnglish -> Body(stringResource(R.string.voice_writing_english))
                    is VoiceModel.UiState.Recording -> {
                        Body(stringResource(R.string.voice_recording, state.seconds))
                        Box(
                            Modifier.padding(top = 12.dp).fillMaxWidth(0.1f + 0.9f * state.level.coerceIn(0f, 1f)).heightIn(min = 8.dp).clip(RoundedCornerShape(4.dp)).background(cs.primary),
                        )
                    }
                    VoiceModel.UiState.TooQuiet -> Body(stringResource(R.string.voice_too_quiet))
                    VoiceModel.UiState.Failed -> Body(stringResource(R.string.voice_failed))
                    is VoiceModel.UiState.Done -> DoneBody(model, state, speak, onShareAudio, onVoiceSettings)
                }
            }
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (speak && state !is VoiceModel.UiState.NoModel) {
                    val busy = state is VoiceModel.UiState.Listening || state is VoiceModel.UiState.WritingUrdu || state is VoiceModel.UiState.WritingEnglish
                    PillButton(
                        if (state is VoiceModel.UiState.Recording) stringResource(R.string.voice_stop)
                        else if (state is VoiceModel.UiState.Done) stringResource(R.string.voice_record_again)
                        else stringResource(R.string.voice_record),
                        Modifier.weight(1f), filled = true, enabled = !busy,
                    ) { onRecord() }
                }
                PillButton(stringResource(R.string.sheet_close), Modifier.weight(1f)) { onClose() }
            }
        }
    }
}

@Composable
private fun DoneBody(
    model: VoiceModel,
    s: VoiceModel.UiState.Done,
    speak: Boolean,
    onShareAudio: (File) -> Unit,
    onVoiceSettings: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val content = TextStyle(textDirection = TextDirection.Content)
    if (s.english.isNotEmpty()) {
        Muted(stringResource(R.string.voice_english))
        SelectionContainer { Text(s.english, style = content, color = cs.onSurface, fontSize = Type.title, lineHeight = Type.title * 1.3f) }
    }
    if (s.urdu.isNotEmpty()) {
        Muted(stringResource(R.string.voice_urdu), Modifier.padding(top = 14.dp))
        SelectionContainer { Text(s.urdu, style = content, color = cs.onSurface, fontSize = Type.title, lineHeight = Type.title * 1.5f) }
    }
    if (s.roman.isNotEmpty()) {
        Muted(stringResource(R.string.voice_roman), Modifier.padding(top = 14.dp))
        SelectionContainer { Text(s.roman, style = content, color = cs.onSurface, fontSize = Type.bodyLarge, lineHeight = Type.bodyLarge * 1.4f) }
    }
    if (s.truncated) Muted(stringResource(R.string.voice_truncated), Modifier.padding(top = 10.dp))

    ButtonRow {
        if (s.english.isNotEmpty()) PillButton(stringResource(R.string.voice_copy_english), Modifier.weight(1f).fillMaxHeight()) { model.copy("English", s.english) }
        if (s.urdu.isNotEmpty()) PillButton(stringResource(R.string.voice_copy_urdu), Modifier.weight(1f).fillMaxHeight()) { model.copy("Urdu", s.urdu) }
    }
    if (speak && s.urdu.isNotEmpty()) {
        when (val a = model.audio) {
            VoiceModel.AudioState.None -> {}
            VoiceModel.AudioState.Making -> Muted(stringResource(R.string.voice_making_audio), Modifier.padding(top = 12.dp))
            is VoiceModel.AudioState.Ready -> ButtonRow {
                PillButton(stringResource(R.string.voice_play), Modifier.weight(1f).fillMaxHeight()) { model.play(s.urdu) }
                PillButton(stringResource(R.string.voice_share_audio), Modifier.weight(1f).fillMaxHeight(), filled = true) { onShareAudio(a.file) }
            }
            VoiceModel.AudioState.VoiceMissing -> {
                Muted(stringResource(R.string.voice_audio_missing), Modifier.padding(top = 12.dp))
                ButtonRow {
                    PillButton(stringResource(R.string.voice_open_voice_settings), Modifier.weight(1f).fillMaxHeight()) { onVoiceSettings() }
                    PillButton(stringResource(R.string.voice_try_again), Modifier.weight(1f).fillMaxHeight(), filled = true) { model.retryAudio() }
                }
            }
            VoiceModel.AudioState.Failed -> Muted(stringResource(R.string.voice_audio_failed), Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = Type.bodyLarge, lineHeight = Type.bodyLarge * 1.4f)
}
