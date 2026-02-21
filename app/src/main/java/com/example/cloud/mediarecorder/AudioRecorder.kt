package com.example.cloud.mediarecorder

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.annotation.RequiresPermission
import com.example.cloud.ERRORINSERT
import com.example.cloud.ERRORINSERTDATA
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant

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

        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                channelConfig,
                audioFormat
            )
            if (bufferSize <= 0) {
                throw IllegalStateException("Ungueltige Buffergroesse: $bufferSize")
            }

            audioRecord = AudioRecord(
                android.media.MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord konnte nicht initialisiert werden")
            }

            val m4aFile = File(outputFile.parent, outputFile.nameWithoutExtension + ".m4a")

            audioRecord?.startRecording()
            isRecording = true

            recordingThread = Thread {
                try {
                    encodeToAAC(m4aFile)
                } catch (e: Exception) {
                    reportError("Fehler beim Audio-Encoding", e, "startRecording")
                } finally {
                    try {
                        audioRecord?.stop()
                    } catch (e: Exception) {
                        reportError("Fehler beim Stoppen von AudioRecord", e, "startRecording")
                    }
                    try {
                        audioRecord?.release()
                    } catch (e: Exception) {
                        reportError("Fehler beim Freigeben von AudioRecord", e, "startRecording")
                    } finally {
                        audioRecord = null
                        isRecording = false
                    }
                }
            }
            recordingThread?.start()
        } catch (e: Exception) {
            isRecording = false
            reportError("Fehler bei startRecording", e, "startRecording")
            try {
                audioRecord?.release()
            } catch (releaseException: Exception) {
                reportError(
                    "Fehler beim Freigeben nach Start-Fehler",
                    releaseException,
                    "startRecording"
                )
            } finally {
                audioRecord = null
            }
        }
    }

    private fun encodeToAAC(outputFile: File) {
        var muxer: MediaMuxer? = null
        var codec: MediaCodec? = null
        var muxerStarted = false

        try {
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                44100,
                2
            )
            format.setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC
            )
            format.setInteger(MediaFormat.KEY_BIT_RATE, 128000)
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)

            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            var trackIndex = -1
            val bufferInfo = MediaCodec.BufferInfo()
            val buffer = ByteArray(4096)

            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) {
                    val inputBufferIndex = codec.dequeueInputBuffer(10000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                        inputBuffer?.clear()
                        inputBuffer?.put(buffer, 0, read)
                        codec.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            read,
                            System.nanoTime() / 1000,
                            0
                        )
                    }
                }

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

                        if (trackIndex >= 0) {
                            muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                        }
                    }

                    codec.releaseOutputBuffer(outputBufferIndex, false)
                    outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                }
            }

            val inputBufferIndex = codec.dequeueInputBuffer(10000)
            if (inputBufferIndex >= 0) {
                codec.queueInputBuffer(
                    inputBufferIndex,
                    0,
                    0,
                    System.nanoTime() / 1000,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
            }
        } catch (e: Exception) {
            reportError("Fehler in encodeToAAC", e, "encodeToAAC")
            throw e
        } finally {
            try {
                codec?.stop()
            } catch (e: Exception) {
                reportError("Fehler beim Stoppen vom MediaCodec", e, "encodeToAAC")
            }
            try {
                codec?.release()
            } catch (e: Exception) {
                reportError("Fehler beim Freigeben vom MediaCodec", e, "encodeToAAC")
            }
            try {
                if (muxerStarted) {
                    muxer?.stop()
                }
            } catch (e: Exception) {
                reportError("Fehler beim Stoppen vom MediaMuxer", e, "encodeToAAC")
            }
            try {
                muxer?.release()
            } catch (e: Exception) {
                reportError("Fehler beim Freigeben vom MediaMuxer", e, "encodeToAAC")
            }
        }
    }

    fun stopRecording() {
        isRecording = false
        try {
            recordingThread?.join(2000)
        } catch (e: Exception) {
            reportError("Fehler beim Join von recordingThread", e, "stopRecording")
        }
        recordingThread = null
    }

    private fun reportError(message: String, throwable: Throwable, servicename: String) {
        CoroutineScope(Dispatchers.IO).launch {
            ERRORINSERT(
                ERRORINSERTDATA(
                    "AudioRecorder.$servicename",
                    "$message: ${throwable::class.simpleName} - ${throwable.message ?: "ohne Nachricht"}",
                    Instant.now().toString(),
                    "Error"
                )
            )
        }
    }
}
