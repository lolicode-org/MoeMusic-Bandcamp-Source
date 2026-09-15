package org.lolicode.moemusic.bandcamp.platform

import net.neoforged.fml.common.Mod
import org.lolicode.moemusic.api.MoeMusicApi
import org.lolicode.moemusic.bandcamp.BandcampPlugin

/**
 * NeoForge mod entrypoint.
 *
 * Instantiated by NeoForge FML during mod loading to register [BandcampPlugin]
 * with MoeMusic's public plugin API.
 */
@Mod(BandcampPlugin.MOD_ID)
class NeoForgeEntrypoint {
    init {
        MoeMusicApi.registerPlugin(BandcampPlugin)
    }
}
