package net.gotev.speech

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Utility methods.
 *
 * @author huuphuoc1396
 */
object SpeechUtil {

    /**
     * Opens the Google App page on Play Store
     * @param context application context
     */
    fun redirectUserToGoogleAppOnPlayStore(context: Context) {
        context.startActivity(Intent(Intent.ACTION_VIEW)
                .setData(Uri.parse("market://details?id=com.google.android.googlequicksearchbox")))
    }
}
