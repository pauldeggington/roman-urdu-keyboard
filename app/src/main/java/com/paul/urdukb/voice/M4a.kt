package com.paul.urdukb.voice

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Turns the WAV that text-to-speech writes into an AAC .m4a file, which chat apps accept as an audio message. */
object M4a {

    private class Wav(val rate: Int, val channels: Int, val pcm: ByteArray)

    /** Reads a PCM16 WAV: finds the "fmt " and "data" parts, wherever they are. */
    private fun readWav(file: File): Wav {
        val b = file.readBytes()
        if (b.size < 44 || String(b, 0, 4) != "RIFF") throw IllegalStateException("not a WAV file")
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        var rate = 22050
        var channels = 1
        var data: ByteArray? = null
        while (pos + 8 <= b.size) {
            val id = String(b, pos, 4)
            var len = bb.getInt(pos + 4)
            if (len < 0 || pos + 8 + len > b.size) len = b.size - pos - 8   // a streamed WAV may carry a wrong length
            if (id == "fmt ") {
                channels = bb.getShort(pos + 10).toInt()
                rate = bb.getInt(pos + 12)
            } else if (id == "data") {
                data = b.copyOfRange(pos + 8, pos + 8 + len)
                break
            }
            pos += 8 + len + (len and 1)
        }
        return Wav(rate, channels, data ?: throw IllegalStateException("no audio in the WAV file"))
    }

    fun fromWav(wavFile: File, out: File) {
        val wav = readWav(wavFile)
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, wav.rate, wav.channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        if (out.exists()) out.delete()
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var muxing = false
        val info = MediaCodec.BufferInfo()
        var inPos = 0
        var inputDone = false
        var outputDone = false
        val bytesPerSecond = wav.rate * wav.channels * 2L
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val ix = codec.dequeueInputBuffer(10_000)
                    if (ix >= 0) {
                        val buf = codec.getInputBuffer(ix)!!
                        buf.clear()
                        val n = minOf(buf.capacity(), wav.pcm.size - inPos)
                        val pts = inPos * 1_000_000L / bytesPerSecond
                        if (n <= 0) {
                            codec.queueInputBuffer(ix, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            buf.put(wav.pcm, inPos, n)
                            codec.queueInputBuffer(ix, 0, n, pts, 0)
                            inPos += n
                        }
                    }
                }
                val ox = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    ox == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxing = true
                    }
                    ox >= 0 -> {
                        val buf = codec.getOutputBuffer(ox)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && muxing) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buf, info)
                        }
                        codec.releaseOutputBuffer(ox, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            try { codec.stop() } catch (_: Throwable) {}
            codec.release()
            if (muxing) try { muxer.stop() } catch (_: Throwable) {}
            muxer.release()
        }
    }
}
