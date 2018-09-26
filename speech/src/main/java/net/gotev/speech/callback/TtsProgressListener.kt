package net.gotev.speech.callback

import android.content.Context
import android.os.Handler
import android.speech.tts.UtteranceProgressListener

import net.gotev.speech.callback.TextToSpeechCallback

import java.lang.ref.WeakReference

/**
 * @author huuphuoc1396
 */

class TtsProgressListener(
        context: Context,
        private val mTtsCallbacks: MutableMap<String, TextToSpeechCallback>
) : UtteranceProgressListener() {

    private val contextWeakReference: WeakReference<Context> = WeakReference(context)

    override fun onStart(utteranceId: String) {
        val callback = mTtsCallbacks[utteranceId]
        val context = contextWeakReference.get()

        if (callback != null && context != null) {
            Handler(context.mainLooper).post { callback.onStart() }
        }
    }

    override fun onDone(utteranceId: String) {
        val callback = mTtsCallbacks[utteranceId]
        val context = contextWeakReference.get()
        if (callback != null && context != null) {
            Handler(context.mainLooper).post {
                callback.onCompleted()
                mTtsCallbacks.remove(utteranceId)
            }
        }
    }

    override fun onError(utteranceId: String) {
        val callback = mTtsCallbacks[utteranceId]
        val context = contextWeakReference.get()

        if (callback != null && context != null) {
            Handler(context.mainLooper).post {
                callback.onError()
                mTtsCallbacks.remove(utteranceId)
            }
        }
    }
}
