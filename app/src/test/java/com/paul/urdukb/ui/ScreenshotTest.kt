package com.paul.urdukb.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.edit
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.lifecycle.SavedStateHandle
import com.paul.urdukb.SetupActivity
import androidx.test.core.app.ApplicationProvider
import com.paul.urdukb.KeyboardLook
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/**
 * Draws screens to PNG on the JVM, no emulator. Each picture is written to build/screenshots and compared with the saved
 * copy in src/test/snapshots; a change that moves pixels fails the test, and the new picture is there to look at.
 * To accept a change on purpose, copy the new picture over the saved one.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h860dp-xxhdpi")
class ScreenshotTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext<Application>()

    // the test activity is not in the app's manifest (that would ship it), so it is registered before the rule launches it
    private val register = object : ExternalResource() {
        override fun before() {
            shadowOf(ctx.packageManager).addActivityIfNotPresent(ComponentName(ctx.packageName, ComponentActivity::class.java.name))
        }
    }
    private val rule = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(register).around(rule)

    private fun look(mode: String, swatch: String) {
        ctx.getSharedPreferences("look", Context.MODE_PRIVATE).edit(commit = true) {
            putString("mode", mode)
            putString("swatch", swatch)
        }
    }

    private fun shoot(name: String, rtl: Boolean = false, fontScale: Float = 1f, content: @androidx.compose.runtime.Composable () -> Unit) {
        try {
            rule.runOnUiThread { rule.activity.actionBar?.hide() }
            rule.setContent {
                val d = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(d.density, fontScale),
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    RomanUrduTheme(urdu = rtl) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
                    }
                }
            }
            rule.waitForIdle()
            check(name, draw())
        } catch (e: Throwable) {
            println("SCREENSHOTFAIL $name: ${e.javaClass.simpleName}: ${e.message?.take(400)?.replace('\n', ' ')}")
            throw e
        }
    }

    /** The whole window drawn into a bitmap (Robolectric's native graphics draw it for real). */
    private fun draw(): Bitmap {
        lateinit var bmp: Bitmap
        rule.runOnUiThread {
            val v = rule.activity.window.decorView
            bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp))
        }
        return bmp
    }

    private fun check(name: String, bmp: Bitmap) {
        val out = File("build/screenshots").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val golden = File("src/test/snapshots/$name.png")
        if (!golden.exists()) {
            println("SNAPSHOT new: $name (no saved copy yet)")
            return
        }
        val saved = android.graphics.BitmapFactory.decodeFile(golden.path)
        assertTrue("$name: size changed ${saved.width}x${saved.height} -> ${bmp.width}x${bmp.height}", saved.width == bmp.width && saved.height == bmp.height)
        var off = 0
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
            val a = saved.getPixel(x, y)
            val b = bmp.getPixel(x, y)
            val d = abs((a shr 16 and 255) - (b shr 16 and 255)) + abs((a shr 8 and 255) - (b shr 8 and 255)) + abs((a and 255) - (b and 255))
            if (d > 24) off++
        }
        val share = off.toDouble() / (bmp.width * bmp.height)
        println("SNAPSHOT $name: ${"%.3f".format(share * 100)}% of pixels differ")
        assertTrue("$name: ${"%.2f".format(share * 100)}% of pixels changed (limit 0.5%)", share <= 0.005)
    }

    @Test fun galleryLight() { look("light", "blue"); shoot("gallery-light") { ComponentGallery() } }
    @Test fun galleryDark() { look("dark", "blue"); shoot("gallery-dark") { ComponentGallery() } }
    @Test fun galleryLemonLight() { look("light", "lemon"); shoot("gallery-lemon-light") { ComponentGallery() } }
    @Test fun galleryUrduRtl() { look("light", "blue"); shoot("gallery-urdu-rtl", rtl = true) { ComponentGallery(urduGallery) } }
    @Test fun galleryLargeText() { look("light", "blue"); shoot("gallery-large-text", fontScale = 1.6f) { ComponentGallery() } }

    // ---- the real setup guide, driven by its real ViewModel

    private fun setupModel() = SetupModel(ctx as Application, SavedStateHandle())

    @Test fun setupGuideLight() { look("light", "blue"); val m = setupModel(); shoot("setup-welcome-light") { SetupScreen(m, {}, {}, {}) } }
    @Test fun setupGuideUrdu() { look("light", "blue"); val m = setupModel(); shoot("setup-welcome-urdu-rtl", rtl = true) { SetupScreen(m, {}, {}, {}) } }
    @Test fun setupGuideLargeText() { look("light", "blue"); val m = setupModel(); shoot("setup-welcome-large-text", fontScale = 1.6f) { SetupScreen(m, {}, {}, {}) } }

    /** Every tappable thing is at least 48dp each way and says what it is, on the first three steps of the guide. */
    @Test fun setupGuideIsAccessible() {
        look("light", "blue")
        val m = setupModel()
        rule.setContent { RomanUrduTheme { SetupScreen(m, {}, {}, {}) } }
        val problems = ArrayList<String>()
        for (step in listOf(SetupActivity.Step.WELCOME, SetupActivity.Step.MODEL, SetupActivity.Step.PERSONAL)) {
            rule.runOnUiThread { m.show(step) }
            rule.waitForIdle()
            val clickable = rule.onAllNodes(hasClickAction() or isToggleable() or isSelectable())
            clickable.fetchSemanticsNodes().forEachIndexed { i, node ->
                val cfg = node.config
                val named = cfg.contains(SemanticsProperties.ContentDescription) || cfg.contains(SemanticsProperties.Text)
                if (!named) problems += "$step #$i has no label"
                val density = ctx.resources.displayMetrics.density
                val w = node.size.width / density
                val h = node.size.height / density
                if (w < 48f - 0.5f || h < 48f - 0.5f) problems += "$step #$i is %.0fx%.0fdp".format(w, h)
            }
        }
        println("ACCESSIBILITY problems: $problems")
        assertTrue("Tap targets or labels missing: $problems", problems.isEmpty())
    }
}
