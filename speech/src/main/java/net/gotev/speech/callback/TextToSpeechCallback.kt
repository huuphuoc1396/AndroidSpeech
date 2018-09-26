package net.gotev.speech.callback

/**
 * Contains the methods which are called to notify text to speech progress status.
 *
 * @author huuphuoc1396
 */
interface TextToSpeechCallback {
    fun onStart()
    fun onCompleted()
    fun onError()
}
