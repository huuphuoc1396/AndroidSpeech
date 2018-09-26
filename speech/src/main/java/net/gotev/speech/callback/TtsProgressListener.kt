package net.gotev.speech.callback

import android.content.Context
import android.os.Handler
import android.speech.tts.UtteranceProgressListener

import java.lang.ref.WeakReference

/**
 * @author huuphuoc1396
 */

class TtsProgressListener(
        context: Context,
        private val ttsCallbacks: MutableMap<String, TextToSpeechCallback>
) : UtteranceProgressListener() {

    private val contextWeakReference: WeakReference<Context> = WeakReference(context)

    override fun onStart(utteranceId: String) {
        val callback = ttsCallbacks[utteranceId]
        val context = contextWeakReference.get()

        if (callback != null && context != null) {
            Handler(context.mainLooper).post { callback.onStart() }
        }
    }

    override fun onDone(utteranceId: String) {
        val callback = ttsCallbacks[utteranceId]
        val context = contextWeakReference.get()
        if (callback != null && context != null) {
            Handler(context.mainLooper).post {
                callback.onCompleted()
                ttsCallbacks.remove(utteranceId)
            }
        }
    }

    override fun onError(utteranceId: String) {
        val callback = ttsCallbacks[utteranceId]
        val context = contextWeakReference.get()

        if (callback != null && context != null) {
            Handler(context.mainLooper).post {
                callback.onError()
                ttsCallbacks.remove(utteranceId)
            }
        }
    }
}
