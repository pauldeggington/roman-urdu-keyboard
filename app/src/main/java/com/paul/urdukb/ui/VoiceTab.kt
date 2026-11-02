package com.paul.urdukb.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.paul.urdukb.ModelChoice
import com.paul.urdukb.R
import com.paul.urdukb.ime.KbSettings
import kotlin.math.sin

/**
 * The Voice tab: speak English and get an Urdu voice message, or open an Urdu voice note and read it in English.
 * Both run on [VoiceModel]; the same body is shown in the sheet that opens when a voice note is shared to the app.
 */
fun LazyListScope.voiceTab(main: MainModel, voice: VoiceModel, actions: VoiceActions) {
    item { TabHeader(stringResource(R.string.tab_voice), stringResource(R.string.voice_tab_title), stringResource(R.string.voice_tab_lead)) }
    item {
        val speak = voice.mode == VoiceModel.Mode.ENGLISH_TO_URDU
        Segmented(
            listOf(stringResource(R.string.voice_mode_speak) to true, stringResource(R.string.voice_mode_note) to false),
            selected = speak,
        ) { s -> if (s) voice.prepareSpeak() else voice.prepareNote() }
    }
    item {
        if (main.snap.choice != ModelChoice.GEMMA4) NeedsGemma4Card(main)
        else {
            val speak = voice.mode == VoiceModel.Mode.ENGLISH_TO_URDU
            AppCard(Modifier.padding(top = 12.dp)) {
                if (speak) {
                    CardTitle(stringResource(R.string.voice_title_speak))
                    Muted(stringResource(R.string.voice_speak_help), Modifier.padding(top = 6.dp))
                    RecordButton(voice, actions.onRecord)
                } else {
                    CardTitle(stringResource(R.string.voice_title_share))
                    Muted(stringResource(R.string.voice_note_help), Modifier.padding(top = 6.dp))
                    Numbered(1, stringResource(R.string.voice_note_step1))
                    Numbered(2, stringResource(R.string.voice_note_step2))
                    PillButton(stringResource(R.string.voice_open_file), Modifier.fillMaxWidth().padding(top = 14.dp), icon = R.drawable.kb_paste) { actions.onPickAudio() }
                }
                VoiceBody(voice, speak, actions.onShareAudio, actions.onVoiceSettings)
            }
        }
    }
    item {
        AppCard {
            CardTitle(stringResource(R.string.voice_options_title))
            PrefSwitch(stringResource(R.string.voice_careful), stringResource(R.string.voice_careful_sub), KbSettings.CAREFUL_VOICE)
            Muted(stringResource(R.string.voice_private_note), Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun NeedsGemma4Card(main: MainModel) {
    AppCard(Modifier.padding(top = 12.dp)) {
        CardTitle(stringResource(R.string.voice_needs_gemma4_title))
        Muted(stringResource(R.string.voice_needs_gemma4), Modifier.padding(top = 6.dp))
        PillButton(stringResource(R.string.voice_get_gemma4), Modifier.fillMaxWidth().padding(top = 14.dp), filled = true, icon = R.drawable.ic_setup_download) {
            main.chooseModel(ModelChoice.GEMMA4)
            main.scrollRequest = Section.MODEL
        }
    }
}

/** A big round mic button. It breathes gold while recording. */
@Composable
private fun RecordButton(voice: VoiceModel, onRecord: () -> Unit) {
    val recording = voice.isRecording
    val busy = voice.state.let { it is VoiceModel.UiState.Listening || it is VoiceModel.UiState.Comparing || it is VoiceModel.UiState.Checking || it is VoiceModel.UiState.WritingUrdu || it is VoiceModel.UiState.WritingEnglish || it is VoiceModel.UiState.Decoding }
    val t = rememberInfiniteTransition(label = "breathe")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "p")
    val label = stringResource(
        when {
            recording -> R.string.voice_stop
            voice.state is VoiceModel.UiState.Done -> R.string.voice_record_again
            else -> R.string.voice_record
        },
    )
    Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            if (recording) {
                val s = 1f + 0.35f * sin(pulse * Math.PI).toFloat()
                Box(Modifier.size(96.dp).scale(s).clip(CircleShape).background(Brand.gold.copy(alpha = 0.18f)).clearAndSetSemantics {})
            }
            Box(
                Modifier.size(96.dp).clip(CircleShape)
                    .background(if (recording) Brand.gold else Brand.pillBg)
                    .clickable(enabled = !busy, role = Role.Button, onClickLabel = label) { onRecord() }
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) SpinningTranslate(34.dp, Brand.pillText)
                else TintedIcon(if (recording) R.drawable.kb_done else R.drawable.kb_mic, if (recording) Brand.onGold else Brand.pillText, 36.dp)
            }
        }
        Text(label, Modifier.padding(top = 12.dp).clearAndSetSemantics {}, color = Brand.text, fontSize = Type.body, fontWeight = FontWeight.Medium)
    }
}

/**
 * What the voice model is doing or found, under the controls. Shared by the Voice tab and the share sheet.
 */
@Composable
fun VoiceBody(model: VoiceModel, speak: Boolean, onShareAudio: (java.io.File) -> Unit, onVoiceSettings: () -> Unit) {
    val state = model.state
    val thinking = androidx.compose.runtime.remember { com.paul.urdukb.Words.thinking() }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        if (model.copied.isNotEmpty()) Muted(stringResource(R.string.sheet_copied), Modifier.padding(bottom = 6.dp))
        when (state) {
            VoiceModel.UiState.Idle -> if (speak) Muted(stringResource(R.string.voice_tap_to_speak), size = Type.body)
            VoiceModel.UiState.NoModel -> Muted(stringResource(R.string.sheet_no_model), size = Type.body)
            VoiceModel.UiState.NeedsGemma4 -> Muted(stringResource(R.string.voice_needs_gemma4), size = Type.body)
            VoiceModel.UiState.Decoding -> Working(stringResource(R.string.voice_decoding))
            is VoiceModel.UiState.Listening -> {
                Working(
                    when {
                        state.of > 1 && state.passes > 1 -> stringResource(R.string.voice_listening_part_pass, state.part, state.of, state.pass, state.passes)
                        state.of > 1 -> stringResource(R.string.voice_listening_part, state.part, state.of)
                        state.passes > 1 -> stringResource(R.string.voice_listening_pass, state.pass, state.passes)
                        else -> stringResource(R.string.voice_listening)
                    },
                )
                if (model.firstLoad) Muted(stringResource(R.string.sheet_loading_first), Modifier.padding(top = 6.dp))
                Muted(thinking, Modifier.padding(top = 6.dp))
            }
            VoiceModel.UiState.Comparing -> Working(stringResource(R.string.voice_comparing))
            VoiceModel.UiState.Checking -> Working(stringResource(R.string.voice_checking))
            VoiceModel.UiState.WritingUrdu -> Working(stringResource(R.string.voice_writing_urdu))
            VoiceModel.UiState.WritingEnglish -> Working(stringResource(R.string.voice_writing_english))
            is VoiceModel.UiState.Recording -> {
                Text(stringResource(R.string.voice_recording, state.seconds), color = Brand.text, fontSize = Type.bodyLarge)
                LevelBars(state.level)
            }
            VoiceModel.UiState.TooQuiet -> Muted(stringResource(R.string.voice_too_quiet), size = Type.body)
            VoiceModel.UiState.Failed -> Muted(stringResource(R.string.voice_failed), size = Type.body)
            is VoiceModel.UiState.Done -> DoneBody(model, state, speak, onShareAudio, onVoiceSettings)
        }
    }
}

/** The website's little waveform: bars that rise with the microphone level. */
@Composable
private fun LevelBars(level: Float) {
    val t = rememberInfiniteTransition(label = "bars")
    val f by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "f")
    Row(Modifier.padding(top = 12.dp).height(28.dp).clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until 18) {
            val wave = (sin((f * 2 * Math.PI) + i * 0.7).toFloat() + 1f) / 2f
            val h = 4.dp + (24.dp * (0.15f + 0.85f * level.coerceIn(0f, 1f) * wave))
            Box(Modifier.width(3.dp).height(h).clip(RoundedCornerShape(2.dp)).background(Brand.gold2))
        }
    }
}

@Composable
private fun DoneBody(
    model: VoiceModel,
    s: VoiceModel.UiState.Done,
    speak: Boolean,
    onShareAudio: (java.io.File) -> Unit,
    onVoiceSettings: () -> Unit,
) {
    val content = TextStyle(textDirection = TextDirection.Content, fontFamily = AppFont)
    if (s.english.isNotEmpty()) {
        Muted(stringResource(R.string.voice_english))
        SelectionContainer { Text(s.english, style = content, color = Brand.text, fontSize = Type.title, lineHeight = Type.title * 1.3f) }
    }
    if (s.urdu.isNotEmpty()) {
        Muted(stringResource(R.string.voice_urdu), Modifier.padding(top = 14.dp))
        SelectionContainer { Text(s.urdu, style = content, color = Brand.text, fontSize = Type.title, lineHeight = Type.title * 1.5f) }
    }
    if (s.roman.isNotEmpty()) {
        Muted(stringResource(R.string.voice_roman), Modifier.padding(top = 14.dp))
        SelectionContainer { Text(s.roman, style = content, color = Brand.text, fontSize = Type.bodyLarge, lineHeight = Type.bodyLarge * 1.4f) }
    }
    if (!speak && s.alternates.isNotEmpty()) {
        // what else the listening passes heard: tapping Use this translates that reading instead
        Muted(stringResource(R.string.voice_also_heard), Modifier.padding(top = 14.dp))
        s.alternates.forEach { alt ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(alt, Modifier.weight(1f).padding(end = 8.dp), style = content, color = Brand.text, fontSize = Type.bodyLarge, lineHeight = Type.bodyLarge * 1.5f)
                PillButton(stringResource(R.string.voice_use_this), Modifier.heightIn(min = 48.dp)) { model.useReading(alt) }
            }
        }
    }
    if (s.unsure) Muted(stringResource(R.string.voice_unsure), Modifier.padding(top = 10.dp))
    if (s.truncated) Muted(stringResource(R.string.voice_truncated), Modifier.padding(top = 10.dp))

    ButtonRow {
        if (s.english.isNotEmpty()) PillButton(stringResource(R.string.voice_copy_english), Modifier.weight(1f).fillMaxHeight()) { model.copy("English", s.english) }
        if (s.urdu.isNotEmpty()) PillButton(stringResource(R.string.voice_copy_urdu), Modifier.weight(1f).fillMaxHeight()) { model.copy("Urdu", s.urdu) }
        if (!speak && s.roman.isNotEmpty()) PillButton(stringResource(R.string.voice_copy_roman), Modifier.weight(1f).fillMaxHeight()) { model.copy("Roman Urdu", s.roman) }
    }
    if (speak && s.urdu.isNotEmpty()) {
        when (val a = model.audio) {
            VoiceModel.AudioState.None -> {}
            VoiceModel.AudioState.Making -> Working(stringResource(R.string.voice_making_audio))
            is VoiceModel.AudioState.Ready -> ButtonRow {
                PillButton(stringResource(R.string.voice_play), Modifier.weight(1f).fillMaxHeight()) { model.play(s.urdu) }
                PillButton(stringResource(R.string.voice_share_audio), Modifier.weight(1f).fillMaxHeight(), filled = true, icon = R.drawable.kb_send) { onShareAudio(a.file) }
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
