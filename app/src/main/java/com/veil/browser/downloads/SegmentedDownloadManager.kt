package com.veil.browser.downloads

import com.veil.browser.data.DownloadChunkDao
import com.veil.browser.data.DownloadChunkEntity
import com.veil.browser.tor.TorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.Proxy

class SegmentedDownloadManager(
    private val chunkDao: DownloadChunkDao
) {
    private fun buildClient(viaTor: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
        if (viaTor) {
            // TOR-5: Downloads started in Tor mode route through local Tor SOCKS5
            val proxy = Proxy(
                Proxy.Type.SOCKS,
                InetSocketAddress(TorManager.SOCKS_HOST, TorManager.SOCKS_PORT)
            )
            builder.proxy(proxy)
        }
        return builder.build()
    }

    suspend fun download(
        downloadId: String,
        url: String,
        destination: File,
        viaTor: Boolean,
        desiredSegments: Int = 6
    ) = withContext(Dispatchers.IO) {
        val client = buildClient(viaTor)

        // DL-1: Issue HTTP HEAD to verify Accept-Ranges: bytes and Content-Length
        val headRequest = Request.Builder().url(url).head().build()
        val headResponse = client.newCall(headRequest).execute()
        val acceptRanges = headResponse.header("Accept-Ranges")?.equals("bytes", true) == true
        val contentLength = headResponse.header("Content-Length")?.toLongOrNull() ?: -1L
        headResponse.close()

        // DL-4: Fall back to single stream when server does not support ranges
        val segmentCount = if (acceptRanges && contentLength > 0) {
            desiredSegments.coerceIn(4, 8)
        } else {
            1
        }

        if (contentLength > 0) {
            RandomAccessFile(destination, "rw").use { it.setLength(contentLength) }
        }

        val existing = chunkDao.getChunksForDownload(downloadId)
        val chunks = if (existing.isNotEmpty()) existing else buildChunks(downloadId, contentLength, segmentCount)
        chunkDao.upsertAll(chunks)

        // DL-2 & DL-3: Fetch ranges concurrently (206 Partial Content) and persist byte offsets
        coroutineScope {
            chunks.map { chunk ->
                async(Dispatchers.IO) {
                    val resumeFrom = chunk.startByte + chunk.downloadedBytes
                    if (chunk.endByte >= 0 && resumeFrom > chunk.endByte) return@async

                    val reqBuilder = Request.Builder().url(url)
                    if (segmentCount > 1) {
                        reqBuilder.addHeader("Range", "bytes=$resumeFrom-${chunk.endByte}")
                    }

                    client.newCall(reqBuilder.build()).execute().use { resp ->
                        val body = resp.body?.byteStream() ?: return@use
                        val buffer = ByteArray(16 * 1024)
                        var currentOffset = resumeFrom
                        RandomAccessFile(destination, "rw").use { raf ->
                            raf.seek(currentOffset)
                            while (true) {
                                val read = body.read(buffer)
                                if (read == -1) break
                                raf.write(buffer, 0, read)
                                currentOffset += read
                                chunkDao.updateProgress(
                                    downloadId = downloadId,
                                    chunkIndex = chunk.chunkIndex,
                                    downloadedBytes = currentOffset - chunk.startByte
                                )
                            }
                        }
                    }
                }
            }.awaitAll()
        }
    }

    private fun buildChunks(id: String, totalLength: Long, count: Int): List<DownloadChunkEntity> {
        if (count == 1 || totalLength <= 0) {
            return listOf(DownloadChunkEntity(id, 0, 0, totalLength - 1, 0))
        }
        val partSize = totalLength / count
        return (0 until count).map { idx ->
            val start = idx * partSize
            val end = if (idx == count - 1) totalLength - 1 else (idx + 1) * partSize - 1
            DownloadChunkEntity(id, idx, start, end, 0)
        }
    }
}
