package com.forgeport.android.repo

import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.util.io.DisabledOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileCommitsTest {
    @get:Rule val temporary = TemporaryFolder()
    private val identity = CommitIdentity.github("TokenOwner", 123, "Token Owner")

    private fun write(root: File, path: String, value: String) {
        File(root, path).apply { parentFile.mkdirs(); writeText(value) }
    }

    private fun stage(git: Git) {
        git.add().addFilepattern(".").call()
        git.add().setUpdate(true).addFilepattern(".").call()
    }

    private fun repository(files: Map<String, String>, test: (Git, File) -> Unit) {
        val root = temporary.newFolder()
        Git.init().setDirectory(root).setInitialBranch("main").call().use { git ->
            git.repository.config.apply { setBoolean("core", null, "autocrlf", false); save() }
            files.forEach { (path, value) -> write(root, path, value) }
            stage(git)
            git.commit().setMessage("Initial").setAuthor("Initial", "initial@example.com")
                .setCommitter("Initial", "initial@example.com").call()
            test(git, root)
        }
    }

    private fun paths(git: Git, commits: List<RevCommit>): List<String> {
        RevWalk(git.repository).use { walk ->
            DiffFormatter(DisabledOutputStream.INSTANCE).use { diff ->
                diff.setRepository(git.repository)
                diff.setDetectRenames(false)
                return commits.map { commit ->
                    val parent = walk.parseCommit(commit.getParent(0).id)
                    val changes = diff.scan(parent.tree, commit.tree)
                    assertEquals("Every commit must change exactly one path", 1, changes.size)
                    val change = changes.single()
                    val path = if (change.changeType == DiffEntry.ChangeType.DELETE) change.oldPath else change.newPath
                    assertEquals("Update ZIP: $path", commit.fullMessage)
                    assertEquals(identity.name, commit.authorIdent.name)
                    assertEquals(identity.email, commit.authorIdent.emailAddress)
                    assertEquals(identity.name, commit.committerIdent.name)
                    assertEquals(identity.email, commit.committerIdent.emailAddress)
                    path
                }
            }
        }
    }

    @Test fun fiftyChangedFilesProduceFiftySingleFileCommitsAndPushTogether() {
        val names = listOf(" leading space.txt", "bracket[1].txt", "unicodé.txt", "--option.txt") +
            (0 until 46).map { "file-${it.toString().padStart(2, '0')}.txt" }
        repository(names.associateWith { "old" } + ("unchanged.txt" to "same")) { git, root ->
            names.forEach { write(root, it, "new") }
            stage(git)
            val commits = commitChangedFiles(git, "Update ZIP", identity)
            assertEquals(50, commits.size)
            assertEquals(names.toSet(), paths(git, commits).toSet())
            assertTrue(git.status().call().isClean)
            assertEquals(51, git.log().call().count())
            val remote = temporary.newFolder()
            Git.init().setBare(true).setDirectory(remote).setInitialBranch("main").call().use { bare ->
                val result = git.push().setRemote(remote.toURI().toString()).call().toList()
                assertEquals(1, result.size)
                assertEquals(git.repository.resolve(Constants.HEAD), bare.repository.resolve("refs/heads/main"))
                assertEquals(51, bare.log().add(bare.repository.resolve("refs/heads/main")).call().count())
            }
            stage(git)
            assertTrue(commitChangedFiles(git, "Update ZIP", identity).isEmpty())
            assertEquals(51, git.log().call().count())
        }
    }

    @Test fun renamesAndFileDirectoryReplacementsRemainSinglePathCommits() {
        val before = mapOf("file-to-dir" to "old", "dir-to-file/child.txt" to "old", "old-name.txt" to "same")
        val after = mapOf("file-to-dir/child.txt" to "new", "dir-to-file" to "new", "new-name.txt" to "same")
        repository(before) { git, root ->
            before.keys.forEach { assertTrue(File(root, it).delete()) }
            assertTrue(File(root, "dir-to-file").delete())
            after.forEach { (path, value) -> write(root, path, value) }
            stage(git)
            val commits = commitChangedFiles(git, "Update ZIP", identity)
            assertEquals(before.keys.sorted() + after.keys.sorted(), paths(git, commits))
            assertTrue(git.status().call().isClean)
            assertEquals(7, git.log().call().count())
        }
    }

    @Test fun unchangedFilesDoNotProduceEmptyCommits() {
        repository(mapOf("same.txt" to "same")) { git, _ ->
            stage(git)
            assertTrue(commitChangedFiles(git, "Update ZIP", identity).isEmpty())
            assertEquals(1, git.log().call().count())
        }
    }
}
