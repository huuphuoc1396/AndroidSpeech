package net.gotev.speech;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import net.gotev.speech.callback.TextToSpeechCallback;
import net.gotev.speech.callback.TtsProgressListener;
import net.gotev.speech.exception.GoogleVoiceTypingDisabledException;
import net.gotev.speech.exception.SpeechRecognitionException;
import net.gotev.speech.exception.SpeechRecognitionNotAvailable;
import net.gotev.speech.logger.Logger;
import net.gotev.speech.callback.SpeechDelegate;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Helper class to easily work with Android speech recognition.
 *
 * @author Aleksandar Gotev
 */
public class Speech {

    private static final String LOG_TAG = Speech.class.getSimpleName();

    private static Speech instance = null;

    private SpeechRecognizer speechRecognizer;
    private String callingPackage;
    private boolean preferOffline = false;
    private boolean getPartialResults = true;
    private SpeechDelegate delegate;
    private boolean isListening = false;

    private final List<String> partialData = new ArrayList<>();
    private String unstableData;

    private DelayedOperation delayedStopListening;
    private Context context;

    private TextToSpeech textToSpeech;
    private final Map<String, TextToSpeechCallback> ttsCallbacks = new HashMap<>();
    private Locale locale = Locale.getDefault();
    private float ttsRate = 1.0f;
    private float ttsPitch = 1.0f;
    private int ttsQueueMode = TextToSpeech.QUEUE_FLUSH;
    private long stopListeningDelayInMs = 4000;
    private long transitionMinimumDelay = 1200;
    private long lastActionTimestamp;
    private List<String> lastPartialResults = null;

    private final TextToSpeech.OnInitListener ttsInitListener = new TextToSpeech.OnInitListener() {
        @Override
        public void onInit(final int status) {
            switch (status) {
                case TextToSpeech.SUCCESS:
                    Logger.Companion.info(LOG_TAG, "TextToSpeech engine successfully started");
                    break;

                case TextToSpeech.ERROR:
                    Logger.Companion.error(LOG_TAG, "Error while initializing TextToSpeech engine!");
                    break;

                default:
                    Logger.Companion.error(LOG_TAG, "Unknown TextToSpeech status: " + status);
                    break;
            }
        }
    };

    private UtteranceProgressListener ttsProgressListener;

    private final RecognitionListener mListener = new RecognitionListener() {

        @Override
        public void onReadyForSpeech(final Bundle bundle) {
            partialData.clear();
            unstableData = null;
        }

        @Override
        public void onBeginningOfSpeech() {

            delayedStopListening.start(new DelayedOperation.Operation() {
                @Override
                public void onDelayedOperation() {
                    returnPartialResultsAndRecreateSpeechRecognizer();
                }

                @Override
                public boolean shouldExecuteDelayedOperation() {
                    return true;
                }
            });
        }

        @Override
        public void onRmsChanged(final float v) {
            try {
                if (delegate != null)
                    delegate.onSpeechRmsChanged(v);
            } catch (final Throwable exc) {
                Logger.Companion.error(Speech.class.getSimpleName(),
                        "Unhandled exception in delegate onSpeechRmsChanged", exc);
            }

        }

        @Override
        public void onPartialResults(final Bundle bundle) {
            delayedStopListening.resetTimer();

            final List<String> partialResults = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            final List<String> unstableData = bundle.getStringArrayList("android.speech.extra.UNSTABLE_TEXT");

            if (partialResults != null && !partialResults.isEmpty()) {
                partialData.clear();
                partialData.addAll(partialResults);
                Speech.this.unstableData = unstableData != null && !unstableData.isEmpty()
                        ? unstableData.get(0) : null;
                try {
                    if (lastPartialResults == null || !lastPartialResults.equals(partialResults)) {
                        if (delegate != null)
                            delegate.onSpeechPartialResults(partialResults);
                        lastPartialResults = partialResults;
                    }
                } catch (final Throwable exc) {
                    Logger.Companion.error(Speech.class.getSimpleName(),
                            "Unhandled exception in delegate onSpeechPartialResults", exc);
                }
            }
        }

        @Override
        public void onResults(final Bundle bundle) {
            delayedStopListening.cancel();

            final List<String> results = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

            final String result;

            if (results != null && !results.isEmpty()
                    && results.get(0) != null && !results.get(0).isEmpty()) {
                result = results.get(0);
            } else {
                Logger.Companion.info(Speech.class.getSimpleName(), "No speech results, getting partial");
                result = getPartialResultsAsString();
            }

            isListening = false;

            try {
                if (delegate != null)
                    delegate.onSpeechResult(result.trim());
            } catch (final Throwable exc) {
                Logger.Companion.error(Speech.class.getSimpleName(),
                        "Unhandled exception in delegate onSpeechResult", exc);
            }

            initSpeechRecognizer(context);
        }

        @Override
        public void onError(final int code) {
            Logger.Companion.error(LOG_TAG, "Speech recognition error", new SpeechRecognitionException(code));
            returnPartialResultsAndRecreateSpeechRecognizer();
        }

        @Override
        public void onBufferReceived(final byte[] bytes) {

        }

        @Override
        public void onEndOfSpeech() {

        }

        @Override
        public void onEvent(final int i, final Bundle bundle) {

        }
    };

    private Speech(final Context context) {
        initSpeechRecognizer(context);
        initTts(context);
    }

    private Speech(final Context context, final String callingPackage) {
        initSpeechRecognizer(context);
        initTts(context);
        this.callingPackage = callingPackage;
    }

    private void initSpeechRecognizer(final Context context) {
        if (context == null)
            throw new IllegalArgumentException("context must be defined!");

        this.context = context;

        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            if (speechRecognizer != null) {
                try {
                    speechRecognizer.destroy();
                } catch (final Throwable exc) {
                    Logger.Companion.debug(Speech.class.getSimpleName(),
                            "Non-Fatal error while destroying speech. " + exc.getMessage());
                } finally {
                    speechRecognizer = null;
                }
            }

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
            speechRecognizer.setRecognitionListener(mListener);
            initDelayedStopListening(context);

        } else {
            speechRecognizer = null;
        }

        partialData.clear();
        unstableData = null;
    }

    private void initTts(final Context context) {
        if (textToSpeech == null) {
            ttsProgressListener = new TtsProgressListener(this.context, ttsCallbacks);
            textToSpeech = new TextToSpeech(context.getApplicationContext(), ttsInitListener);
            textToSpeech.setOnUtteranceProgressListener(ttsProgressListener);
            textToSpeech.setLanguage(locale);
            textToSpeech.setPitch(ttsPitch);
            textToSpeech.setSpeechRate(ttsRate);
        }
    }

    private void initDelayedStopListening(final Context context) {
        if (delayedStopListening != null) {
            delayedStopListening.cancel();
            delayedStopListening = null;
        }

        delayedStopListening = new DelayedOperation(context, stopListeningDelayInMs, "delayStopListening");
    }

    /**
     * Initializes speech recognition.
     *
     * @param context application context
     * @return speech instance
     */
    public static Speech init(final Context context) {
        if (instance == null) {
            instance = new Speech(context);
        }

        return instance;
    }

    /**
     * Initializes speech recognition.
     *
     * @param context        application context
     * @param callingPackage The extra key used in an intent to the speech recognizer for
     *                       voice search. Not generally to be used by developers.
     *                       The system search dialog uses this, for example, to set a calling
     *                       package for identification by a voice search API.
     *                       If this extra is set by anyone but the system process,
     *                       it should be overridden by the voice search implementation.
     *                       By passing null or empty string (which is the default) you are
     *                       not overriding the calling package
     * @return speech instance
     */
    public static Speech init(final Context context, final String callingPackage) {
        if (instance == null) {
            instance = new Speech(context, callingPackage);
        }

        return instance;
    }

    /**
     * Must be called inside Activity's onDestroy.
     */
    public synchronized void shutdown() {
        if (speechRecognizer != null) {
            try {
                speechRecognizer.stopListening();
            } catch (final Exception exc) {
                Logger.Companion.error(getClass().getSimpleName(), "Warning while de-initing speech recognizer", exc);
            }
        }

        if (textToSpeech != null) {
            try {
                ttsCallbacks.clear();
                textToSpeech.stop();
                textToSpeech.shutdown();
            } catch (final Exception exc) {
                Logger.Companion.error(getClass().getSimpleName(), "Warning while de-initing text to speech", exc);
            }
        }

        unregisterDelegate();
        instance = null;
    }

    /**
     * Gets speech recognition instance.
     *
     * @return SpeechRecognition instance
     */
    public static Speech getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Speech recognition has not been initialized! call init method first!");
        }

        return instance;
    }

    /**
     * Starts voice recognition.
     *
     * @param delegate delegate which will receive speech recognition events and status
     * @throws SpeechRecognitionNotAvailable      when speech recognition is not available on the device
     * @throws GoogleVoiceTypingDisabledException when google voice typing is disabled on the device
     */
    public void startListening(final SpeechDelegate delegate)
            throws SpeechRecognitionNotAvailable, GoogleVoiceTypingDisabledException {
        if (isListening) return;

        if (speechRecognizer == null)
            throw new SpeechRecognitionNotAvailable();

        if (delegate == null)
            throw new IllegalArgumentException("delegate must be defined!");

        if (throttleAction()) {
            Logger.Companion.debug(getClass().getSimpleName(), "Hey man calm down! Throttling start to prevent disaster!");
            return;
        }

        this.delegate = delegate;

        final Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, getPartialResults)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.getLanguage())
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

        if (callingPackage != null && !callingPackage.isEmpty()) {
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, callingPackage);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline);
        }

        try {
            speechRecognizer.startListening(intent);
        } catch (final SecurityException exc) {
            throw new GoogleVoiceTypingDisabledException();
        }

        isListening = true;
        updateLastActionTimestamp();

        try {
            if (this.delegate != null)
                this.delegate.onStartOfSpeech();
        } catch (final Throwable exc) {
            Logger.Companion.error(Speech.class.getSimpleName(),
                    "Unhandled exception in delegate onStartOfSpeech", exc);
        }

    }

    private void unregisterDelegate() {
        delegate = null;
    }

    private void updateLastActionTimestamp() {
        lastActionTimestamp = new Date().getTime();
    }

    private boolean throttleAction() {
        return (new Date().getTime() <= (lastActionTimestamp + transitionMinimumDelay));
    }

    /**
     * Stops voice recognition listening.
     * This method does nothing if voice listening is not active
     */
    public void stopListening() {
        if (!isListening) return;

        if (throttleAction()) {
            Logger.Companion.debug(getClass().getSimpleName(), "Hey man calm down! Throttling stop to prevent disaster!");
            return;
        }

        isListening = false;
        updateLastActionTimestamp();
        returnPartialResultsAndRecreateSpeechRecognizer();
    }

    private String getPartialResultsAsString() {
        final StringBuilder out = new StringBuilder("");

        for (final String partial : partialData) {
            out.append(partial).append(" ");
        }

        if (unstableData != null && !unstableData.isEmpty())
            out.append(unstableData);

        return out.toString().trim();
    }

    private void returnPartialResultsAndRecreateSpeechRecognizer() {
        isListening = false;
        try {
            if (delegate != null)
                delegate.onSpeechResult(getPartialResultsAsString());
        } catch (final Throwable exc) {
            Logger.Companion.error(Speech.class.getSimpleName(),
                    "Unhandled exception in delegate onSpeechResult", exc);
        }

        // recreate the speech recognizer
        initSpeechRecognizer(context);
    }

    /**
     * Check if voice recognition is currently active.
     *
     * @return true if the voice recognition is on, false otherwise
     */
    public boolean isListening() {
        return isListening;
    }

    /**
     * Uses text to speech to transform a written message into a sound.
     *
     * @param message message to play
     */
    public void say(final String message) {
        say(message, null);
    }

    /**
     * Uses text to speech to transform a written message into a sound.
     *
     * @param message  message to play
     * @param callback callback which will receive progress status of the operation
     */
    public void say(final String message, final TextToSpeechCallback callback) {

        final String utteranceId = UUID.randomUUID().toString();

        if (callback != null) {
            ttsCallbacks.put(utteranceId, callback);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textToSpeech.speak(message, ttsQueueMode, null, utteranceId);
        } else {
            final HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            textToSpeech.speak(message, ttsQueueMode, params);
        }
    }

    /**
     * Stops text to speech.
     */
    public void stopTextToSpeech() {
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
    }

    /**
     * Set whether to only use an offline speech recognition engine.
     * The default is false, meaning that either network or offline recognition engines may be used.
     *
     * @param preferOffline true to prefer offline engine, false to use either one of the two
     * @return speech instance
     */
    public Speech setPreferOffline(final boolean preferOffline) {
        this.preferOffline = preferOffline;
        return this;
    }

    /**
     * Set whether partial results should be returned by the recognizer as the user speaks
     * (default is true). The server may ignore a request for partial results in some or all cases.
     *
     * @param getPartialResults true to get also partial recognition results, false otherwise
     * @return speech instance
     */
    public Speech setGetPartialResults(final boolean getPartialResults) {
        this.getPartialResults = getPartialResults;
        return this;
    }

    /**
     * Sets text to speech and recognition language.
     * Defaults to device language setting.
     *
     * @param locale new locale
     * @return speech instance
     */
    public Speech setLocale(final Locale locale) {
        this.locale = locale;
        if (textToSpeech != null)
            textToSpeech.setLanguage(locale);
        return this;
    }

    /**
     * Sets the speech rate. This has no effect on any pre-recorded speech.
     *
     * @param rate Speech rate. 1.0 is the normal speech rate, lower values slow down the speech
     *             (0.5 is half the normal speech rate), greater values accelerate it
     *             (2.0 is twice the normal speech rate).
     * @return speech instance
     */
    public Speech setTextToSpeechRate(final float rate) {
        ttsRate = rate;
        textToSpeech.setSpeechRate(rate);
        return this;
    }

    /**
     * Sets the speech pitch for the TextToSpeech engine.
     * This has no effect on any pre-recorded speech.
     *
     * @param pitch Speech pitch. 1.0 is the normal pitch, lower values lower the tone of the
     *              synthesized voice, greater values increase it.
     * @return speech instance
     */
    public Speech setTextToSpeechPitch(final float pitch) {
        ttsPitch = pitch;
        textToSpeech.setPitch(pitch);
        return this;
    }

    /**
     * Sets the idle timeout after which the listening will be automatically stopped.
     *
     * @param milliseconds timeout in milliseconds
     * @return speech instance
     */
    public Speech setStopListeningAfterInactivity(final long milliseconds) {
        stopListeningDelayInMs = milliseconds;
        initDelayedStopListening(context);
        return this;
    }

    /**
     * Sets the minimum interval between start/stop events. This is useful to prevent
     * monkey input from users.
     *
     * @param milliseconds minimum interval betweeb state change in milliseconds
     * @return speech instance
     */
    public Speech setTransitionMinimumDelay(final long milliseconds) {
        transitionMinimumDelay = milliseconds;
        return this;
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
    public Speech setTextToSpeechQueueMode(final int mode) {
        ttsQueueMode = mode;
        return this;
    }

}
