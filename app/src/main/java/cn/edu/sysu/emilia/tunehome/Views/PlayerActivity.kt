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
import android.media.MediaMetadata
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.support.constraint.ConstraintSet
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.Toast
import cn.edu.sysu.emilia.tunehome.Adapters.SongListAdapter
import cn.edu.sysu.emilia.tunehome.Model.Song
import cn.edu.sysu.emilia.tunehome.R
import cn.edu.sysu.emilia.tunehome.Services.PlayerService
import cn.edu.sysu.emilia.tunehome.Utils.BlurUtil
import cn.edu.sysu.emilia.tunehome.Utils.OnSwipeTouchListener
import com.jaygoo.widget.OnRangeChangedListener
import com.jaygoo.widget.RangeSeekBar
import com.orhanobut.dialogplus.DialogPlus
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.android.synthetic.main.activity_player.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream


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
        var musicSrv : PlayerService? = null
    }

    private var songs: ArrayList<Song> = ArrayList()
    private lateinit var mAdapter : SongListAdapter
    private var mplayStatus = PlayStatus.PAUSE
    private var playIntent : Intent? = null
    private var musicBound = false
    private var animator : ObjectAnimator? = null
    private var mPlayMode : PlayerService.PlayMode = PlayerService.PlayMode.ONETIME
    private var mHandler = Handler()
    private var process : Float = 0f
    private var normalDisplay = true
    private var mDialog : DialogPlus? = null


    private var serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            var binder = service as PlayerService.MusicBinder
            musicSrv = binder.service
            musicSrv?.mSongList = songs
            musicBound = true

            var newPosition = musicSrv?.getCurrentSong()
            if (newPosition != null && newPosition != -1) {
                switchToSongPosition(newPosition)
            }

            if (musicSrv?.isPlaying() == true) {
                onPlayStatusChanged(PlayStatus.PLAYING)
            } else if (musicSrv?.isPaused() == true) {
                onPlayStatusChanged(PlayStatus.PAUSE)
            } else {
                onPlayStatusChanged(PlayStatus.STOP)
            }
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

    data class ClickedItemPostion(val position: Int)

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlaylistItemClicked(position: ClickedItemPostion) {
        musicSrv?.playAt(position.position)
        switchToSongPosition(position.position)
        onPlayStatusChanged(PlayStatus.PLAYING)
        mDialog?.dismiss()
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
        window.attributes.systemUiVisibility = window.attributes.systemUiVisibility.or(
                (View.SYSTEM_UI_FLAG_LAYOUT_STABLE.or(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    .or(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION.or(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        .or(View.SYSTEM_UI_FLAG_IMMERSIVE))))))
          window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.statusBarColor = color

    }

    @SuppressLint("ObsoleteSdkInt")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        try {
            openFileInput("PlayList").use {fileInputStream ->
                var ois = ObjectInputStream(fileInputStream)
                songs = ois.readObject() as ArrayList<Song>
                ois.close()
                fileInputStream.close()
            }
        } catch (e : java.lang.Exception) {
            e.printStackTrace()
        }

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

        exitButton.setOnLongClickListener {
            onBackPressed()
            true
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
        else {
            var newPosition = musicSrv?.getCurrentSong()
            if (newPosition != null && newPosition != -1) {
                switchToSongPosition(newPosition)
            }

            if (musicSrv?.isPlaying() == true) {
                onPlayStatusChanged(PlayStatus.PLAYING)
            } else if (musicSrv?.isPaused() == true) {
                onPlayStatusChanged(PlayStatus.PAUSE)
            } else {
                onPlayStatusChanged(PlayStatus.STOP)
            }
        }
    }

    override fun onStop() {
        try {
            openFileOutput("PlayList", Context.MODE_PRIVATE).use { fileOutputStream ->
                var oos = ObjectOutputStream(fileOutputStream)
                oos.writeObject(songs)
                oos.close()
                fileOutputStream.close()
            }
        } catch (e : java.lang.Exception) {
            e.printStackTrace()
        }

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
        else if (animator?.isRunning == false) animator?.start()
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
                mDialog = DialogPlus.newDialog(this)
                    .setAdapter(mAdapter)
//                    .setOnItemClickListener { dialog, item, view, position ->
//                        musicSrv?.playAt(position)
//                        switchToSongPosition(position)
//                        onPlayStatusChanged(PlayStatus.PLAYING)
//                        dialog.dismiss()
//                    }
                    .setOnClickListener { _, view ->
                        when(view.id) {
                            R.id.addButton -> {
                                val galleryIntent =
                                    Intent(Intent.ACTION_PICK, android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
                                startActivityForResult(galleryIntent, AUDIO_GALLERY_REQUEST_CODE)
                            }
                            R.id.deleteAllButton -> {
                                songs.clear()
                                mAdapter.notifyDataSetChanged()
                            }
                            R.id.addFolderButton -> {
                                importLibary()
                                Toast.makeText(this, "Media library imported successfully", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    }
                    .setExpanded(false)
                    .setContentBackgroundResource(R.drawable.rounded_corner)
                    .setHeader(R.layout.playlist_header)
                    .setFooter(R.layout.playlist_footer)
                    .setPadding(16, 8,16,0)
                    .setContentHeight(800)
                    .create()

                mDialog?.show()
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
        try {
            openFileOutput("PlayList", Context.MODE_PRIVATE).use { fileOutputStream ->
                var oos = ObjectOutputStream(fileOutputStream)
                oos.writeObject(songs)
                oos.close()
                fileOutputStream.close()
            }
        } catch (e : java.lang.Exception) {
            e.printStackTrace()
        }

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
                    }
                    mAdapter.notifyDataSetChanged()
                }
                else toast.show()
            }
        }
        else toast.show()
    }

    private fun importLibary() {
        songs.clear()
        var cursor = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media._ID
            ),
            null,
            null,
            "")
        with(cursor) {
            if (this != null) {
                while(moveToNext()) {
                    songs.add(Song(
                        getString(getColumnIndex(MediaStore.Audio.Media.TITLE)),
                        Uri.withAppendedPath(Uri.parse("content://media/external/audio/media"),
                            getLong(getColumnIndex(MediaStore.Audio.Media._ID)).toString()).toString(),
                        getLong(getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)),
                        getString(getColumnIndex(MediaStore.Audio.Media.ARTIST)),
                        getString(getColumnIndex(MediaStore.Audio.Media.ALBUM))
                    ))
                }
                mAdapter.notifyDataSetChanged()
            }
        }

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
