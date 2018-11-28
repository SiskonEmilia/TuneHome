package cn.edu.sysu.emilia.tunehome.Views

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioManager
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import cn.edu.sysu.emilia.tunehome.Adapters.SongListAdapter
import cn.edu.sysu.emilia.tunehome.Model.Song
import cn.edu.sysu.emilia.tunehome.R
import cn.edu.sysu.emilia.tunehome.Utils.BlurUtil
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.android.synthetic.main.activity_player.*
import com.orhanobut.dialogplus.DialogPlus
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.support.annotation.ColorInt
import android.support.constraint.ConstraintSet
import android.support.v4.app.ActivityCompat
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.animation.AnticipateOvershootInterpolator
import cn.edu.sysu.emilia.tunehome.Services.PlayerService
import cn.edu.sysu.emilia.tunehome.Utils.OnSwipeTouchListener
import com.jaygoo.widget.OnRangeChangedListener
import com.jaygoo.widget.RangeSeekBar
import kotlinx.android.synthetic.main.playlist_item.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import java.io.InputStream
import java.lang.Exception


public enum class PlayStatus {PLAYING, PAUSE, STOP}
public data class PlayPosition (val position : Int)

class PlayerActivity : AppCompatActivity(){
    companion object {
        const val AUDIO_GALLERY_REQUEST_CODE = 1
        lateinit var mCover : CircleImageView
        lateinit var defaultCover : Bitmap
        lateinit var defaultBlurredCover : Bitmap
        lateinit var mNewBackground : ImageView
        lateinit var mOldBackground : ImageView
    }

    private var songs: ArrayList<Song> = ArrayList()
    private lateinit var mAdapter : SongListAdapter
    private lateinit var mHeader : View
    private var mplayStatus = PlayStatus.PAUSE
    private var musicSrv : PlayerService? = null
    private var playIntent : Intent? = null
    private var musicBound = false
    private var animator : ObjectAnimator? = null
    private var mPlayMode : PlayerService.PlayMode = PlayerService.PlayMode.ONETIME
    private var mHandler = Handler()
    private var process : Float = 0f
    private var normalDisplay = true


    private var serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            var binder = service as PlayerService.MusicBinder
            musicSrv = binder.service
            musicSrv?.mSongList = songs
            musicBound = true

        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicBound = false
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlayStatusChanged(playStatus: PlayStatus) {
        playButton.setImageResource(when(playStatus) {
            PlayStatus.PLAYING -> {
                startRotate()
                mplayStatus = PlayStatus.PLAYING
                R.drawable.pause
            }
            PlayStatus.PAUSE -> {
                pauseRotate()
                mplayStatus = PlayStatus.PAUSE
                R.drawable.play
            }
            PlayStatus.STOP -> {
                stopRotate()
                mplayStatus = PlayStatus.STOP
                R.drawable.play
            }
        })
        mplayStatus = playStatus
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlayingMusicChanged(playPosition : PlayPosition) {
        switchToSongPosition(playPosition.position)
    }

    fun switchToSongPosition(position: Int) {
        // TODO: Duration

        titleSongName.text = songs[position].title
        titleArtistName.text = songs[position].artist

        songName.text = songs[position].title
        artistName.text = songs[position].artist
        albumName.text = songs[position].album

        var songCover = Uri.parse("content://media/external/audio/albumart")
        var uriSongCover = ContentUris.withAppendedId(songCover, songs[position].albumId)
        try {
            RetrieveCover().execute(contentResolver.openInputStream(uriSongCover))
        }
        catch (e : Exception) {
            newBackground.alpha = 0f
            newBackground.setImageBitmap(defaultBlurredCover)
            newBackground.animate()
                .setDuration(1000)
                .alpha(1.0f)
                .withEndAction {
                    mOldBackground.setImageBitmap(defaultBlurredCover)
                }
                .start()
            albumView.setImageBitmap(defaultCover)
        }
    }

    override fun onAttachedToWindow() {
        setStatusBarImmersiveMode(Color.TRANSPARENT)
        super.onAttachedToWindow()
    }

    protected fun setStatusBarImmersiveMode(color : Int) {

        // StatusBar
        if (Build.VERSION.SDK_INT >= 19) { // 19, 4.4, KITKAT
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }
        if (Build.VERSION.SDK_INT >= 21) { // 21, 5.0, LOLLIPOP
            window.attributes.systemUiVisibility = window.attributes.systemUiVisibility.or(
                    (View.SYSTEM_UI_FLAG_LAYOUT_STABLE.or(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)))
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.statusBarColor = color
        }

    }

    @SuppressLint("ObsoleteSdkInt")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val w = window // in Activity's onCreate() for instance
            w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            w.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        }

        setStatusBarImmersiveMode(Color.TRANSPARENT)

        mCover = albumView
        mNewBackground = newBackground
        mOldBackground = oldBackground

        playButton.alpha = 0.6f
        previousButton.alpha = 0.6f
        nextButton.alpha = 0.6f
        playListButton.alpha = 0.6f
        listModeButton.alpha = 0.6f

        mAdapter = SongListAdapter(this, songs)
        mHeader = LayoutInflater.from(this).inflate(R.layout.playlist_header, null)
        playButton.setOnLongClickListener {
            if (mplayStatus == PlayStatus.PLAYING) {
                musicSrv?.playOperation(PlayerService.PlayOperation.STOP)
            }
            true
        }

        defaultCover = BitmapFactory.decodeResource(resources, R.drawable.default_background)
        defaultBlurredCover = BlurUtil.doBlur(defaultCover, 50, false)

        newBackground.setImageBitmap(defaultBlurredCover)
        oldBackground.setImageBitmap(defaultBlurredCover)
        albumView.setImageBitmap(defaultCover)

        processSlider.setRange(0f, 100f)
        processSlider.setValue(0f)
        processSlider.setOnRangeChangedListener(object : OnRangeChangedListener {
            override fun onRangeChanged(view: RangeSeekBar?, leftValue: Float, rightValue: Float, isFromUser: Boolean) {
                process = leftValue
            }

            override fun onStartTrackingTouch(view: RangeSeekBar?, isLeft: Boolean) {

            }

            override fun onStopTrackingTouch(view: RangeSeekBar?, isLeft: Boolean) {
                var duration = musicSrv?.getDuration()
                if (duration != null)
                    musicSrv?.setCurrentSeek((process * duration / 100).toInt())
            }
        })

        runOnUiThread(object : Runnable {
            override fun run() {
                if (mplayStatus != PlayStatus.STOP) {
                    var current = musicSrv?.getCurrentPosition()?.toFloat()
                    var total = musicSrv?.getDuration()?.toFloat()
                    if (current != null && total != null) {
                        currentTime.text = secondsToString(current.toInt() / 1000)
                        totalTime.text = secondsToString(total.toInt() / 1000)
                        processSlider.setValue(current / total * 100);
                    } else {
                        currentTime.text = "00:00"
                        totalTime.text = "00:00"
                        processSlider.setValue(0f)
                    }
                }
                else {
                    currentTime.text = "00:00"
                    totalTime.text = "00:00"
                    processSlider.setValue(0f)
                }

                mHandler.postDelayed(this, 500)
            }
        })

        rootLayout.setOnTouchListener(object : OnSwipeTouchListener(this) {
            override fun onSwipeLeft() {
                musicSrv?.playOperation(PlayerService.PlayOperation.NEXT)
            }

            override fun onSwipeRight() {
                musicSrv?.playOperation(PlayerService.PlayOperation.PREVIOUS)
            }
        })

        val constraintNormal = ConstraintSet()
        val constraintAbnormal = ConstraintSet()

        constraintNormal.clone(this, R.layout.activity_player)
        constraintAbnormal.clone(this, R.layout.activity_player_abnormal)

        albumView.setOnClickListener {
            if (normalDisplay) {
                val transition = ChangeBounds()
                // transition.interpolator = AnticipateOvershootInterpolator(1.0f)
                transition.duration = 800
                TransitionManager.beginDelayedTransition(rootLayout, transition)
                constraintAbnormal.applyTo(rootLayout)
            }
            else {
                val transition = ChangeBounds()
                // transition.interpolator = AnticipateOvershootInterpolator(1.0f)
                transition.duration = 800
                TransitionManager.beginDelayedTransition(rootLayout, transition)
                constraintNormal.applyTo(rootLayout)
            }

            normalDisplay = !normalDisplay
        }

    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)

        if (playIntent == null) {
            playIntent = Intent(this, PlayerService::class.java)
            bindService(playIntent, serviceConnection, Context.BIND_AUTO_CREATE)
            startService(playIntent)
        }

        var newPosition = musicSrv?.getCurrentSong()
        if (newPosition != null && newPosition != -1) {
            switchToSongPosition(newPosition)
        }
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    private fun startRotate() {
        if (animator == null) {
            animator = ObjectAnimator.ofFloat(albumView, "rotation", 0f, 360.0f);
            animator?.duration = 20000
            animator?.interpolator = LinearInterpolator()
            animator?.repeatCount = -1
            animator?.repeatMode = ValueAnimator.RESTART
        }
        if (animator?.isPaused == true)  animator?.resume()
        else animator?.start()
    }

    private fun stopRotate() {
        animator?.end()
        albumView.rotation = 0f

    }

    private fun pauseRotate() {
        animator?.pause()

    }

    public fun onButtonsClick(view: View?) {
        when(view?.id) {
            R.id.playButton -> {
                when(mplayStatus) {
                    PlayStatus.PLAYING -> {
                        musicSrv?.playOperation(PlayerService.PlayOperation.PAUSE)
                    }
                    else -> {
                        musicSrv?.playOperation(PlayerService.PlayOperation.PLAY)
                    }
                }
            }
            R.id.previousButton -> {
                musicSrv?.playOperation(PlayerService.PlayOperation.PREVIOUS)
            }
            R.id.nextButton -> {
                musicSrv?.playOperation(PlayerService.PlayOperation.NEXT)
            }
            R.id.playListButton -> {
                val dialog = DialogPlus.newDialog(this)
                    .setAdapter(mAdapter)
                    .setOnItemClickListener { dialog, item, view, position ->
                        musicSrv?.playAt(position)
                        switchToSongPosition(position)
                        onPlayStatusChanged(PlayStatus.PLAYING)
                        dialog.dismiss()
                    }
                    .setOnClickListener { dialog, view ->
                        if (view.id == R.id.addButton) {
                            val galleryIntent =
                                Intent(Intent.ACTION_PICK, android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
                            startActivityForResult(galleryIntent, AUDIO_GALLERY_REQUEST_CODE)
                        }
                    }
                    .setExpanded(false)
                    .setContentBackgroundResource(R.drawable.rounded_corner)
                    .setHeader(mHeader)
                    .setPadding(16, 8,16,0)
                    .setContentHeight(800)
                    .create()

                dialog.show()
                requestPermissionForPermissions()
            }
            R.id.listModeButton -> {
                listModeButton.setImageResource(when(mPlayMode) {
                    PlayerService.PlayMode.ONETIME -> {
                        musicSrv?.setPlayMode(PlayerService.PlayMode.CYCLE)
                        mPlayMode = PlayerService.PlayMode.CYCLE
                        R.drawable.repeat
                    }
                    PlayerService.PlayMode.CYCLE -> {
                        musicSrv?.setPlayMode(PlayerService.PlayMode.SHUFFLE)
                        mPlayMode = PlayerService.PlayMode.SHUFFLE
                        R.drawable.shuffle
                    }
                    PlayerService.PlayMode.SHUFFLE-> {
                        musicSrv?.setPlayMode(PlayerService.PlayMode.ONETIME)
                        mPlayMode = PlayerService.PlayMode.ONETIME
                        R.drawable.onetime
                    }
                })
            }
            R.id.exitButton -> {
                stopService(playIntent)
                musicSrv = null
                System.exit(0)
            }
        }
    }

    override fun onDestroy() {
        stopService(playIntent)
        musicSrv = null
        System.exit(0)
        super.onDestroy()
    }

    private fun addMusicToList(uri: String?) {
        var toast = Toast.makeText(this, "Failed to add.", Toast.LENGTH_SHORT)
        if (uri != null) {
            var cursor = contentResolver.query(
                Uri.parse(uri),
                arrayOf(
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.TITLE),
                null,
                null,
                "")
            with(cursor) {
                if (this != null) {
                    while(moveToNext()) {
                        songs.add(Song(
                            getString(getColumnIndex(MediaStore.Audio.Media.TITLE)),
                            uri,
                            getLong(getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)),
                            getString(getColumnIndex(MediaStore.Audio.Media.ARTIST)),
                            getString(getColumnIndex(MediaStore.Audio.Media.ALBUM))
                        ))
                        toast.setText("Added successfully.")
                        toast.show()
                        mAdapter.notifyDataSetChanged()
                        if (songs.size == 0) {
                            onPlayingMusicChanged(PlayPosition(0))
                        }
                        return
                    }
                }
                else toast.show()
            }
        }
        else toast.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == AUDIO_GALLERY_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                try {
                    addMusicToList(data?.data.toString())
                } catch (e: Exception) {
                   e.printStackTrace()
                }
            }
        }
    }

    @Throws(Exception::class)
    fun requestPermissionForPermissions() {
        try {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                0x3
            )
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }


    private class RetrieveCover : AsyncTask<InputStream, Unit, Unit>() {
        var bitmap : Bitmap? = null

        override fun doInBackground(vararg params: InputStream?) {
            try {
                bitmap = BitmapFactory.decodeStream(params[0])
            }
            catch (e : Exception) {
                e.printStackTrace()
                bitmap = null
            }
        }

        override fun onPostExecute(result: Unit?) {
            if (bitmap == null) {
                mNewBackground.setImageBitmap(defaultBlurredCover)
                mCover.setImageBitmap(defaultCover)
            }
            else {
                ChangeCover().execute(bitmap)
            }
        }
    }

    private class ChangeCover : AsyncTask<Bitmap, Unit, Unit>() {
        var originBitmap : Bitmap? = null
        var blurredBitmap : Bitmap? = null

        override fun doInBackground(vararg params: Bitmap?){
            originBitmap = params[0]
            if (originBitmap != null)
                blurredBitmap = BlurUtil.doBlur(originBitmap, 50, false)

        }

        override fun onPostExecute(result: Unit?) {
            if (originBitmap != null) {
                mNewBackground.alpha = 0f
                mNewBackground.setImageBitmap(blurredBitmap)
                mNewBackground.animate()
                    .setDuration(1000)
                    .alpha(1.0f)
                    .withEndAction {
                        mOldBackground.setImageBitmap(blurredBitmap)
                    }
                    .start()

                mCover.setImageBitmap(originBitmap)
            }
        }

    }

    private fun secondsToString(pTime: Int): String {
        return String.format("%02d:%02d", pTime / 60, pTime % 60)
    }

    override fun onBackPressed() {
        var intent =  Intent(Intent.ACTION_MAIN)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        intent.addCategory(Intent.CATEGORY_HOME)
        startActivity(intent)
    }
}
