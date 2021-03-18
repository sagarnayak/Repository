package com.sagar.repository

import android.os.Handler
import android.os.Looper
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.FileInputStream


class RequestBodyForProgress(private val file: File) : RequestBody() {

    private lateinit var progressCallback: ProgressCallback

    interface ProgressCallback {
        fun progressChange(progress: Float)
    }

    @Nullable
    override fun contentType(): MediaType? {
        val ext = file.name.split(".").last()
        return "image/$ext".toMediaType()
    }

    override fun contentLength(): Long {
        return file.length()
    }

    override fun writeTo(@NonNull sink: BufferedSink) {
        val fileLength: Long = file.length()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var uploaded: Long = 0
        FileInputStream(file).use { inputStream ->
            var read: Int = 0
            val handler = Handler(Looper.getMainLooper())
            while (inputStream.read(buffer).also { read = it } != -1) {
                uploaded += read.toLong()
                sink.write(buffer, 0, read)
                handler.post(ProgressUpdater(uploaded, fileLength))
            }
        }
    }

    private inner class ProgressUpdater internal constructor(
        private val mUploaded: Long,
        private val mTotal: Long
    ) :
        Runnable {
        override fun run() {
            if (this@RequestBodyForProgress::progressCallback.isInitialized) {
                progressCallback.progressChange(((mUploaded.toFloat() / mTotal.toFloat()) * 100))
            }
        }
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 1024
    }

    @Suppress("unused")
    fun requestCallback(
        progressCallback: ProgressCallback
    ): RequestBodyForProgress {
        this.progressCallback = progressCallback
        return this
    }
}