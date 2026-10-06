package se.stilla.launcher.media

import android.service.notification.NotificationListenerService
import se.stilla.launcher.StillaApp

/**
 * Only here so Android lets Stilla see what's playing (song, artist, play/pause).
 * It doesn't read, hide or change any notification.
 */
class MediaListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        (application as StillaApp).container.media.start()
    }
}
