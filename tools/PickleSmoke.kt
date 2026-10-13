import com.forgeport.android.oauth.PythonCredentialsPickle
import java.io.File

fun main(args: Array<String>) {
    val output = File(args.firstOrNull() ?: error("output path required"))
    output.writeBytes(
        PythonCredentialsPickle.create(
            refreshToken = "refresh-test",
            clientId = "client-test.apps.googleusercontent.com",
            clientSecret = "secret-test",
            scopes = listOf("https://www.googleapis.com/auth/drive"),
        ),
    )
}
