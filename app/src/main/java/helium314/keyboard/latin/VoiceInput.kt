// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import helium314.keyboard.compat.locale
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.permissions.PermissionsUtil
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getSecondaryLocales

/**
 * Dictation through the system speech recognizer, without leaving the keyboard.
 * Words heard so far show as underlined composing text; the final phrase is committed when the speaker pauses.
 */
class VoiceInput(private val ime: LatinIME) : RecognitionListener {
    private var recognizer: SpeechRecognizer? = null
    private var prefix = ""
    private var capitalize = false

    var isListening = false
        private set

    fun toggle() = if (isListening) recognizer?.stopListening() ?: Unit else start()

    /** Stop without waiting for a final result; whatever was heard so far stays in the field. */
    fun cancel() {
        if (!isListening) return
        recognizer?.cancel()
        finish()
    }

    fun destroy() {
        cancel()
        recognizer?.destroy()
        recognizer = null
    }

    private fun start() {
        if (!PermissionsUtil.checkAllPermissionsGranted(ime, Manifest.permission.RECORD_AUDIO)) {
            VoicePermissionActivity.launch(ime)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(ime)) {
            Toast.makeText(ime, R.string.voice_input_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        ime.mInputLogic.finishInput() // commit the word being typed, dictation continues after it
        val before = ime.mInputLogic.mConnection.getTextBeforeCursor(1, 0)
        prefix = if (before.isNullOrEmpty() || before[0].isWhitespace()) "" else " "
        capitalize = ime.currentAutoCapsState != Constants.TextUtils.CAP_MODE_OFF

        val subtype = RichInputMethodManager.getInstance().currentSubtype
        val languages = listOf(subtype.locale) + getSecondaryLocales(subtype.rawSubtype.extraValue)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languages.first().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ime.packageName)
            // a layout with two languages (English + Czech) lets the recognizer follow whichever is spoken
            if (languages.size > 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val tags = ArrayList(languages.map { it.toLanguageTag() })
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, tags)
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, tags)
            }
        }
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(ime).also {
            it.setRecognitionListener(this)
            recognizer = it
        }
        r.startListening(intent)
        setListening(true)
    }

    private fun show(text: String) {
        if (text.isBlank()) return
        val shown = if (capitalize) text.replaceFirstChar { it.titlecase(ime.resources.configuration.locale()) } else text
        ime.mInputLogic.mConnection.setComposingText(prefix + shown, 1)
    }

    private fun finish() {
        ime.mInputLogic.mConnection.finishComposingText()
        setListening(false)
    }

    private fun setListening(listening: Boolean) {
        isListening = listening
        ime.onVoiceListeningChanged(listening)
    }

    private fun Bundle.bestText() = getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    override fun onPartialResults(partialResults: Bundle) = show(partialResults.bestText())

    override fun onResults(results: Bundle) {
        show(results.bestText())
        finish()
    }

    override fun onError(error: Int) {
        Log.i(TAG, "speech recognizer error $error")
        if (!isListening) return
        finish()
        when (error) {
            // silence is not an error worth a message
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_CLIENT -> Unit
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoicePermissionActivity.launch(ime)
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                Toast.makeText(ime, R.string.voice_input_language_unsupported, Toast.LENGTH_SHORT).show()
            else -> Toast.makeText(ime, R.string.voice_input_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    companion object {
        private const val TAG = "VoiceInput"
    }
}
