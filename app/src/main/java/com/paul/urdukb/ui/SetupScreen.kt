package com.paul.urdukb.ui

import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import com.paul.urdukb.KeyboardLook
import com.paul.urdukb.ModelChoice
import com.paul.urdukb.Prefs
import com.paul.urdukb.R
import com.paul.urdukb.RomanUrdu
import com.paul.urdukb.SetupActivity.Step
import com.paul.urdukb.Speaker
import com.paul.urdukb.ime.KbSettings

/** The first-run guide: one task per screen, a progress bar on top, one big button below. What it does is in [SetupModel]. */
@Composable
fun SetupScreen(model: SetupModel, pickModelFile: () -> Unit, recreateScreen: () -> Unit, closeGuide: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val step = model.step
    val live = model.live
    val scroll = rememberScrollState()
    LaunchedEffect(step) { scroll.scrollTo(0) }
    BackHandler(enabled = step.ordinal > 0 || model.problem != null) { if (!model.back()) closeGuide() }

    Column(
        Modifier.fillMaxSize().background(cs.background).windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)).imePadding(),
    ) {
        TopBar(model)
        if (live.chip.isNotEmpty()) {
            Text(
                live.chip,
                Modifier.padding(start = 24.dp, top = 4.dp).minimumInteractiveComponentSize().clip(RoundedCornerShape(50)).background(cs.secondaryContainer)
                    .clickable(role = Role.Button) { model.show(Step.MODEL) }.padding(horizontal = 12.dp, vertical = 6.dp),
                color = cs.onSecondaryContainer, fontSize = Type.caption,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val problem = model.problem
            if (problem != null) {
                Column(Modifier.verticalScroll(scroll).padding(horizontal = 24.dp, vertical = 12.dp)) {
                    Heading(stringResource(R.string.setup_problem_title))
                    Body(stringResource(R.string.setup_problem_body))
                    SelectionContainer {
                        Text(problem, Modifier.padding(top = 14.dp), color = cs.onSurfaceVariant, fontSize = Type.caption, fontFamily = FontFamily.Monospace)
                    }
                }
            } else {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = { (fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 12 }) togetherWith (fadeOut(tween(120)) + slideOutHorizontally(tween(120)) { -it / 12 }) },
                    label = "step",
                ) { s ->
                    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 24.dp, vertical = 12.dp)) {
                        when (s) {
                            Step.WELCOME -> Welcome()
                            Step.MODEL -> ModelStep(model, pickModelFile)
                            Step.ENABLE -> EnableStep(model)
                            Step.SWITCH -> SwitchStep(model)
                            Step.PERSONAL -> PersonalStep(recreateScreen)
                            Step.PRACTICE -> PracticeStep(model)
                            Step.DONE -> DoneStep()
                        }
                    }
                }
            }
        }
        BottomBar(model, pickModelFile)
    }
}

// ------------------------------------------------------------------ top and bottom bars

@Composable
private fun TopBar(model: SetupModel) {
    val cs = MaterialTheme.colorScheme
    val step = model.step
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val backVisible = step.ordinal > 0 && step != Step.DONE
        val back = stringResource(R.string.a11y_back)
        if (backVisible) {
            Box(
                Modifier.size(48.dp).clickable(role = Role.Button, onClickLabel = back) { model.back() }.semantics { contentDescription = back },
                contentAlignment = Alignment.Center,
            ) {
                // the arrow points the way "back" is: right to left in English, left to right in Urdu
                Text(
                    if (LocalLayoutDirection.current == LayoutDirection.Rtl) "›" else "‹",
                    Modifier.padding(bottom = 4.dp).clearAndSetSemantics {},
                    color = cs.primary, fontSize = Type.display, fontWeight = FontWeight.Medium,
                )
            }
        } else Spacer(Modifier.size(48.dp))
        val n = Step.entries.size
        val stepText = stringResource(R.string.a11y_step, step.ordinal + 1, n)
        Row(
            Modifier.weight(1f).height(6.dp).semantics(mergeDescendants = true) { contentDescription = stepText },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (i in 0 until n) Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(3.dp)).background(if (i <= step.ordinal) cs.primary else cs.secondaryContainer))
        }
        Text(
            stringResource(R.string.setup_skip),
            Modifier.alpha(if (step == Step.DONE) 0f else 1f).minimumInteractiveComponentSize().clickable(enabled = step != Step.DONE, role = Role.Button) { model.finish(openApp = true) }.padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            color = cs.primary, fontSize = Type.body, fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun BottomBar(model: SetupModel, pickModelFile: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val step = model.step
    val live = model.live
    val ctx = LocalContext.current
    var secondaryLabel: String? = null
    var secondaryClick: () -> Unit = {}
    val primaryLabel: String
    val primaryClick: () -> Unit
    var primaryEnabled = true
    when {
        model.problem != null -> {
            primaryLabel = stringResource(R.string.setup_skip_step); primaryClick = { model.skipProblem() }
            secondaryLabel = stringResource(R.string.setup_copy_details); secondaryClick = { model.copyProblem() }
        }
        step == Step.WELCOME -> { primaryLabel = stringResource(R.string.setup_get_started); primaryClick = { model.next() } }
        step == Step.MODEL -> {
            primaryLabel = live.modelPrimary.ifEmpty { stringResource(R.string.setup_download_now) }
            primaryClick = { model.onModelPrimary() }
            if (live.secondary != SetupModel.Secondary.NONE) {
                secondaryLabel = live.secondaryLabel
                secondaryClick = if (live.secondary == SetupModel.Secondary.CANCEL_DOWNLOAD) ({ model.cancelDownload() }) else pickModelFile
            }
        }
        step == Step.ENABLE -> {
            primaryLabel = stringResource(if (live.keyboardOn) R.string.next else R.string.setup_open_settings)
            primaryClick = { if (live.keyboardOn) model.next() else model.openKeyboardSettings() }
        }
        step == Step.SWITCH -> {
            primaryLabel = stringResource(if (live.keyboardCurrent) R.string.next else R.string.setup_choose_keyboard)
            primaryClick = { if (live.keyboardCurrent) model.next() else model.showPicker() }
        }
        step == Step.PERSONAL -> { primaryLabel = stringResource(R.string.next); primaryClick = { model.next() } }
        step == Step.PRACTICE -> {
            primaryLabel = stringResource(R.string.next); primaryClick = { model.next() }
            secondaryLabel = stringResource(R.string.setup_skip_for_now); secondaryClick = { model.next() }
        }
        else -> { primaryLabel = stringResource(R.string.setup_finish); primaryClick = { model.finish(openApp = true) } }
    }
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = primaryClick, enabled = primaryEnabled,
            modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = cs.primary, contentColor = cs.onPrimary),
        ) { Text(primaryLabel, fontSize = Type.bodyLarge, fontWeight = FontWeight.Medium) }
        if (secondaryLabel != null) {
            Text(
                secondaryLabel,
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { secondaryClick() }.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                color = cs.primary, fontSize = Type.body, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
            )
        }
    }
}

// ------------------------------------------------------------------ steps

@Composable
private fun Welcome() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AndroidView(
            factory = { c -> ImageView(c).apply { setImageResource(R.mipmap.ic_launcher) } },
            modifier = Modifier.padding(top = 24.dp).size(96.dp).clip(RoundedCornerShape(29.dp)),
        )
        Heading(stringResource(R.string.app_name), Modifier.padding(top = 4.dp), center = true)
        Body(stringResource(R.string.setup_welcome_body), Modifier.padding(bottom = 20.dp), center = true)
    }
    Feature(R.drawable.ic_setup_translate, stringResource(R.string.setup_feat_translate), stringResource(R.string.setup_feat_translate_sub))
    Feature(R.drawable.ic_setup_offline, stringResource(R.string.setup_feat_private), stringResource(R.string.setup_feat_private_sub))
    Feature(R.drawable.ic_setup_keyboard, stringResource(R.string.setup_feat_gboard), stringResource(R.string.setup_feat_gboard_sub))
    Small(stringResource(R.string.setup_welcome_note), Modifier.padding(top = 20.dp).fillMaxWidth(), center = true)
}

@Composable
private fun ModelStep(model: SetupModel, pickModelFile: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val live = model.live
    val ctx = LocalContext.current
    val current = Prefs.modelChoice(ctx)
    Hero(R.drawable.ic_setup_download)
    Heading(stringResource(R.string.setup_model_title))
    Body(stringResource(R.string.setup_model_body))

    Column(Modifier.padding(top = 16.dp)) {
        ModelChoice.entries.forEach { m ->
            val selected = m == current
            Column(
                Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(20.dp))
                    .background(if (selected) cs.secondaryContainer else cs.surface)
                    .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) cs.primary else cs.secondaryContainer), RoundedCornerShape(20.dp))
                    .selectable(selected = selected, role = Role.RadioButton) { model.chooseModel(m) }.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text((if (selected) "● " else "○ ") + m.tagline, Modifier.weight(1f), color = cs.onSurface, fontSize = Type.body, fontWeight = FontWeight.Medium)
                    Small(m.sizeText)
                }
                Small(
                    stringResource(if (m == ModelChoice.GEMMA4) R.string.setup_gemma4_sub else R.string.setup_gemma3_sub),
                    Modifier.padding(top = 4.dp),
                )
            }
        }
        if (live.modelNeedsToken) TokenHelp(model, current)
    }

    val (okColor, errColor) = statusColors()
    Card(Modifier.padding(top = 20.dp)) {
        Text(
            live.modelLine, color = when (live.modelTone) { SetupModel.Tone.OK -> okColor; SetupModel.Tone.ERROR -> errColor; else -> cs.onSurface },
            fontSize = Type.body, fontWeight = FontWeight.Medium,
        )
        live.modelBar?.let { LinearProgressIndicator(progress = { it }, Modifier.fillMaxWidth().padding(top = 10.dp), color = cs.primary, trackColor = cs.secondaryContainer) }
        Small(live.modelNote, Modifier.padding(top = 6.dp))
    }

    // mobile data choice, only when on a metered network
    if (live.metered) {
        var wifiOnly by remember { mutableStateOf(model.wifiOnly) }
        SwitchRow(stringResource(R.string.setup_mobile_data), "", wifiOnly) { wifiOnly = it; model.wifiOnly = it }
    }
}

@Composable
private fun TokenHelp(model: SetupModel, m: ModelChoice) {
    Card(Modifier.padding(top = 10.dp)) {
        Small(stringResource(R.string.setup_token_intro))
        Numbered(1, stringResource(R.string.setup_token_step1))
        Numbered(2, stringResource(R.string.setup_token_step2))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(stringResource(R.string.setup_model_page), Modifier.weight(1f)) { model.openUrl(m.pageUrl) }
            PillButton(stringResource(R.string.setup_make_token), Modifier.weight(1f)) { model.openUrl("https://huggingface.co/settings/tokens") }
        }
        TextBox(model.token, { model.saveToken(it) }, stringResource(R.string.setup_token_hint), modifier = Modifier.padding(top = 8.dp), password = true)
    }
}

@Composable
private fun EnableStep(model: SetupModel) {
    Hero(R.drawable.ic_setup_keyboard)
    Heading(stringResource(R.string.setup_enable_title))
    Body(stringResource(R.string.setup_enable_body))
    Numbered(1, stringResource(R.string.setup_enable_1))
    Numbered(2, stringResource(R.string.setup_enable_2))
    Numbered(3, stringResource(R.string.setup_enable_3))
    ToggleIllustration()
    Card(Modifier.padding(top = 16.dp)) {
        Row {
            Icon(R.drawable.ic_setup_lock, MaterialTheme.colorScheme.primary, 22.dp)
            Small(stringResource(R.string.setup_enable_note), Modifier.padding(start = 12.dp))
        }
    }
    if (model.live.keyboardOn) DoneBanner(stringResource(R.string.setup_enable_done))
}

@Composable
private fun SwitchStep(model: SetupModel) {
    Hero(R.drawable.ic_setup_touch)
    Heading(stringResource(R.string.setup_switch_title))
    Body(stringResource(R.string.setup_switch_body))
    Small(stringResource(R.string.setup_switch_note), Modifier.padding(top = 10.dp))
    if (model.live.keyboardCurrent) DoneBanner(stringResource(R.string.setup_switch_done))
}

@Composable
private fun PersonalStep(recreateScreen: () -> Unit) {
    val ctx = LocalContext.current
    var speaker by remember { mutableStateOf(Speaker.get(ctx)) }
    var formal by remember { mutableStateOf(RomanUrdu.isFormal(ctx)) }
    Hero(R.drawable.ic_setup_person)
    Heading(stringResource(R.string.setup_personal_title))
    Body(stringResource(R.string.setup_personal_body))

    Label(stringResource(R.string.style_i_am))
    Segmented(listOf(stringResource(R.string.male) to Speaker.MALE, stringResource(R.string.female) to Speaker.FEMALE), speaker) { speaker = it; Speaker.set(ctx, it) }
    Small(stringResource(R.string.setup_personal_i_am_help), Modifier.padding(top = 6.dp))

    Label(stringResource(R.string.setup_how_talk))
    Segmented(listOf(stringResource(R.string.tone_casual) to false, stringResource(R.string.tone_formal) to true), formal) {
        formal = it
        KbSettings.of(ctx).formalTone = it
    }
    Small(stringResource(R.string.setup_personal_tone_help), Modifier.padding(top = 6.dp))

    Label(stringResource(R.string.setup_theme))
    Segmented(
        listOf(stringResource(R.string.mode_follow) to KeyboardLook.MODE_FOLLOW, stringResource(R.string.mode_light) to KeyboardLook.MODE_LIGHT, stringResource(R.string.mode_dark) to KeyboardLook.MODE_DARK),
        KeyboardLook.mode(ctx),
    ) { KeyboardLook.apply(ctx, KeyboardLook.swatchId(ctx), it); recreateScreen() }

    Label(stringResource(R.string.setup_colour))
    val current = KeyboardLook.swatchId(ctx)
    val outline = MaterialTheme.colorScheme.onSurface
    KeyboardLook.SWATCHES.take(10).chunked(5).forEach { rowSw ->
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            rowSw.forEach { sw ->
                val selected = sw.id == current
                Box(
                    Modifier.weight(1f).height(52.dp)
                        .selectable(selected = selected, role = Role.RadioButton) { KeyboardLook.apply(ctx, sw.id, KeyboardLook.mode(ctx)); recreateScreen() }
                        .semantics { contentDescription = sw.name },
                    contentAlignment = Alignment.Center,
                ) {
                    val fill = if (sw.id == KeyboardLook.WALLPAPER) Brush.linearGradient(listOf(Color(0xFFC27BA0), Color(0xFF6C7CE8), Color(0xFF2FB5A8))) else Brush.linearGradient(listOf(Color(sw.color), Color(sw.color)))
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(fill).then(if (selected) Modifier.border(3.dp, outline, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) { if (selected) Text("✓", Modifier.clearAndSetSemantics {}, color = Color.White, fontSize = Type.bodyLarge) }
                }
            }
        }
    }
    Small(stringResource(R.string.setup_colour_note), Modifier.padding(top = 6.dp))
}

@Composable
private fun PracticeStep(model: SetupModel) {
    val cs = MaterialTheme.colorScheme
    val live = model.live
    val (okColor, _) = statusColors()
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus(); keyboard?.show() } }
    Heading(stringResource(R.string.setup_practice_title))
    Body(stringResource(R.string.setup_practice_body))
    Card(Modifier.padding(top = 12.dp)) {
        Feature(R.drawable.kb_translate, stringResource(R.string.translate), stringResource(R.string.setup_tip_translate))
        Feature(R.drawable.ic_setup_mood, stringResource(R.string.setup_tip_tone_title), stringResource(R.string.setup_tip_tone))
        Feature(R.drawable.ic_setup_swap, stringResource(R.string.dir_auto), stringResource(R.string.setup_tip_auto))
    }
    TextBox(
        text, { text = it }, stringResource(R.string.setup_practice_hint),
        modifier = Modifier.padding(top = 16.dp).focusRequester(focus), minLines = 3, sentences = true, fontSize = Type.bodyLarge,
    )
    Text(live.practiceText, Modifier.padding(top = 8.dp), color = if (live.practiceDone) okColor else cs.onSurfaceVariant, fontSize = Type.caption, lineHeight = Type.caption * 1.35f)
}

@Composable
private fun DoneStep() {
    Hero(R.drawable.ic_setup_celebration)
    Heading(stringResource(R.string.setup_done_title))
    Body(stringResource(R.string.setup_done_body))
    Card(Modifier.padding(top = 16.dp)) {
        Feature(R.drawable.ic_setup_translate, stringResource(R.string.translate), stringResource(R.string.setup_done_tip_translate))
        Feature(R.drawable.ic_setup_person, stringResource(R.string.setup_done_tip_wrong), stringResource(R.string.setup_done_tip_wrong_sub))
        Feature(R.drawable.ic_setup_palette, stringResource(R.string.setup_done_tip_colours), stringResource(R.string.setup_done_tip_colours_sub))
    }
}

// ------------------------------------------------------------------ pieces

@Composable
private fun Heading(text: String, modifier: Modifier = Modifier, center: Boolean = false) {
    Text(
        text, modifier.fillMaxWidth().padding(top = 16.dp).semantics { heading() }, color = MaterialTheme.colorScheme.onBackground,
        fontSize = Type.headline, lineHeight = Type.headline * 1.25f, fontWeight = FontWeight.Medium, textAlign = if (center) TextAlign.Center else TextAlign.Start,
    )
}

@Composable
private fun Body(text: String, modifier: Modifier = Modifier, center: Boolean = false) {
    Text(
        text, modifier.fillMaxWidth().padding(top = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = Type.bodyLarge, lineHeight = Type.bodyLarge * 1.35f, textAlign = if (center) TextAlign.Center else TextAlign.Start,
    )
}

@Composable
private fun Small(text: String, modifier: Modifier = Modifier, center: Boolean = false) {
    Text(
        text, modifier, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = Type.caption, lineHeight = Type.caption * 1.35f,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
    )
}

@Composable
private fun Label(text: String) {
    Text(text, Modifier.padding(top = 22.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.onBackground, fontSize = Type.body, fontWeight = FontWeight.Medium)
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 18.dp, vertical = 16.dp)) { content() }
}

@Composable
private fun Icon(res: Int, tint: Color, size: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.Image(painterResource(res), null, Modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

@Composable
private fun Hero(res: Int) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.padding(top = 16.dp).size(96.dp).clip(RoundedCornerShape(48.dp)).background(cs.secondaryContainer), contentAlignment = Alignment.Center) {
        Icon(res, cs.onSecondaryContainer, 48.dp)
    }
}

@Composable
private fun Feature(res: Int, title: String, sub: String) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(cs.secondaryContainer), contentAlignment = Alignment.Center) { Icon(res, cs.onSecondaryContainer, 24.dp) }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, color = cs.onSurface, fontSize = Type.bodyLarge, fontWeight = FontWeight.Medium)
            Small(sub, Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun Numbered(n: Int, text: String) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(cs.primary), contentAlignment = Alignment.Center) {
            Text("$n", color = cs.onPrimary, fontSize = Type.body, fontWeight = FontWeight.Bold)
        }
        Text(text, Modifier.weight(1f).padding(start = 14.dp), color = cs.onSurface, fontSize = Type.bodyLarge, lineHeight = Type.bodyLarge * 1.3f)
    }
}

@Composable
private fun DoneBanner(text: String) {
    val cs = MaterialTheme.colorScheme
    val (okColor, _) = statusColors()
    Row(
        Modifier.padding(top = 18.dp).clip(RoundedCornerShape(50)).background(cs.secondaryContainer).padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(R.drawable.ic_setup_check, okColor, 22.dp)
        Text(text, Modifier.padding(start = 10.dp), color = cs.onSurface, fontSize = Type.body)
    }
}

/** A little settings row whose switch flips on and off, showing what to tap. */
@Composable
private fun ToggleIllustration() {
    val cs = MaterialTheme.colorScheme
    val t = rememberInfiniteTransition(label = "toggle")
    val f by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "f")
    val on = f in 0.35f..0.9f
    val x = when {
        f < 0.35f -> 0f
        f < 0.45f -> (f - 0.35f) / 0.1f
        f < 0.9f -> 1f
        else -> 1f - (f - 0.9f) / 0.1f
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp).clip(RoundedCornerShape(20.dp)).background(cs.surface).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.app_name), Modifier.weight(1f), color = cs.onSurface, fontSize = Type.body)
        Box(Modifier.width(52.dp).height(32.dp).clearAndSetSemantics {}.clip(RoundedCornerShape(16.dp)).background(if (on) cs.primary else cs.secondaryContainer)) {
            Box(
                Modifier.padding(start = 5.dp).align(Alignment.CenterStart).offset(x = (20f * x).dp).size(22.dp).clip(CircleShape)
                    .background(if (on) cs.onPrimary else cs.onSurfaceVariant),
            )
        }
    }
}
