package com.forgeport.android.oauth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ResultReceiver
import com.forgeport.android.MainActivity
import com.forgeport.android.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Keep the loopback listener runnable while the user completes consent in a browser. */
class GoogleOAuthCallbackService : Service() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ready = intent?.getParcelableExtra<ResultReceiver>(READY)
        try {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Google sign-in", NotificationManager.IMPORTANCE_LOW))
            val returnToApp = PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            startForeground(307, Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_aetherport_monochrome)
                .setContentTitle("Complete Google sign-in")
                .setContentText("Return to AetherPort after approving access in your browser.")
                .setContentIntent(returnToApp).setOngoing(true).build())
            ready?.send(1, Bundle.EMPTY)
            // Stop before Android's three-minute shortService limit, even if the caller disappears.
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({ stopSelf() }, 165_000)
        } catch (_: Exception) {
            ready?.send(0, Bundle.EMPTY)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int) { stopSelf() }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "google_oauth_callback"
        private const val READY = "callback_ready"

        suspend fun start(context: Context) = withContext(Dispatchers.Main.immediate) {
            val ready = CompletableDeferred<Boolean>()
            val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    ready.complete(resultCode == 1)
                }
            }
            try {
                context.startForegroundService(Intent(context, GoogleOAuthCallbackService::class.java).putExtra(READY, receiver))
                check(withTimeoutOrNull(5_000) { ready.await() } == true) {
                    "Android could not keep Google sign-in active. Return to AetherPort and try again."
                }
            } catch (failure: Exception) {
                stop(context)
                throw failure
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GoogleOAuthCallbackService::class.java))
        }
    }
}
