@file:Suppress("unused", "UNUSED_PARAMETER")

package androidx.core.app

import android.app.Notification
import android.app.Service
import android.content.Context

class NotificationCompat {
    class Builder(context: Context, channelId: String) {
        fun setSmallIcon(icon: Int): Builder = this
        fun setContentTitle(title: CharSequence?): Builder = this
        fun setContentText(text: CharSequence?): Builder = this
        fun setOngoing(ongoing: Boolean): Builder = this
        fun build(): Notification = Notification()
    }
}

object ServiceCompat {
    @JvmStatic fun startForeground(service: Service, id: Int, notification: Notification, foregroundServiceType: Int) {}
}
