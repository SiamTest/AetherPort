package com.forgeport.android.repo

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.Proxy
import java.net.URL
import java.util.zip.GZIPInputStream
import org.eclipse.jgit.transport.Transport
import org.eclipse.jgit.transport.TransportHttp
import org.eclipse.jgit.transport.http.HttpConnection
import org.eclipse.jgit.transport.http.JDKHttpConnection
import org.eclipse.jgit.transport.http.JDKHttpConnectionFactory

internal fun configureHuggingFaceTransport(transport: Transport) {
    if (transport is TransportHttp && transport.uri.host.equals("huggingface.co", ignoreCase = true)) {
        transport.setHttpConnectionFactory(HuggingFaceHttpConnectionFactory)
    }
}

// The Hub's Git endpoint can return an empty 200 response to gzipped POSTs.
// Keep JGit's HTTP/TLS/auth handling, but send its buffered requests uncompressed.
internal object HuggingFaceHttpConnectionFactory : JDKHttpConnectionFactory() {
    override fun create(url: URL): HttpConnection = UncompressedConnection(url)
    override fun create(url: URL, proxy: Proxy): HttpConnection = UncompressedConnection(url, proxy)
}

private class UncompressedConnection : JDKHttpConnection {
    constructor(url: URL) : super(url)
    constructor(url: URL, proxy: Proxy) : super(url, proxy)

    private var gzip = false

    override fun setRequestProperty(key: String, value: String) {
        if (key.equals("Content-Encoding", ignoreCase = true) && value.equals("gzip", ignoreCase = true)) {
            gzip = true
        } else {
            super.setRequestProperty(key, value)
        }
    }

    override fun setFixedLengthStreamingMode(contentLength: Int) {
        if (!gzip) super.setFixedLengthStreamingMode(contentLength)
    }

    override fun getOutputStream(): OutputStream {
        if (!gzip) return super.getOutputStream()
        // JGit only compresses requests that fit its existing in-memory POST
        // buffer. Large, already-uncompressed streaming uploads pass through.
        return object : ByteArrayOutputStream() {
            private var sent = false
            override fun close() {
                if (sent) return
                sent = true
                val plain = GZIPInputStream(toByteArray().inputStream()).use { it.readBytes() }
                sendPlain(plain)
            }
        }
    }

    private fun sendPlain(bytes: ByteArray) {
        super.setFixedLengthStreamingMode(bytes.size)
        super.getOutputStream().use { it.write(bytes) }
    }
}
