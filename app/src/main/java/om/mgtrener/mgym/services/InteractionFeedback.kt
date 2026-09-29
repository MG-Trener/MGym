package om.mgtrener.mgym.services

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator

class InteractionFeedback(private val context:Context) {
    private var tone:ToneGenerator?=null
    fun play(sound:Boolean,haptic:Boolean,complete:Boolean=false) {
        if(haptic) {
            @Suppress("DEPRECATION")
            val vibrator=context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if(vibrator.hasVibrator()) vibrator.vibrate(VibrationEffect.createPredefined(if(complete) VibrationEffect.EFFECT_DOUBLE_CLICK else VibrationEffect.EFFECT_TICK))
        }
        val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if(sound && audio.ringerMode==AudioManager.RINGER_MODE_NORMAL && audio.getStreamVolume(AudioManager.STREAM_MUSIC)>0) {
            runCatching {
                if(tone==null) tone=ToneGenerator(AudioManager.STREAM_MUSIC,18)
                tone?.startTone(if(complete) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP,if(complete) 110 else 40)
            }
        }
    }
    fun close() {tone?.release();tone=null}
}
