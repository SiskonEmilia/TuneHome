package cn.edu.sysu.emilia.tunehome.Services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import cn.edu.sysu.emilia.tunehome.Model.Song
import cn.edu.sysu.emilia.tunehome.Views.PlayPosition
import cn.edu.sysu.emilia.tunehome.Views.PlayStatus
import org.greenrobot.eventbus.EventBus
import java.util.*
import kotlin.random.Random


class PlayerService: Service(), MediaPlayer.OnPreparedListener,
    MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener {

    companion object {
        private const val LOG_TAG = "cn.edu.sysu.emilia.LOG_TAG"
    }

    // MediaSession
    private var mMediaSessionManager : MediaSessionManager? = null
    private var mMediaSession : MediaSession? = null

    private fun initialMediaSession() {
        if (mMediaSessionManager != null) return
        mMediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        mMediaSession = MediaSession(applicationContext, LOG_TAG)
        mMediaSession?.isActive = true
        mMediaSession?.setCallback(object : MediaSession.Callback() {
            override fun onPlay() {
                Log.d("MediaSession", "PLAY")
                super.onPlay()
                playOperation(PlayOperation.PLAY)
            }

            override fun onPause() {
                Log.d("MediaSession", "PAUSE")
                super.onPause()
                playOperation(PlayOperation.PAUSE)
            }

            override fun onSkipToNext() {
                Log.d("MediaSession", "NEXT")
                super.onSkipToNext()
                playOperation(PlayOperation.NEXT)
            }

            override fun onSkipToPrevious() {
                Log.d("MediaSession", "PREVIOUS")
                super.onSkipToPrevious()
                playOperation(PlayOperation.PREVIOUS)
            }

            override fun onStop() {
                Log.d("MediaSession", "STOP")
                super.onStop()
                playOperation(PlayOperation.STOP)
            }

            override fun onSeekTo(pos: Long) {
                Log.d("MediaSession", "SEEK")
                super.onSeekTo(pos)
                setCurrentSeek(pos.toInt())
            }

            override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                val event = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as KeyEvent
                if (event.action != KeyEvent.ACTION_UP) return true

                when (event.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_STOP -> {
                        onStop()
                    }
                    KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (mMediaPlayer.isPlaying) onPause()
                        else onPlay()
                    }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        onPause()
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        onPlay()
                    }
                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        onSkipToNext()
                    }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        onSkipToPrevious()
                    }
                }
                return true
            }
        })
    }

    private lateinit var mMediaPlayer: MediaPlayer
    public lateinit var mSongList: ArrayList<Song>
    private var songPosition: Int = -1
    private var musicBinder : MusicBinder = MusicBinder()
    private var mPlayMode : PlayMode = PlayMode.ONETIME
    private var playStack : Stack<Int> = Stack()
    private var isTopValid = false
    private var isPaused = false
    private var isReady = false

    enum class PlayMode {ONETIME, CYCLE, SHUFFLE}
    enum class PlayOperation {PLAY, PAUSE, STOP, PREVIOUS, NEXT}

    override fun onCreate() {
        super.onCreate()
        mMediaPlayer = MediaPlayer()
        mSongList = ArrayList()

        initMediaPlayer()
        initialMediaSession()
    }

    private fun initMediaPlayer() {
        mMediaPlayer.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
        mMediaPlayer.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())

        mMediaPlayer.setOnPreparedListener(this)
        mMediaPlayer.setOnCompletionListener(this)
        mMediaPlayer.setOnErrorListener(this)
    }

    public fun setPlayMode(playMode: PlayMode) {
        mPlayMode = playMode
    }

    public fun playOperation(playOperation: PlayOperation) {
        when(playOperation) {
            PlayOperation.PLAY -> {
                if (mSongList.size != 0 && !mMediaPlayer.isPlaying) {
                    EventBus.getDefault().post(PlayStatus.PLAYING)
                    if (isPaused) {
                        mMediaPlayer.start()
                    }
                    else {
                        if (songPosition == -1) {
                            EventBus.getDefault().post(PlayPosition(0))
                            songPosition = 0
                        }
                        playAt(songPosition)
                    }

                    isPaused = false
                }

            }
            PlayOperation.PAUSE -> {
                if (mMediaPlayer.isPlaying) {
                    EventBus.getDefault().post(PlayStatus.PAUSE)
                    mMediaPlayer.pause()

                    isPaused = true
                }
            }
            PlayOperation.STOP -> {
                if (mMediaPlayer.isPlaying) {
                    EventBus.getDefault().post(PlayStatus.STOP)
                    mMediaPlayer.reset()
                    initMediaPlayer()
                    isPaused = false
                    isReady = false
                }
            }
            PlayOperation.NEXT -> {
                if (mSongList.size < 1) return
                if (getNextPosition() == -1) {
                    EventBus.getDefault().post(PlayPosition(0))
                    songPosition = 0
                }
                if (!mMediaPlayer.isPlaying)
                    EventBus.getDefault().post(PlayStatus.PLAYING)
                playAt(songPosition)

                isPaused = false
            }
            PlayOperation.PREVIOUS -> {
                if (mSongList.size < 1) return
                if (isTopValid && playStack.size > 1) {
                    isTopValid = false
                    playStack.pop()
                    if (!mMediaPlayer.isPlaying)
                        EventBus.getDefault().post(PlayStatus.PLAYING)
                    EventBus.getDefault().post(PlayPosition(playStack.peek() % mSongList.size))
                    playAt(playStack.pop() % mSongList.size)

                    isPaused = false
                }
            }
        }
    }

    public fun playAt(position: Int) {
        if (mMediaPlayer.isPlaying || isPaused) {
            mMediaPlayer.reset()
            initMediaPlayer()
        }
        songPosition = position
        isTopValid = true
        playStack.push(songPosition)
        mMediaPlayer.setDataSource(applicationContext, Uri.parse(mSongList[songPosition].path))
        mMediaPlayer.prepareAsync()
        isReady = false
    }

    public fun getNextPosition(): Int {
        when(mPlayMode) {
            PlayMode.ONETIME -> {
                if (songPosition != mSongList.size - 1) {
                    songPosition += 1
                }
                else {
                    songPosition = -1
                    return songPosition
                }
            }
            PlayMode.CYCLE -> {
                songPosition = (songPosition + 1) % mSongList.size
            }
            PlayMode.SHUFFLE -> {
                do {
                    songPosition = Random.nextInt() % mSongList.size
                } while(songPosition < 0)
            }
        }
        EventBus.getDefault().post(PlayPosition(songPosition))
        return songPosition
    }

    inner class MusicBinder : Binder() {
        internal val service: PlayerService
            get() = this@PlayerService
    }

    override fun onCompletion(mp: MediaPlayer?) {
        mMediaPlayer.reset()
        initMediaPlayer()

        if (getNextPosition() != -1)
            playAt(songPosition)
        else {
            EventBus.getDefault().post(PlayStatus.STOP)
            isReady = false
        }

    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        EventBus.getDefault().post(PlayStatus.STOP)
        isReady = false
        mMediaPlayer.reset()
        initMediaPlayer()
        return true
    }

    override fun onBind(intent: Intent?): IBinder? {
        return musicBinder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isReady = false
        mMediaPlayer.stop()
        mMediaPlayer.release()
        mMediaSession?.isActive = false
        mMediaSession?.release()
        return false
    }

    fun isPlaying() : Boolean {
        return mMediaPlayer.isPlaying
    }

    fun isPaused() : Boolean {
        return isPaused
    }

    fun getCurrentPosition(): Int? {
        if (isReady)
            return mMediaPlayer.currentPosition
        return null
    }

    fun getDuration(): Int? {
        if (isReady)
            return mMediaPlayer.duration
        return null
    }

    fun setCurrentSeek(currentSeek : Int) {
        if (isReady)
            mMediaPlayer.seekTo(currentSeek)
    }

    fun getCurrentSong() : Int {
        return songPosition
    }

    override fun onPrepared(mediaPlayer: MediaPlayer) {
        isReady = true
        mediaPlayer.start()
    }
}