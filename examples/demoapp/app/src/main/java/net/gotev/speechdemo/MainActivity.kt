package net.gotev.speechdemo

import android.Manifest
import android.content.DialogInterface
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Toast

import com.tbruyelle.rxpermissions.RxPermissions
import kotlinx.android.synthetic.main.activity_main.*

import net.gotev.speech.exception.GoogleVoiceTypingDisabledException
import net.gotev.speech.Speech
import net.gotev.speech.callback.SpeechDelegate
import net.gotev.speech.exception.SpeechRecognitionNotAvailable
import net.gotev.speech.SpeechUtil
import net.gotev.speech.callback.TextToSpeechCallback
import net.gotev.toyproject.R

class MainActivity : AppCompatActivity(), SpeechDelegate {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Speech.getInstance().setTransitionMinimumDelay(0)

        startSpeech.setOnClickListener { startSpeech() }
        speak.setOnClickListener { speak() }
    }

    override fun onDestroy() {
        super.onDestroy()
        Speech.getInstance().shutdown()
    }

    private fun startSpeech() {
        if (Speech.getInstance().isListening) {
            Speech.getInstance().stopListening()
        } else {
            RxPermissions.getInstance(this)
                    .request(Manifest.permission.RECORD_AUDIO)
                    .subscribe { granted ->
                        if (granted) { // Always true pre-M
                            onRecordAudioPermissionGranted()
                        } else {
                            Toast.makeText(
                                    this@MainActivity,
                                    R.string.permission_required,
                                    Toast.LENGTH_LONG
                            ).show()
                        }
                    }
        }
    }

    private fun onRecordAudioPermissionGranted() {
        startSpeech.visibility = View.GONE
        waveView.visibility = View.VISIBLE

        try {
            Speech.getInstance().stopTextToSpeech()
            Speech.getInstance().startListening(this@MainActivity)

        } catch (exc: SpeechRecognitionNotAvailable) {
            showSpeechNotSupportedDialog()

        } catch (exc: GoogleVoiceTypingDisabledException) {
            showEnableGoogleVoiceTyping()
        }

    }

    private fun speak() {
        if (textToSpeech.text.toString().trim { it <= ' ' }.isEmpty()) {
            Toast.makeText(this, R.string.input_something, Toast.LENGTH_LONG).show()
            return
        }

        Speech.getInstance().say(textToSpeech.text.toString().trim { it <= ' ' }, object : TextToSpeechCallback {
            override fun onStart() {
                Toast.makeText(this@MainActivity, "TTS onStart", Toast.LENGTH_SHORT).show()
            }

            override fun onCompleted() {
                Toast.makeText(this@MainActivity, "TTS onCompleted", Toast.LENGTH_SHORT).show()
            }

            override fun onError() {
                Toast.makeText(this@MainActivity, "TTS onError", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onStartOfSpeech() {

    }

    override fun onSpeechRmsChanged(value: Float) {
        waveView.updateAmplitude(value / 20)
        Log.d(javaClass.simpleName, "Speech recognition rms is now " + value + "dB")
    }

    override fun onSpeechResult(result: String) {

        startSpeech.visibility = View.VISIBLE
        waveView.visibility = View.GONE

        text.text = result

        if (result.isEmpty()) {
            Speech.getInstance().say(getString(R.string.repeat))

        } else {
            Speech.getInstance().say(result)
        }
    }

    override fun onSpeechPartialResults(results: List<String>) {
        text.text = ""
        for (partial in results) {
            text.append("$partial ")
        }
    }

    private fun showSpeechNotSupportedDialog() {
        val dialogClickListener = DialogInterface.OnClickListener { _, which ->
            when (which) {
                DialogInterface.BUTTON_POSITIVE -> SpeechUtil.redirectUserToGoogleAppOnPlayStore(this@MainActivity)

                DialogInterface.BUTTON_NEGATIVE -> {
                }
            }
        }

        val builder = AlertDialog.Builder(this)
        builder.setMessage(R.string.speech_not_available)
                .setCancelable(false)
                .setPositiveButton(R.string.yes, dialogClickListener)
                .setNegativeButton(R.string.no, dialogClickListener)
                .show()
    }

    private fun showEnableGoogleVoiceTyping() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage(R.string.enable_google_voice_typing)
                .setCancelable(false)
                .setPositiveButton(R.string.yes) { dialogInterface, i ->
                    // do nothing
                }
                .show()
    }

}
