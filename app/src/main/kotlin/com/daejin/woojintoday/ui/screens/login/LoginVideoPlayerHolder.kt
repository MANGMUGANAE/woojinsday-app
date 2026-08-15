package com.daejin.woojintoday.ui.screens.login

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

private const val VIDEO_URL = "https://www.daejin.ac.kr/sites/daejin/video/dju_movie.mp4"

/**
 * Single shared ExoPlayer instance for the login screen's looping promo video. [prepare] is
 * called from the splash screen so buffering starts before the login screen even appears;
 * [player] just returns the already-warming instance.
 */
object LoginVideoPlayerHolder {
    @Volatile
    private var player: ExoPlayer? = null

    fun prepare(context: Context) {
        if (player != null) return
        synchronized(this) {
            if (player != null) return
            player = ExoPlayer.Builder(context.applicationContext).build().apply {
                setMediaItem(MediaItem.fromUri(VIDEO_URL))
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 0f
                playWhenReady = true
                prepare()
            }
        }
    }

    fun player(context: Context): ExoPlayer {
        prepare(context)
        return player!!
    }
}
