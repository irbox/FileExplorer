package com.opensource.filemanager.server

import android.webkit.MimeTypeMap
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException

class PcShareServer(private val rootDir: File, port: Int = 8080) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val file = File(rootDir, uri)

        if (!file.exists()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File not found")
        }

        if (file.isDirectory) {
            val builder = StringBuilder(
                "<html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'/>" +
                "<style>body{font-family:sans-serif; padding:16px;} li{margin: 12px 0; font-size:18px;} a{text-decoration:none; color:#0056b3;}</style>" +
                "</head><body>"
            )
            builder.append("<h2>📁 ${if (uri == "/") "Internal Storage" else uri}</h2><ul>")
            
            if (uri != "/") {
                val parentUri = uri.substring(0, uri.lastIndexOf('/'))
                val link = if (parentUri.isEmpty()) "/" else parentUri
                builder.append("<li><a href=\"$link\">⬆️ .. (Parent Directory)</a></li>")
            }

            file.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))?.forEach { f ->
                val link = if (uri == "/") "/${f.name}" else "$uri/${f.name}"
                val icon = if (f.isDirectory) "📁" else "📄"
                builder.append("<li><a href=\"$link\">$icon ${f.name}</a></li>")
            }
            builder.append("</ul></body></html>")
            
            return newFixedLengthResponse(Response.Status.OK, MIME_HTML, builder.toString())
        }

        return try {
            val mime = getMimeTypeForFile(file.name)
            newChunkedResponse(Response.Status.OK, mime, FileInputStream(file))
        } catch (e: FileNotFoundException) {
            newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Access Denied")
        }
    }

    private fun getMimeTypeForFile(fileName: String): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(fileName.replace(" ", "%20"))
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: "application/octet-stream"
    }
}
