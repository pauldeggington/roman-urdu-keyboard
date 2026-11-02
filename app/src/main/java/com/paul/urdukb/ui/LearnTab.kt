package com.paul.urdukb.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paul.urdukb.R
import com.paul.urdukb.SetupActivity

/** The Learn tab: how the keyboard is used, with small drawn pictures, tips and the questions people ask. */
fun LazyListScope.learnTab(main: MainModel) {
    item { TabHeader(stringResource(R.string.tab_learn), stringResource(R.string.learn_title), stringResource(R.string.learn_lead)) }
    item {
        AppCard {
            CardTitle(stringResource(R.string.learn_steps_title))
            ToolbarPicture(Modifier.padding(top = 14.dp))
            Numbered(1, stringResource(R.string.learn_step1))
            Numbered(2, stringResource(R.string.learn_step2))
            Numbered(3, stringResource(R.string.learn_step3))
        }
    }
    item {
        AppCard {
            CardTitle(stringResource(R.string.learn_tone_title))
            Muted(stringResource(R.string.learn_tone_body), Modifier.padding(top = 6.dp))
            TonePicture(Modifier.padding(top = 14.dp))
            Muted(stringResource(R.string.learn_tone_note), Modifier.padding(top = 12.dp))
        }
    }
    item {
        AppCard {
            CardTitle(stringResource(R.string.learn_back_title))
            Muted(stringResource(R.string.learn_back_body), Modifier.padding(top = 6.dp))
            Numbered(1, stringResource(R.string.learn_back_step1))
            Numbered(2, stringResource(R.string.learn_back_step2))
            Numbered(3, stringResource(R.string.learn_back_step3))
        }
    }
    item {
        AppCard {
            CardTitle(stringResource(R.string.learn_voice_title))
            FeatureRow(R.drawable.kb_mic, stringResource(R.string.voice_title_speak), stringResource(R.string.learn_voice_speak))
            FeatureRow(R.drawable.kb_send, stringResource(R.string.voice_title_share), stringResource(R.string.learn_voice_note))
            PillButton(stringResource(R.string.learn_open_voice), Modifier.fillMaxWidth().padding(top = 8.dp), icon = R.drawable.kb_mic) { main.tab = Tab.VOICE }
        }
    }
    item {
        AppCard {
            CardTitle(stringResource(R.string.learn_tips_title))
            FeatureRow(R.drawable.kb_undo, stringResource(R.string.tips_undo_title), stringResource(R.string.tips_undo_sub))
            FeatureRow(R.drawable.ic_setup_person, stringResource(R.string.learn_tip_glossary_title), stringResource(R.string.learn_tip_glossary_sub))
            FeatureRow(R.drawable.ic_setup_touch, stringResource(R.string.learn_tip_glide_title), stringResource(R.string.learn_tip_glide_sub))
            FeatureRow(R.drawable.kb_paste, stringResource(R.string.learn_tip_clipboard_title), stringResource(R.string.learn_tip_clipboard_sub))
            FeatureRow(R.drawable.kb_emoji, stringResource(R.string.learn_tip_gif_title), stringResource(R.string.learn_tip_gif_sub))
        }
    }
    item {
        AppCard {
            CardTitle(stringResource(R.string.learn_faq_title))
            Faq(stringResource(R.string.learn_faq1_q), stringResource(R.string.learn_faq1_a))
            Faq(stringResource(R.string.learn_faq2_q), stringResource(R.string.learn_faq2_a))
            Faq(stringResource(R.string.learn_faq3_q), stringResource(R.string.learn_faq3_a))
            Faq(stringResource(R.string.learn_faq4_q), stringResource(R.string.learn_faq4_a))
            Faq(stringResource(R.string.learn_faq5_q), stringResource(R.string.learn_faq5_a))
        }
    }
    item {
        val ctx = LocalContext.current
        AppCard {
            CardTitle(stringResource(R.string.learn_more_title))
            ButtonRow {
                PillButton(stringResource(R.string.kb_run_setup), Modifier.weight(1f).fillMaxHeight(), filled = true) {
                    SetupActivity.reset(ctx)
                    ctx.startActivity(Intent(ctx, SetupActivity::class.java))
                }
                PillButton(stringResource(R.string.learn_website), Modifier.weight(1f).fillMaxHeight()) {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://pauldeggington.github.io/roman-urdu-keyboard/"))) }
                }
            }
            PillButton(stringResource(R.string.learn_privacy), Modifier.fillMaxWidth().padding(top = 8.dp)) {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://pauldeggington.github.io/roman-urdu-keyboard/privacy.html"))) }
            }
        }
    }
}

/** A question that opens to its answer, with the website's plus that turns into a minus. */
@Composable
private fun Faq(q: String, a: String) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(16.dp)).background(Brand.raised).border(1.dp, Brand.line, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button) { open = !open }.padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(q, Modifier.weight(1f), color = Brand.text, fontSize = Type.body, fontWeight = FontWeight.Medium, lineHeight = Type.body * 1.3f)
            Box(Modifier.padding(start = 12.dp).size(18.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                Box(Modifier.width(14.dp).height(1.5.dp).background(Brand.muted))
                Box(Modifier.width(14.dp).height(1.5.dp).rotate(if (open) 0f else 90f).background(Brand.muted))
            }
        }
        AnimatedVisibility(open) { Muted(a, Modifier.padding(top = 10.dp), size = Type.body) }
    }
}

/** The keyboard's toolbar, drawn small: the Translate key lit gold between the tone and direction words. */
@Composable
fun ToolbarPicture(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brand.page).border(1.dp, Brand.line, RoundedCornerShape(16.dp)).padding(12.dp).clearAndSetSemantics {}) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            KeyDot(R.drawable.kb_grid)
            KeyDot(R.drawable.kb_emoji)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(36.dp).clip(CircleShape).background(Brand.gold2), contentAlignment = Alignment.Center) { TintedIcon(R.drawable.kb_translate, Brand.onGold, 18.dp) }
            MiniChip("Casual", Modifier.padding(start = 8.dp))
            MiniChip("Auto", Modifier.padding(start = 6.dp))
            Box(Modifier.padding(start = 8.dp).size(36.dp).clip(CircleShape).background(Brand.raised), contentAlignment = Alignment.Center) { TintedIcon(R.drawable.kb_mic, Brand.text, 18.dp) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (k in listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")) {
                Box(Modifier.weight(1f).height(30.dp), contentAlignment = Alignment.Center) { Text(k, color = Brand.text, fontSize = 15.sp) }
            }
        }
    }
}

@Composable
private fun KeyDot(res: Int) {
    Box(Modifier.padding(end = 6.dp).size(36.dp), contentAlignment = Alignment.Center) { TintedIcon(res, Brand.muted, 18.dp) }
}

@Composable
private fun MiniChip(text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(50)).background(Brand.raised).padding(horizontal = 10.dp, vertical = 7.dp)) {
        Text(text, color = Brand.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** The five tones as chips, with one example line under them. */
@Composable
private fun TonePicture(modifier: Modifier = Modifier) {
    val tones = listOf("Casual", "Formal", "Poetic", "Funny", "Respectful")
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brand.page).border(1.dp, Brand.line, RoundedCornerShape(16.dp)).padding(12.dp).clearAndSetSemantics {}) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tones.take(3).forEachIndexed { i, t ->
                Box(Modifier.weight(1f).heightIn(min = 32.dp).clip(RoundedCornerShape(50)).background(if (i == 0) Brand.pillBg else Brand.raised), contentAlignment = Alignment.Center) {
                    Text(t, color = if (i == 0) Brand.pillText else Brand.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tones.drop(3).forEach { t ->
                Box(Modifier.weight(1f).heightIn(min = 32.dp).clip(RoundedCornerShape(50)).background(Brand.raised), contentAlignment = Alignment.Center) {
                    Text(t, color = Brand.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.weight(1f))
        }
        Text("I'm really tired today", Modifier.padding(top = 14.dp), color = Brand.muted, fontSize = 13.sp)
        Text("Aaj main bohat thak gaya hoon", Modifier.padding(top = 4.dp), color = Brand.text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}
