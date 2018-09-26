package net.gotev.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

import net.gotev.speech.callback.TextToSpeechCallback
import net.gotev.speech.callback.TtsProgressListener
import net.gotev.speech.exception.GoogleVoiceTypingDisabledException
import net.gotev.speech.exception.SpeechRecognitionException
import net.gotev.speech.exception.SpeechRecognitionNotAvailable
import net.gotev.speech.logger.Logger
import net.gotev.speech.callback.SpeechDelegate

import java.util.ArrayList
import java.util.Date
import java.util.HashMap
import java.util.Locale
import java.util.UUID

/**
 * Helper class to easily work with Android speech recognition.
 *
 * @author huuphuoc1396
 */
class Speech private constructor(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var callingPackage: String? = null
    private var preferOffline = false
    private var getPartialResults = true
    private var delegate: SpeechDelegate? = null
    /**
     * Check if voice recognition is currently active.
     *
     * @return true if the voice recognition is on, false otherwise
     */
    var isListening = false
        private set

    private val partialData = ArrayList<String>()
    private var unstableData: String? = null

    private var delayedStopListening: DelayedOperation? = null

    private var textToSpeech: TextToSpeech? = null
    private val ttsCallbacks = HashMap<String, TextToSpeechCallback>()
    private var locale = Locale.getDefault()
    private var ttsRate = 1.0f
    private var ttsPitch = 1.0f
    private var ttsQueueMode = TextToSpeech.QUEUE_FLUSH
    private var stopListeningDelayInMs: Long = 4000
    private var transitionMinimumDelay: Long = 1200
    private var lastActionTimestamp: Long = 0
    private var lastPartialResults: List<String>? = null

    private val ttsInitListener = TextToSpeech.OnInitListener { status ->
        when (status) {
            TextToSpeech.SUCCESS -> Logger.info(LOG_TAG, "TextToSpeech engine successfully started")

            TextToSpeech.ERROR -> Logger.error(LOG_TAG, "Error while initializing TextToSpeech engine!")

            else -> Logger.error(LOG_TAG, "Unknown TextToSpeech status: $status")
        }
    }

    private var ttsProgressListener: UtteranceProgressListener? = null

    private val recognitionListener = object : RecognitionListener {

        override fun onReadyForSpeech(bundle: Bundle) {
            partialData.clear()
            unstableData = null
        }

        override fun onBeginningOfSpeech() {

            delayedStopListening?.start(object : DelayedOperation.Operation {
                override fun onDelayedOperation() {
                    returnPartialResultsAndRecreateSpeechRecognizer()
                }

                override fun shouldExecuteDelayedOperation(): Boolean {
                    return true
                }
            })
        }

        override fun onRmsChanged(v: Float) {
            try {
                delegate?.onSpeechRmsChanged(v)
            } catch (exc: Throwable) {
                Logger.error(Speech::class.java.simpleName,
                        "Unhandled exception in delegate onSpeechRmsChanged", exc)
            }

        }

        override fun onPartialResults(bundle: Bundle) {
            delayedStopListening?.resetTimer()

            val partialResults = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val unstableData = bundle.getStringArrayList("android.speech.extra.UNSTABLE_TEXT")

            if (partialResults != null && !partialResults.isEmpty()) {
                partialData.clear()
                partialData.addAll(partialResults)
                this@Speech.unstableData = if (unstableData != null && !unstableData.isEmpty())
                    unstableData[0]
                else
                    null
                try {
                    if (lastPartialResults == null || lastPartialResults != partialResults) {
                        delegate?.onSpeechPartialResults(partialResults)
                        lastPartialResults = partialResults
                    }
                } catch (exc: Throwable) {
                    Logger.error(Speech::class.java.simpleName,
                            "Unhandled exception in delegate onSpeechPartialResults", exc)
                }

            }
        }

        override fun onResults(bundle: Bundle) {
            delayedStopListening?.cancel()

            val results = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

            val result: String

            if (results != null
                    && !results.isEmpty()
                    && results[0] != null
                    && !results[0].isEmpty()) {
                result = results[0]
            } else {
                Logger.info(Speech::class.java.simpleName, "No speech results, getting partial")
                result = partialResultsAsString
            }

            isListening = false

            try {
                delegate?.onSpeechResult(result.trim { it <= ' ' })
            } catch (exc: Throwable) {
                Logger.error(Speech::class.java.simpleName,
                        "Unhandled exception in delegate onSpeechResult", exc)
            }

            initSpeechRecognizer()
        }

        override fun onError(code: Int) {
            Logger.error(LOG_TAG, "Speech recognition error", SpeechRecognitionException(code))
            returnPartialResultsAndRecreateSpeechRecognizer()
        }

        override fun onBufferReceived(bytes: ByteArray) {

        }

        override fun onEndOfSpeech() {

        }

        override fun onEvent(i: Int, bundle: Bundle) {

        }
    }

    private val partialResultsAsString: String
        get() {
            val out = StringBuilder("")

            for (partial in partialData) {
                out.append(partial).append(" ")
            }

            if (unstableData != null && !unstableData!!.isEmpty())
                out.append(unstableData)

            return out.toString().trim { it <= ' ' }
        }

    init {
        initSpeechRecognizer()
        initTts(context)
    }

    private constructor(context: Context, callingPackage: String) : this(context) {
        this.callingPackage = callingPackage
    }

    private fun initSpeechRecognizer() {

        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            try {
                speechRecognizer?.destroy()
            } catch (exc: Throwable) {
                Logger.debug(Speech::class.java.simpleName,
                        "Non-Fatal error while destroying speech. " + exc.message)
            } finally {
                speechRecognizer = null
            }

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(recognitionListener)
            initDelayedStopListening(context)

        } else {
            speechRecognizer = null
        }

        partialData.clear()
        unstableData = null
    }

    private fun initTts(context: Context) {
        if (textToSpeech == null) {
            ttsProgressListener = TtsProgressListener(context, ttsCallbacks)
            textToSpeech = TextToSpeech(context.applicationContext, ttsInitListener)
            textToSpeech?.setOnUtteranceProgressListener(ttsProgressListener)
            textToSpeech?.language = locale
            textToSpeech?.setPitch(ttsPitch)
            textToSpeech?.setSpeechRate(ttsRate)
        }
    }

    private fun initDelayedStopListening(context: Context) {
        delayedStopListening?.let {
            it.cancel()
            delayedStopListening = null
        }
        delayedStopListening = DelayedOperation(context, stopListeningDelayInMs, "delayStopListening")
    }

    /**
     * Must be called inside Activity's onDestroy.
     */
    @Synchronized
    fun shutdown() {
        try {
            speechRecognizer?.stopListening()
        } catch (exc: Exception) {
            Logger.error(javaClass.simpleName, "Warning while de-initing speech recognizer", exc)
        }

        try {
            ttsCallbacks.clear()
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (exc: Exception) {
            Logger.error(javaClass.simpleName, "Warning while de-initing text to speech", exc)
        }
        unregisterDelegate()
        instance = null
    }

    /**
     * Starts voice recognition.
     *
     * @param delegate delegate which will receive speech recognition events and status
     * @throws SpeechRecognitionNotAvailable      when speech recognition is not available on the device
     * @throws GoogleVoiceTypingDisabledException when google voice typing is disabled on the device
     */
    @Throws(SpeechRecognitionNotAvailable::class, GoogleVoiceTypingDisabledException::class)
    fun startListening(delegate: SpeechDelegate?) {
        if (isListening) return

        if (speechRecognizer == null)
            throw SpeechRecognitionNotAvailable()

        if (delegate == null)
            throw IllegalArgumentException("delegate must be defined!")

        if (throttleAction()) {
            Logger.debug(javaClass.simpleName, "Hey man calm down! Throttling start to prevent disaster!")
            return
        }

        this.delegate = delegate

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, getPartialResults)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.language)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)

        if (callingPackage != null && callingPackage?.isEmpty() == false) {
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, callingPackage)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (exc: SecurityException) {
            throw GoogleVoiceTypingDisabledException()
        }

        isListening = true
        updateLastActionTimestamp()
        try {
            this.delegate?.onStartOfSpeech()
        } catch (exc: Throwable) {
            Logger.error(Speech::class.java.simpleName,
                    "Unhandled exception in delegate onStartOfSpeech", exc)
        }

    }

    private fun unregisterDelegate() {
        delegate = null
    }

    private fun updateLastActionTimestamp() {
        lastActionTimestamp = Date().time
    }

    private fun throttleAction(): Boolean {
        return Date().time <= lastActionTimestamp + transitionMinimumDelay
    }

    /**
     * Stops voice recognition listening.
     * This method does nothing if voice listening is not active
     */
    fun stopListening() {
        if (!isListening) return

        if (throttleAction()) {
            Logger.debug(javaClass.simpleName, "Hey man calm down! Throttling stop to prevent disaster!")
            return
        }

        isListening = false
        updateLastActionTimestamp()
        returnPartialResultsAndRecreateSpeechRecognizer()
    }

    private fun returnPartialResultsAndRecreateSpeechRecognizer() {
        isListening = false
        try {
            delegate?.onSpeechResult(partialResultsAsString)
        } catch (exc: Throwable) {
            Logger.error(Speech::class.java.simpleName,
                    "Unhandled exception in delegate onSpeechResult", exc)
        }

        // recreate the speech recognizer
        initSpeechRecognizer()
    }

    /**
     * Uses text to speech to transform a written message into a sound.
     *
     * @param message  message to play
     * @param callback callback which will receive progress status of the operation
     */
    @JvmOverloads
    fun say(message: String, callback: TextToSpeechCallback? = null) {

        val utteranceId = UUID.randomUUID().toString()

        if (callback != null) {
            ttsCallbacks[utteranceId] = callback
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textToSpeech?.speak(message, ttsQueueMode, null, utteranceId)
        } else {
            val params = HashMap<String, String>()
            params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = utteranceId
            textToSpeech?.speak(message, ttsQueueMode, params)
        }
    }

    /**
     * Stops text to speech.
     */
    fun stopTextToSpeech() {
        textToSpeech?.stop()
    }

    /**
     * Set whether to only use an offline speech recognition engine.
     * The default is false, meaning that either network or offline recognition engines may be used.
     *
     * @param preferOffline true to prefer offline engine, false to use either one of the two
     * @return speech instance
     */
    fun setPreferOffline(preferOffline: Boolean): Speech {
        this.preferOffline = preferOffline
        return this
    }

    /**
     * Set whether partial results should be returned by the recognizer as the user speaks
     * (default is true). The server may ignore a request for partial results in some or all cases.
     *
     * @param getPartialResults true to get also partial recognition results, false otherwise
     * @return speech instance
     */
    fun setGetPartialResults(getPartialResults: Boolean): Speech {
        this.getPartialResults = getPartialResults
        return this
    }

    /**
     * Sets text to speech and recognition language.
     * Defaults to device language setting.
     *
     * @param locale new locale
     * @return speech instance
     */
    fun setLocale(locale: Locale): Speech {
        this.locale = locale
        this.textToSpeech?.language = locale
        return this
    }

    /**
     * Sets the speech rate. This has no effect on any pre-recorded speech.
     *
     * @param rate Speech rate. 1.0 is the normal speech rate, lower values slow down the speech
     * (0.5 is half the normal speech rate), greater values accelerate it
     * (2.0 is twice the normal speech rate).
     * @return speech instance
     */
    fun setTextToSpeechRate(rate: Float): Speech {
        ttsRate = rate
        textToSpeech?.setSpeechRate(rate)
        return this
    }

    /**
     * Sets the speech pitch for the TextToSpeech engine.
     * This has no effect on any pre-recorded speech.
     *
     * @param pitch Speech pitch. 1.0 is the normal pitch, lower values lower the tone of the
     * synthesized voice, greater values increase it.
     * @return speech instance
     */
    fun setTextToSpeechPitch(pitch: Float): Speech {
        ttsPitch = pitch
        textToSpeech?.setPitch(pitch)
        return this
    }

    /**
     * Sets the idle timeout after which the listening will be automatically stopped.
     *
     * @param milliseconds timeout in milliseconds
     * @return speech instance
     */
    fun setStopListeningAfterInactivity(milliseconds: Long): Speech {
        stopListeningDelayInMs = milliseconds
        initDelayedStopListening(context)
        return this
    }

    /**
     * Sets the minimum interval between start/stop events. This is useful to prevent
     * monkey input from users.
     *
     * @param milliseconds minimum interval betweeb state change in milliseconds
     * @return speech instance
     */
    fun setTransitionMinimumDelay(milliseconds: Long): Speech {
        transitionMinimumDelay = milliseconds
        return this
    }

    /**
     * Sets the text to speech queue mode.
     * By default is TextToSpeech.QUEUE_FLUSH, which is faster, because it clears all the
     * messages before speaking the new one. TextToSpeech.QUEUE_ADD adds the last message
     * to speak in the queue, without clearing the messages that have been added.
     *
     * @param mode It can be either TextToSpeech.QUEUE_ADD or TextToSpeech.QUEUE_FLUSH.
     * @return speech instance
     */
    fun setTextToSpeechQueueMode(mode: Int): Speech {
        ttsQueueMode = mode
        return this
    }

    companion object {

        private val LOG_TAG = Speech::class.java.simpleName

        private var instance: Speech? = null

        /**
         * Initializes speech recognition.
         *
         * @param context application context
         * @return speech instance
         */
        @JvmStatic
        fun init(context: Context): Speech {
            if (instance == null) {
                instance = Speech(context)
            }

            return instance!!
        }

        /**
         * Initializes speech recognition.
         *
         * @param context        application context
         * @param callingPackage The extra key used in an intent to the speech recognizer for
         * voice search. Not generally to be used by developers.
         * The system search dialog uses this, for example, to set a calling
         * package for identification by a voice search API.
         * If this extra is set by anyone but the system process,
         * it should be overridden by the voice search implementation.
         * By passing null or empty string (which is the default) you are
         * not overriding the calling package
         * @return speech instance
         */
        @JvmStatic
        fun init(context: Context, callingPackage: String): Speech {
            if (instance == null) {
                instance = Speech(context, callingPackage)
            }

            return instance!!
        }

        /**
         * Gets speech recognition instance.
         *
         * @return SpeechRecognition instance
         */
        @JvmStatic
        fun getInstance(): Speech {
            if (instance == null) {
                throw IllegalStateException("Speech recognition has not been initialized! call init method first!")
            }

            return instance!!
        }
    }

}