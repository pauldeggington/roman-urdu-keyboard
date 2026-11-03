package com.paul.urdukb

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.paul.urdukb.ui.Brand
import com.paul.urdukb.ui.Muted
import com.paul.urdukb.ui.PillButton
import com.paul.urdukb.ui.RomanUrduTheme
import com.paul.urdukb.ui.SectionLabel
import com.paul.urdukb.ui.Type
import com.paul.urdukb.ui.Working
import java.io.ByteArrayOutputStream

/**
 * PROTOTYPE. A picture with Urdu text shared to this app (Gallery, WhatsApp, Files) is read and translated by Gemma 4 on
 * the phone. It shows the picture, what the model read, and the English. It needs Gemma 4: the built-in Gemma 3 cannot see.
 * This is for judging how well the model reads Urdu in pictures; the screens are plain on purpose.
 */
class ImageActivity : ComponentActivity() {

    private var state by mutableStateOf<State>(State.Loading)
    private var preview by mutableStateOf<Bitmap?>(null)
    private var seconds by mutableStateOf(0L)

    sealed interface State {
        data object Loading : State
        data object NeedsGemma4 : State
        data class Done(val original: String, val english: String, val raw: String) : State
        data class Failed(val message: String) : State
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val uri = if (intent?.action == Intent.ACTION_SEND) intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri else null
        if (uri == null) { finish(); return }
        setContent { RomanUrduTheme { Sheet() } }
        if (Prefs.modelChoice(this) != ModelChoice.GEMMA4 || !LlmEngine.modelExists(this)) {
            state = State.NeedsGemma4
            return
        }
        Thread({
            try {
                val bmp = decode(uri)
                runOnUiThread { preview = bmp }
                val t0 = System.currentTimeMillis()
                val reply = LlmEngine.readImage(this, jpeg(bmp), PROMPT)
                seconds = (System.currentTimeMillis() - t0) / 1000
                runOnUiThread { state = parse(reply) }
            } catch (t: Throwable) {
                runOnUiThread { state = State.Failed(t.message ?: t.toString()) }
            }
        }, "image-read").start()
    }

    /** Opens the shared picture and shrinks it so the longest side is at most 1024 px (smaller means faster and lighter on memory). */
    private fun decode(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IllegalStateException("Could not open that picture.")
        val scale = 1024f / maxOf(bmp.width, bmp.height)
        return if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
    }

    private fun jpeg(bmp: Bitmap): ByteArray = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()

    private fun parse(reply: String): State {
        val e = reply.indexOf("ENGLISH:")
        if (e < 0) return State.Done("", reply, reply)
        val original = reply.substring(0, e).replace("URDU:", "").trim()
        return State.Done(original, reply.substring(e + "ENGLISH:".length).trim(), reply)
    }

    @Composable
    private fun Sheet() {
        Box(
            Modifier.fillMaxSize().background(Color(0x99000000))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
        ) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 720.dp)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Brand.card)
                    .border(1.dp, Brand.line, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 22.dp),
            ) {
                SectionLabel("Picture translator (prototype)")
                preview?.let {
                    Image(
                        it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(16.dp)),
                    )
                }
                when (val s = state) {
                    State.Loading -> Working("Reading the picture on your phone. The first one can take a minute.")
                    State.NeedsGemma4 -> Muted("Reading pictures needs Gemma 4. Add it in the app's Settings, then share the picture again.", Modifier.padding(top = 12.dp))
                    is State.Failed -> Muted("Could not read it: ${s.message}", Modifier.padding(top = 12.dp))
                    is State.Done -> {
                        if (s.original.isNotBlank()) {
                            SectionLabel("What it read", Modifier.padding(top = 16.dp))
                            SelectionContainer { Text(s.original, Modifier.padding(top = 6.dp), color = Brand.text, fontSize = Type.bodyLarge) }
                        }
                        SectionLabel("English", Modifier.padding(top = 16.dp))
                        SelectionContainer { Text(s.english, Modifier.padding(top = 6.dp), color = Brand.text, fontSize = Type.bodyLarge) }
                        Muted("Took $seconds s on this phone. Check anything important.", Modifier.padding(top = 10.dp))
                    }
                }
                PillButton("Close", Modifier.padding(top = 18.dp)) { finish() }
            }
        }
    }

    private companion object {
        const val PROMPT = "This picture may contain Urdu text (Nastaliq or Naskh script, printed or handwritten) or Roman Urdu. " +
            "First copy out all the text you can read, exactly as written, under the heading URDU:. " +
            "Then translate it into natural English under the heading ENGLISH:. " +
            "If the picture has no readable text, write URDU: none and ENGLISH: briefly describe the picture. " +
            "Do not add anything else."
    }
}
