package com.example.cloud.mediarecorder

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.annotation.RequiresPermission
import java.io.File

// Füge diese Klasse außerhalb der Composable hinzu:
class AudioRecorder {
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    @Volatile
    private var isRecording = false

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecording(outputFile: File) {
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_STEREO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            channelConfig,
            audioFormat
        )

        audioRecord = AudioRecord(
            android.media.MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        // Ändere Dateiendung zu .m4a (AAC komprimiert)
        val m4aFile = File(outputFile.parent, outputFile.nameWithoutExtension + ".m4a")

        audioRecord?.startRecording()
        isRecording = true

        recordingThread = Thread {
            try {
                encodeToAAC(m4aFile, sampleRate)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
            }
        }
        recordingThread?.start()
    }

    private fun encodeToAAC(outputFile: File, sampleRate: Int) {
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            sampleRate,
            2 // Stereo
        )
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(MediaFormat.KEY_BIT_RATE, 128000) // 128 kbps
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        var trackIndex = -1
        var muxerStarted = false

        val bufferInfo = MediaCodec.BufferInfo()
        val buffer = ByteArray(4096)

        while (isRecording) {
            // PCM Daten lesen
            val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
            if (read > 0) {
                // PCM in Codec eingeben
                val inputBufferIndex = codec.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                    inputBuffer?.clear()
                    inputBuffer?.put(buffer, 0, read)
                    codec.queueInputBuffer(inputBufferIndex, 0, read, System.nanoTime() / 1000, 0)
                }
            }

            // Encoded Daten holen
            var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            while (outputBufferIndex >= 0) {
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                    continue
                }

                val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    if (!muxerStarted) {
                        val outputFormat = codec.outputFormat
                        trackIndex = muxer.addTrack(outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }

                    if (muxerStarted) {
                        muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                    }
                }

                codec.releaseOutputBuffer(outputBufferIndex, false)
                outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            }
        }

        // Finalisieren
        val inputBufferIndex = codec.dequeueInputBuffer(10000)
        if (inputBufferIndex >= 0) {
            codec.queueInputBuffer(inputBufferIndex, 0, 0, System.nanoTime() / 1000, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }

        codec.stop()
        codec.release()

        if (muxerStarted) {
            muxer.stop()
        }
        muxer.release()
    }

    fun stopRecording() {
        isRecording = false
        recordingThread?.join(2000)
        recordingThread = null
    }
}