package com.forgeport.android.repo

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand.ResetType
import org.eclipse.jgit.revwalk.RevCommit

internal fun commitChangedFiles(git: Git, message: String, identity: CommitIdentity): List<RevCommit> {
    val status = git.status().call()
    check(status.conflicting.isEmpty()) { "Conflicting files cannot be published." }
    val paths = (status.added + status.changed + status.removed)
        .sortedWith(compareBy<String> { it !in status.removed }.thenBy { it })
    if (paths.isEmpty()) return emptyList()
    // Clear the staged batch so each commit contains exactly one path; delete before adding replacements.
    git.reset().setMode(ResetType.MIXED).call()
    return paths.map { path ->
        if (path in status.removed) git.rm().setCached(true).addFilepattern(path).call()
        else git.add().addFilepattern(path).call()
        git.commit()
            .setMessage("$message: $path")
            .setAuthor(identity.name, identity.email)
            .setCommitter(identity.name, identity.email)
            .call()
    }
}
