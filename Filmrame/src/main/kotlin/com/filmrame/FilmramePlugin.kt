package com.filmrame

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class FilmramePlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(FilmrameProvider())
    }
}
