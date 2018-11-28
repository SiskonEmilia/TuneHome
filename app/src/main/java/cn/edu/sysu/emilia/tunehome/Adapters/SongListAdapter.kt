package cn.edu.sysu.emilia.tunehome.Adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import cn.edu.sysu.emilia.tunehome.Model.Song
import cn.edu.sysu.emilia.tunehome.R

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

        resultView.findViewById<TextView>(R.id.songTitle).text = song.title
        resultView.findViewById<TextView>(R.id.songArtist).text = song.artist

        return resultView
    }

}