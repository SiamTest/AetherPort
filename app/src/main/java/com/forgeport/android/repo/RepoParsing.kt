package com.forgeport.android.repo

object RepoParsing {
    private val safeOwnerRepo = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
    private val safeBranch = Regex("^[A-Za-z0-9._/-]+$")

    fun normalizeGitHubRepo(input: String): String {
        var value = input.trim().removeSuffix(".git")
        value = when {
            value.startsWith("https://github.com/") -> value.removePrefix("https://github.com/")
            value.startsWith("git@github.com:") -> value.removePrefix("git@github.com:")
            else -> value
        }.trim('/')
        require(safeOwnerRepo.matches(value)) {
            "Use a GitHub repository like username/repository or https://github.com/username/repository."
        }
        return value
    }

    fun normalizeHuggingFaceSpace(input: String): String {
        var value = input.trim().removeSuffix(".git").trimEnd('/')
        value = when {
            value.startsWith("https://huggingface.co/spaces/") -> value.removePrefix("https://huggingface.co/spaces/")
            value.startsWith("spaces/") -> value.removePrefix("spaces/")
            value.startsWith("space:") -> value.removePrefix("space:")
            else -> value
        }.trim('/')
        require(safeOwnerRepo.matches(value)) {
            "Use a Hugging Face Space like username/repository."
        }
        return value
    }

    fun validateBranch(input: String): String {
        val value = input.trim()
        require(value.isNotEmpty() && value.length <= 240 && safeBranch.matches(value)) {
            "Enter a valid Git branch name."
        }
        require(!value.startsWith('/') && !value.endsWith('/') && !value.contains("..") && !value.contains("//")) {
            "Enter a valid Git branch name."
        }
        return value
    }

    fun githubTokenVariableName(repo: String): String {
        val owner = normalizeGitHubRepo(repo).substringBefore('/')
        val normalized = owner.uppercase().replace(Regex("[^A-Z0-9]+"), "_").trim('_')
        return "GITHUB_TOKEN_$normalized"
    }

    fun repositoryKind(name: String): String? = when {
        name == "GITHUB_REPOSITORY" || name.startsWith("GITHUB_REPOSITORY_") -> "github"
        name == "HF_REPOSITORY" || name.startsWith("HF_REPOSITORY_") ||
            name == "HUGGINGFACE_REPOSITORY" || name.startsWith("HUGGINGFACE_REPOSITORY_") -> "huggingface"
        else -> null
    }

    fun repositoryVariableName(label: String, huggingFace: Boolean): String {
        val suffix = label.trim().uppercase().replace(Regex("[^A-Z0-9]+"), "_").trim('_').take(100)
        require(suffix.isNotEmpty()) { "Enter a repository label containing letters or numbers." }
        return (if (huggingFace) "HF_REPOSITORY_" else "GITHUB_REPOSITORY_") + suffix
    }

}
