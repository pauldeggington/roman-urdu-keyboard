package com.paul.urdukb.voice

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/**
 * Turns a shared voice note (WhatsApp sends Ogg Opus; others send AAC, MP3, AMR) into 16 kHz mono samples. Android's own
 * decoders do the work, so nothing extra is bundled.
 */
object AudioDecoder {

    /** At most this much is decoded; longer notes are cut off here (and the screen says so). */
    const val MAX_SECONDS = 5 * 60

    class Decoded(val samples: ShortArray, val truncated: Boolean, val format: String)

    private class Growing {
        var data = ShortArray(Pcm.RATE * 30)
        var size = 0
        fun add(a: ShortArray) {
            if (size + a.size > data.size) data = data.copyOf(maxOf(data.size * 2, size + a.size))
            System.arraycopy(a, 0, data, size, a.size)
            size += a.size
        }
        fun done() = data.copyOf(size)
    }

    fun decode(ctx: Context, uri: Uri): Decoded {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(ctx, uri, null)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")) { track = i; format = f; break }
            }
            if (track < 0 || format == null) throw IllegalStateException("There is no audio in this file.")
            extractor.selectTrack(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var float = false
            val out = Growing()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var truncated = false
            val limit = MAX_SECONDS * Pcm.RATE

            while (!outputDone) {
                if (!inputDone) {
                    val ix = codec.dequeueInputBuffer(10_000)
                    if (ix >= 0) {
                        val buf = codec.getInputBuffer(ix)!!
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(ix, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(ix, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val ox = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    ox == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        float = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    ox >= 0 -> {
                        val buf = codec.getOutputBuffer(ox)!!
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        val bb = buf.order(ByteOrder.LITTLE_ENDIAN)
                        val shorts = if (float) {
                            val fb = bb.asFloatBuffer()
                            ShortArray(fb.remaining()) { (fb.get().coerceIn(-1f, 1f) * 32767f).toInt().toShort() }
                        } else {
                            val sb = bb.asShortBuffer()
                            ShortArray(sb.remaining()).also { sb.get(it) }
                        }
                        // converted piece by piece, so a long note never holds its full-rate samples in memory
                        out.add(Pcm.toMono16k(shorts, rate, channels))
                        codec.releaseOutputBuffer(ox, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        if (out.size >= limit) { truncated = true; outputDone = true }
                    }
                }
            }
            return Decoded(out.done().let { if (it.size > limit) it.copyOf(limit) else it }, truncated, mime)
        } finally {
            try { codec?.stop() } catch (_: Throwable) {}
            try { codec?.release() } catch (_: Throwable) {}
            extractor.release()
        }
    }
}
