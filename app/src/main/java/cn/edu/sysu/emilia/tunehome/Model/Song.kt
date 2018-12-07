package cn.edu.sysu.emilia.tunehome.Model

import java.io.Serializable

data class Song (
                val title      : String,
                val path       : String,
                val albumId    : Long,
                val artist     : String,
                val album      : String
) : Serializable
