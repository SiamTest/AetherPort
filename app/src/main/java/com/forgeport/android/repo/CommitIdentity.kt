package com.forgeport.android.repo

internal data class CommitIdentity(val name: String, val email: String) {
    companion object {
        fun github(login: String, id: Long, displayName: String): CommitIdentity {
            require(login.matches(Regex("[A-Za-z0-9_-]+")) && id > 0) { "GitHub did not return a valid account identity." }
            // Attribute to the token owner while keeping their personal email private.
            return CommitIdentity(cleanName(displayName, login), "$id+$login@users.noreply.github.com")
        }

        fun huggingFace(username: String, displayName: String, email: String, verified: Boolean): CommitIdentity {
            require(username.matches(Regex("[A-Za-z0-9_-]+"))) { "Hugging Face did not return a valid account identity." }
            require(verified && email.matches(Regex("[^\\s<>@]+@[^\\s<>@]+\\.[^\\s<>@]+"))) {
                "Verify your Hugging Face account email before publishing."
            }
            return CommitIdentity(cleanName(displayName, username), email)
        }

        private fun cleanName(displayName: String, fallback: String): String = displayName
            .replace(Regex("[\\p{Cntrl}<>]"), " ").trim().take(200).ifBlank { fallback }
    }
}
