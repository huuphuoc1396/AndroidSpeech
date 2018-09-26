package net.gotev.speech.exception

import android.speech.SpeechRecognizer

/**
 * Speech recognition exception.
 *
 * @author huuphuoc1396
 */
class SpeechRecognitionException(
        val code: Int
) : Exception(SpeechRecognitionException.getMessage(code)) {

    companion object {
        @JvmStatic
        private fun getMessage(code: Int): String {
            val message: String

            // these have been mapped from here:
            // https://developer.android.com/reference/android/speech/SpeechRecognizer.html
            when (code) {
                SpeechRecognizer.ERROR_AUDIO -> message = code.toString() + " - Audio recording error"

                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> message = code.toString() + " - Insufficient permissions. Request android.permission.RECORD_AUDIO"

                SpeechRecognizer.ERROR_CLIENT ->
                    // http://stackoverflow.com/questions/24995565/android-speechrecognizer-when-do-i-get-error-client-when-starting-the-voice-reco
                    message = code.toString() + " - Client side error. Maybe your internet connection is poor!"

                SpeechRecognizer.ERROR_NETWORK -> message = code.toString() + " - Network error"

                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> message = code.toString() + " - Network operation timed out"

                SpeechRecognizer.ERROR_NO_MATCH -> message = code.toString() + " - No recognition result matched. Try turning on partial results as a workaround."

                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> message = code.toString() + " - RecognitionService busy"

                SpeechRecognizer.ERROR_SERVER -> message = code.toString() + " - Server sends error status"

                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> message = code.toString() + " - No speech input"

                else -> message = code.toString() + " - Unknown exception"
            }

            return message
        }
    }
}
