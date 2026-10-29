package com.roshan.instatube

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.nio.ByteBuffer

internal data class SaveOutcome(val fellBackToDefaultFolder: Boolean)

internal fun saveToDownloads(
    item: MediaResult,
    context: Context,
    settings: AppSettings,
    treeUri: String? = settings.downloadTreeUri
): SaveOutcome {
    val base = item.baseName.ifBlank { "instatube_${System.currentTimeMillis()}" }
    val fileName = if (item.isVideo) "$base.mp4" else "$base.jpg"
    val mimeType = if (item.isVideo) "video/mp4" else "image/jpeg"
    val (uri, fellBack) = try {
        saveStreamToStorage(
            context = context,
            downloadTreeUri = treeUri,
            fileName = fileName,
            mimeType = mimeType,
            meta = item.meta,
            writer = { out -> InstagramDownloader.downloadToStream(item.url, out) }
        ) to false
    } catch (e: Exception) {
        if (treeUri == null || treeUri != settings.downloadTreeUri) throw e
        settings.downloadTreeUri = null
        settings.downloadFolderName = AppSettings.DEFAULT_FOLDER_NAME
        saveStreamToStorage(
            context = context,
            downloadTreeUri = null,
            fileName = fileName,
            mimeType = mimeType,
            meta = item.meta,
            writer = { out -> InstagramDownloader.downloadToStream(item.url, out) }
        ) to true
    }
    DownloadHistory.add(context, DownloadRecord(
        uri.toString(), fileName, mimeType, System.currentTimeMillis(), item.meta,
        item.width.takeIf { it < Int.MAX_VALUE } ?: 0, item.height, item.durationSec
    ))
    return SaveOutcome(fellBack)
}

internal fun saveCoverToDownloads(
    item: MediaResult,
    context: Context,
    settings: AppSettings,
    treeUri: String? = settings.downloadTreeUri
): SaveOutcome {
    val coverUrl = item.bestCoverUrl ?: throw Exception("Cover image unavailable for this item")
    val base = item.baseName.ifBlank { "instatube_${System.currentTimeMillis()}" }
    val fileName = "${base}_cover.jpg"
    val mimeType = "image/jpeg"
    val (uri, fellBack) = try {
        saveStreamToStorage(
            context = context,
            downloadTreeUri = treeUri,
            fileName = fileName,
            mimeType = mimeType,
            meta = item.meta,
            writer = { out -> InstagramDownloader.downloadToStream(coverUrl, out) }
        ) to false
    } catch (e: Exception) {
        if (treeUri == null || treeUri != settings.downloadTreeUri) throw e
        settings.downloadTreeUri = null
        settings.downloadFolderName = AppSettings.DEFAULT_FOLDER_NAME
        saveStreamToStorage(
            context = context,
            downloadTreeUri = null,
            fileName = fileName,
            mimeType = mimeType,
            meta = item.meta,
            writer = { out -> InstagramDownloader.downloadToStream(coverUrl, out) }
        ) to true
    }
    DownloadHistory.add(context, DownloadRecord(
        uri.toString(), fileName, mimeType, System.currentTimeMillis(), item.meta,
        item.width.takeIf { it < Int.MAX_VALUE } ?: 0, item.height, 0.0
    ))
    return SaveOutcome(fellBack)
}

internal fun saveAudioToDownloads(
    item: MediaResult,
    context: Context,
    settings: AppSettings,
    treeUri: String? = settings.downloadTreeUri
): SaveOutcome {
    val sourceUrl = item.audioUrl ?: item.url.takeIf { item.isVideo }
        ?: throw Exception("No audio track available for this post")
    val base = item.baseName.ifBlank { "instatube_${System.currentTimeMillis()}" }
    val fileName = "${base}_audio.m4a"
    val mimeType = "audio/mp4"

    val tempSource = File(context.cacheDir, "ig_src_${System.currentTimeMillis()}.mp4")
    val tempAudio = File(context.cacheDir, "ig_aud_${System.currentTimeMillis()}.m4a")
    try {
        tempSource.outputStream().use { out ->
            InstagramDownloader.downloadToStream(sourceUrl, out)
        }
        val audioFile = if (extractAudioTrackToM4a(tempSource, tempAudio) && tempAudio.length() > 0L) {
            tempAudio
        } else {
            tempSource
        }
        val (uri, fellBack) = try {
            saveStreamToStorage(
                context = context,
                downloadTreeUri = treeUri,
                fileName = fileName,
                mimeType = mimeType,
                meta = item.meta,
                writer = { out -> FileInputStream(audioFile).use { it.copyTo(out) } }
            ) to false
        } catch (e: Exception) {
            if (treeUri == null || treeUri != settings.downloadTreeUri) throw e
            settings.downloadTreeUri = null
            settings.downloadFolderName = AppSettings.DEFAULT_FOLDER_NAME
            saveStreamToStorage(
                context = context,
                downloadTreeUri = null,
                fileName = fileName,
                mimeType = mimeType,
                meta = item.meta,
                writer = { out -> FileInputStream(audioFile).use { it.copyTo(out) } }
            ) to true
        }
        DownloadHistory.add(context, DownloadRecord(
            uri.toString(), fileName, mimeType, System.currentTimeMillis(), item.meta,
            0, 0, item.durationSec
        ))
        return SaveOutcome(fellBack)
    } finally {
        runCatching { tempSource.delete() }
        runCatching { tempAudio.delete() }
    }
}

internal fun saveYouTubeCoverToDownloads(
    videoId: String,
    title: String,
    channel: String,
    thumbnailUrl: String?,
    context: Context,
    settings: AppSettings = AppSettings(context)
): SaveOutcome {
    val safe = title.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), " ")
        .replace(Regex("\\s+"), " ").trim().take(50).replace(' ', '_').ifBlank { videoId }
    val fileName = "${safe}_${videoId}_cover.jpg"
    val mimeType = "image/jpeg"
    val meta = PostMeta(
        username = channel.ifBlank { "YouTube" },
        caption = title,
        url = "https://youtu.be/$videoId",
        song = null,
        takenAtSec = System.currentTimeMillis() / 1000L
    )
    val candidates = listOfNotNull(
        "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg",
        "https://i.ytimg.com/vi/$videoId/hq720.jpg",
        thumbnailUrl,
        "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    ).distinct()

    var bytes: ByteArray? = null
    for (url in candidates) {
        val b = runCatching {
            val req = Request.Builder().url(url).header("User-Agent", Net.USER_AGENT).build()
            Net.client.newCall(req).execute().use { res ->
                if (res.isSuccessful) res.body?.bytes()?.takeIf { it.size > 1024 } else null
            }
        }.getOrNull()
        if (b != null) {
            bytes = b
            break
        }
    }
    val finalBytes = bytes ?: throw Exception("Could not download YouTube cover image")
    val treeUri = settings.downloadTreeUri
    val (uri, fellBack) = try {
        saveStreamToStorage(
            context = context,
            downloadTreeUri = treeUri,
            fileName = fileName,
            mimeType = mimeType,
            meta = meta,
            writer = { out -> out.write(finalBytes) }
        ) to false
    } catch (e: Exception) {
        if (treeUri == null) throw e
        settings.downloadTreeUri = null
        settings.downloadFolderName = AppSettings.DEFAULT_FOLDER_NAME
        saveStreamToStorage(
            context = context,
            downloadTreeUri = null,
            fileName = fileName,
            mimeType = mimeType,
            meta = meta,
            writer = { out -> out.write(finalBytes) }
        ) to true
    }
    DownloadHistory.add(context, DownloadRecord(
        uri.toString(), fileName, mimeType, System.currentTimeMillis(), meta, 1280, 720, 0.0
    ))
    return SaveOutcome(fellBack)
}

internal fun exportLocalFileToDownloads(
    sourceFile: File,
    fileName: String,
    mimeType: String,
    meta: PostMeta?,
    width: Int,
    height: Int,
    durationSec: Double,
    context: Context
) {
    if (!sourceFile.exists() || sourceFile.length() <= 0L) return
    val settings = AppSettings(context)
    val treeUri = settings.downloadTreeUri
    val uri = runCatching {
        saveStreamToStorage(
            context = context,
            downloadTreeUri = treeUri,
            fileName = fileName,
            mimeType = mimeType,
            meta = meta,
            writer = { out -> FileInputStream(sourceFile).use { it.copyTo(out) } }
        )
    }.getOrElse {
        runCatching {
            saveStreamToStorage(
                context = context,
                downloadTreeUri = null,
                fileName = fileName,
                mimeType = mimeType,
                meta = meta,
                writer = { out -> FileInputStream(sourceFile).use { it.copyTo(out) } }
            )
        }.getOrNull()
    } ?: Uri.fromFile(sourceFile)

    DownloadHistory.add(context, DownloadRecord(
        uri.toString(), fileName, mimeType, System.currentTimeMillis(), meta, width, height, durationSec
    ))
}

private fun extractAudioTrackToM4a(sourceFile: File, outFile: File): Boolean {
    val extractor = MediaExtractor()
    var muxer: MediaMuxer? = null
    return try {
        extractor.setDataSource(sourceFile.absolutePath)
        var audioTrackIndex = -1
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                break
            }
        }
        if (audioTrackIndex < 0) return false
        extractor.selectTrack(audioTrackIndex)
        val trackFormat = extractor.getTrackFormat(audioTrackIndex)
        muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val outTrack = muxer.addTrack(trackFormat)
        muxer.start()

        val buffer = ByteBuffer.allocateDirect(1024 * 1024)
        val info = MediaCodec.BufferInfo()
        var firstPts = -1L
        while (true) {
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break
            val pts = extractor.sampleTime
            if (firstPts < 0) firstPts = pts
            info.set(0, sampleSize, (pts - firstPts).coerceAtLeast(0L), extractor.sampleFlags)
            muxer.writeSampleData(outTrack, buffer, info)
            extractor.advance()
        }
        muxer.stop()
        true
    } catch (_: Throwable) {
        outFile.delete()
        false
    } finally {
        runCatching { muxer?.release() }
        runCatching { extractor.release() }
    }
}

private fun saveStreamToStorage(
    context: Context,
    downloadTreeUri: String?,
    fileName: String,
    mimeType: String,
    meta: PostMeta?,
    writer: (OutputStream) -> Unit
): Uri {
    val isImage = mimeType.startsWith("image/")
    val base = fileName.substringBeforeLast('.')

    if (downloadTreeUri != null) {
        val treeUri = Uri.parse(downloadTreeUri)
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri, DocumentsContract.getTreeDocumentId(treeUri)
        )
        val fileUri = DocumentsContract.createDocument(
            context.contentResolver, parent, mimeType, fileName
        ) ?: throw Exception(context.getString(R.string.error_create_file_in_folder))
        context.contentResolver.openOutputStream(fileUri)?.use(writer)
            ?: throw Exception(context.getString(R.string.error_write_to_folder))
        if (isImage) tagJpeg(meta) {
            context.contentResolver.openFileDescriptor(fileUri, "rw")?.use(it)
        }
        return fileUri
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/InstaTube")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            meta?.let {
                put(MediaStore.MediaColumns.TITLE, it.caption?.take(100) ?: base)
                put(MediaStore.MediaColumns.ARTIST, it.username)
            }
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: throw Exception(context.getString(R.string.error_create_file_in_downloads))
        resolver.openOutputStream(uri)?.use(writer)
        if (isImage) tagJpeg(meta) { resolver.openFileDescriptor(uri, "rw")?.use(it) }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    } else {
        try {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "InstaTube"
            ).apply { mkdirs() }
            val file = File(dir, fileName)
            file.outputStream().use(writer)
            if (isImage) tagJpeg(meta) {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE).use(it)
            }
            return Uri.fromFile(file)
        } catch (e: SecurityException) {
            throw Exception(context.getString(R.string.error_storage_permission_lost), e)
        }
    }
}

private fun tagJpeg(meta: PostMeta?, withFd: ((ParcelFileDescriptor) -> Unit) -> Unit) {
    if (meta == null) return
    runCatching {
        withFd { pfd ->
            val exif = android.media.ExifInterface(pfd.fileDescriptor)
            meta.username?.let { exif.setAttribute(android.media.ExifInterface.TAG_ARTIST, it) }
            meta.caption?.let { exif.setAttribute(android.media.ExifInterface.TAG_IMAGE_DESCRIPTION, it) }
            exif.setAttribute(
                android.media.ExifInterface.TAG_USER_COMMENT,
                listOfNotNull(meta.url, meta.song?.let { "Song: $it" }).joinToString("\n")
            )
            if (meta.takenAtSec > 0) exif.setAttribute(
                android.media.ExifInterface.TAG_DATETIME_ORIGINAL,
                java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US)
                    .format(java.util.Date(meta.takenAtSec * 1000))
            )
            exif.saveAttributes()
        }
    }
}

