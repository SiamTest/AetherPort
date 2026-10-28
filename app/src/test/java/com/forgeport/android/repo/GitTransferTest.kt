package com.forgeport.android.repo

import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.transport.PacketLineOut
import org.eclipse.jgit.transport.ReceivePack
import org.eclipse.jgit.transport.RefAdvertiser.PacketLineOutRefAdvertiser
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UploadPack
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GitTransferTest {
    @get:Rule val temporary = TemporaryFolder()
    private val credentials = UsernamePasswordCredentialsProvider("test", "unused")

    private fun commit(git: Git, text: String) {
        File(git.repository.workTree, "app.txt").writeText(text)
        git.add().addFilepattern(".").call()
        git.commit().setMessage(text).setAuthor("Test", "test@example.com")
            .setCommitter("Test", "test@example.com").call()
    }

    private fun remote(test: (Git) -> Unit) {
        Git.init().setDirectory(temporary.newFolder()).setInitialBranch("main").call().use { seed ->
            commit(seed, "initial")
            Git.cloneRepository().setURI(seed.repository.workTree.toURI().toString())
                .setBare(true).setDirectory(temporary.newFolder()).call().use(test)
        }
    }

    // A real smart-HTTP Git endpoint; only the chosen response is truncated.
    private class Server(
        private val repository: Repository,
        private val breakClone: Boolean = false,
        private val breakPush: Boolean = false,
    ) : AutoCloseable {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val cloneAdvertisements = AtomicInteger()
        val pushRequests = AtomicInteger()
        val url get() = "http://127.0.0.1:${server.address.port}/repo.git"

        init {
            server.createContext("/repo.git/") { exchange ->
                exchange.use { response ->
                    val advertisement = response.requestURI.path.endsWith("/info/refs")
                    val service = if (advertisement) response.requestURI.query.removePrefix("service=")
                        else response.requestURI.path.substringAfterLast('/')
                    val request = if (response.requestHeaders.getFirst("Content-Encoding") == "gzip")
                        GZIPInputStream(response.requestBody) else response.requestBody
                    val input = request.readBytes().inputStream()
                    val output = ByteArrayOutputStream()
                    val packet = PacketLineOut(output)
                    if (advertisement) {
                        packet.writeString("# service=$service\n")
                        packet.end()
                        if (service == "git-upload-pack" && breakClone && cloneAdvertisements.incrementAndGet() == 1) {
                            // Incomplete four-byte packet length reproduces JGit's exact EOF message.
                            output.write("001".toByteArray())
                        } else if (service == "git-upload-pack") {
                            UploadPack(repository).use { it.sendAdvertisedRefs(PacketLineOutRefAdvertiser(packet)) }
                        } else {
                            ReceivePack(repository).apply { sendAdvertisedRefs(PacketLineOutRefAdvertiser(packet)); revWalk.close() }
                        }
                    } else if (service == "git-upload-pack") {
                        UploadPack(repository).use {
                            it.isBiDirectionalPipe = false
                            it.upload(input, output, null)
                        }
                    } else {
                        ReceivePack(repository).apply {
                            isBiDirectionalPipe = false
                            isAllowNonFastForwards = false
                            receive(input, output, null)
                        }
                        if (breakPush && pushRequests.incrementAndGet() == 1) {
                            // Commit was accepted, but its status response was lost in transit.
                            output.reset()
                            output.write("001".toByteArray())
                        }
                    }
                    response.responseHeaders.set("Content-Type", "application/x-$service-${if (advertisement) "advertisement" else "result"}")
                    val bytes = output.toByteArray()
                    response.sendResponseHeaders(200, bytes.size.toLong())
                    response.responseBody.write(bytes)
                }
            }
            server.start()
        }

        override fun close() = server.stop(0)
    }

    @Test fun cloneRecoversFromTruncatedAdvertisementAndDiscardsPartialDirectory() = remote { remote ->
        Server(remote.repository, breakClone = true).use { server ->
            val directory = File(temporary.newFolder(), "clone")
            cloneForTransfer(server.url, credentials, directory).use { clone ->
                assertEquals(remote.repository.resolve("refs/heads/main"), clone.repository.resolve(Constants.HEAD))
                assertEquals("initial", File(directory, "app.txt").readText())
            }
            assertEquals(2, server.cloneAdvertisements.get())
        }
    }

    @Test fun lostPushSuccessRetriesSameTipWithoutDuplicateCommits() = remote { remote ->
        Server(remote.repository, breakPush = true).use { server ->
            cloneForTransfer(server.url, credentials, File(temporary.newFolder(), "clone")).use { clone ->
                commit(clone, "change")
                val tip = clone.repository.resolve(Constants.HEAD)
                var attempts = 0
                val results = retryGitTransfer("Push") {
                    attempts++
                    clone.push().setCredentialsProvider(credentials).setTimeout(5)
                        .setRefSpecs(RefSpec("refs/heads/main:refs/heads/main")).call().toList()
                }
                assertEquals(2, attempts)
                assertEquals(1, server.pushRequests.get())
                assertTrue(results.flatMap { it.remoteUpdates }.all { it.status == RemoteRefUpdate.Status.UP_TO_DATE })
                assertEquals(tip, remote.repository.resolve("refs/heads/main"))
                assertEquals(2, remote.log().add(tip).call().count())
            }
        }
    }

    @Test fun repeatedTruncationIsBoundedAndRetainsActionableDetails() {
        var attempts = 0
        try {
            retryGitTransfer("Clone") { attempts++; throw TransportException("transfer failed", EOFException("Short read of block.")) }
            fail("Expected failure")
        } catch (error: IOException) {
            val message = cleanGitError(error)
            assertTrue(message.contains("Clone failed after one automatic retry"))
            assertTrue(message.contains("Check your connection"))
            assertTrue(message.contains("Short read of block."))
        }
        assertEquals(2, attempts)
    }

    @Test fun timeoutRetriesButAuthenticationAndValidationErrorsDoNot() {
        var attempts = 0
        assertEquals("ok", retryGitTransfer("Clone") {
            if (++attempts == 1) throw TransportException("timed out", SocketTimeoutException())
            "ok"
        })
        assertEquals(2, attempts)
        for (failure in listOf(TransportException("not authorized"), IOException("Space metadata rejected"))) {
            attempts = 0
            try {
                retryGitTransfer("Push") { attempts++; throw failure }
                fail("Expected failure")
            } catch (error: IOException) {
                assertTrue(cleanGitError(error).contains(failure.message!!))
            }
            assertEquals(1, attempts)
        }
    }

    @Test fun diagnosticsRedactTokensBeforeTruncatingAndKeepServerRejections() {
        val secret = "hf_" + "x".repeat(1600)
        val error = IOException("remote: Space metadata rejected\nAuthorization: Bearer $secret",
            IOException("https://user:password@example.com/repo ghp_abcdefgh github_pat_abcdefgh"))
        val cleaned = cleanGitError(error)
        assertTrue(cleaned.contains("Space metadata rejected"))
        assertTrue(cleaned.contains("https://[redacted]@example.com"))
        assertFalse(cleaned.contains("hf_"))
        assertFalse(cleaned.contains("ghp_"))
        assertFalse(cleaned.contains("github_pat_"))
        assertFalse(cleaned.contains("password"))
    }
}
