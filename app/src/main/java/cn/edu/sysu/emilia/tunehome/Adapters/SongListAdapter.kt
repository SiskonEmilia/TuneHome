package cn.edu.sysu.emilia.tunehome.Adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.TextView
import cn.edu.sysu.emilia.tunehome.Model.Song
import cn.edu.sysu.emilia.tunehome.R
import cn.edu.sysu.emilia.tunehome.Views.PlayerActivity
import org.greenrobot.eventbus.EventBus

class SongListAdapter(
    var context : Context,
    var songs : ArrayList<Song>
) : BaseAdapter() {

    override fun getCount(): Int {
        return songs.size
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getItem(position: Int): Any {
        return songs[position]
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        var resultView : View
        if (convertView == null)
            resultView = LayoutInflater.from(context).inflate(R.layout.playlist_item, parent, false)
        else
            resultView = convertView

        val song = getItem(position) as Song

        resultView.setOnClickListener {
            EventBus.getDefault().post(PlayerActivity.ClickedItemPostion(position))
        }
        resultView.findViewById<TextView>(R.id.songTitle).text = song.title
        resultView.findViewById<TextView>(R.id.songArtist).text = song.artist
        resultView.findViewById<ImageButton>(R.id.deleteButton).setOnClickListener {
            songs.removeAt(position)
            notifyDataSetChanged()
        }

        return resultView
    }

}