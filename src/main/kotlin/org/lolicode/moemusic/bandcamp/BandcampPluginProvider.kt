package org.lolicode.moemusic.bandcamp

import org.lolicode.moemusic.api.plugin.Plugin
import org.lolicode.moemusic.api.plugin.PluginProvider

class BandcampPluginProvider : PluginProvider {
    override fun plugins(): Iterable<Plugin> = listOf(BandcampPlugin)
}
