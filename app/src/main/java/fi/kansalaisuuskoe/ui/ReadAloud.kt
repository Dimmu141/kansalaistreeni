package fi.kansalaisuuskoe.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fi.kansalaisuuskoe.R
import java.util.Locale

/**
 * Reads study text aloud with the phone's own text-to-speech engine. Only voices that work without a
 * network connection are chosen, so the app's offline promise holds. One utterance plays at a time.
 */
class ReadAloud(context: Context) : TextToSpeech.OnInitListener {
    enum class Status { STARTING, READY, NO_ENGINE }

    var status by mutableStateOf(Status.STARTING); private set
    /** The ID passed to [speak] while it plays, so its button can show Stop. */
    var speaking by mutableStateOf<String?>(null); private set
    var slow = false
    private val engine = TextToSpeech(context.applicationContext, this)

    override fun onInit(result: Int) {
        if (result != TextToSpeech.SUCCESS) { status = Status.NO_ENGINE; return }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) {}
            override fun onDone(id: String) { if (speaking == id) speaking = null }
            @Deprecated("Required override") override fun onError(id: String) { if (speaking == id) speaking = null }
            override fun onStop(id: String, interrupted: Boolean) { if (speaking == id) speaking = null }
        })
        status = Status.READY
    }

    /** An installed offline voice for the language, best quality first; null if the phone has none. */
    private fun voiceFor(tag: String) = runCatching { engine.voices }.getOrNull().orEmpty()
        .filter { it.locale.language == tag && !it.isNetworkConnectionRequired &&
            TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
        .maxByOrNull { it.quality }

    fun speak(id: String, text: String, tag: String): Boolean {
        val voice = voiceFor(tag) ?: return false
        engine.voice = voice
        engine.setSpeechRate(if (slow) 0.75f else 1f)
        speaking = id
        return engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.SUCCESS || run { speaking = null; false }
    }

    fun stop() { engine.stop(); speaking = null }
    fun shutdown() { engine.stop(); engine.shutdown() }
}

/** Null when read-aloud is switched off in Settings. */
val LocalReadAloud = staticCompositionLocalOf<ReadAloud?> { null }

/**
 * A speaker button for one piece of study text. Tapping it again stops. If the phone has no offline voice
 * for the language, a dialog explains how to install one instead of failing silently.
 */
@Composable
internal fun SpeakButton(id: String, text: String, language: String, modifier: Modifier = Modifier) {
    val reader = LocalReadAloud.current ?: return
    val context = LocalContext.current
    var missing by remember { mutableStateOf(false) }
    val playing = reader.speaking == id
    // Leaving the screen or moving to the next question stops this text.
    DisposableEffect(reader, id) { onDispose { if (reader.speaking == id) reader.stop() } }
    val label = stringResource(if (playing) R.string.read_aloud_stop else R.string.read_aloud)
    IconButton(onClick = {
        when {
            playing -> reader.stop()
            // The engine binds asynchronously; a tap in the first moment is simply ignored.
            reader.status == ReadAloud.Status.STARTING -> Unit
            reader.status == ReadAloud.Status.NO_ENGINE || !reader.speak(id, text, language) -> missing = true
        }
    }, modifier = modifier.semantics { contentDescription = label }) {
        Icon(if (playing) KsIcons.Stop else KsIcons.Speaker, null, Modifier.size(24.dp), tint = Ks.colors.brand)
    }
    if (missing) AlertDialog(
        onDismissRequest = { missing = false },
        title = { Text(stringResource(R.string.voice_missing_title)) },
        text = { Text(stringResource(if (reader.status == ReadAloud.Status.NO_ENGINE) R.string.voice_no_engine
            else if (language == "sv") R.string.voice_missing_sv else R.string.voice_missing_fi)) },
        confirmButton = {
            if (reader.status != ReadAloud.Status.NO_ENGINE) TextButton(onClick = {
                missing = false
                try { context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (_: ActivityNotFoundException) { }
            }) { Text(stringResource(R.string.voice_install)) }
        },
        dismissButton = { TextButton(onClick = { missing = false }) { Text(stringResource(R.string.voice_close)) } },
    )
}

/** The question, then each option as its own sentence; true/false statements have no options to read. */
internal fun fi.kansalaisuuskoe.domain.Question.spokenText(): String {
    if (format == fi.kansalaisuuskoe.domain.QuestionFormat.TRUE_FALSE) return text
    fun sentence(value: String) = value.trim().let { if (it.last() in ".?!") it else "$it." }
    return (listOf(text) + options.map { it.text }).joinToString(" ") { sentence(it) }
}
