package org.lolicode.moemusic.bandcamp.platform

import net.fabricmc.api.ModInitializer
import org.lolicode.moemusic.api.MoeMusicApi
import org.lolicode.moemusic.bandcamp.BandcampPlugin

/**
 * Fabric / Quilt mod entrypoint.
 *
 * Called by FabricLoader during game initialization to register [BandcampPlugin]
 * with MoeMusic's public plugin API.
 */
class FabricEntrypoint : ModInitializer {
    override fun onInitialize() {
        MoeMusicApi.registerPlugin(BandcampPlugin)
    }
}
