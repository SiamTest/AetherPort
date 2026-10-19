import com.forgeport.android.oauth.PythonCredentialsPickle
import java.io.File
import java.time.Instant

fun main(args: Array<String>) {
    val output = File(args.firstOrNull() ?: error("output path required"))
    output.writeBytes(
        PythonCredentialsPickle.create(
            refreshToken = "refresh-test",
            clientId = "client-test.apps.googleusercontent.com",
            clientSecret = "secret-test",
            scopes = listOf("https://www.googleapis.com/auth/drive"),
            accessToken = if (args.getOrNull(1) == "full") "access-test" else null,
            expiry = if (args.getOrNull(1) == "full") Instant.parse("2100-01-02T03:04:05.123456Z") else null,
        ),
    )
}
