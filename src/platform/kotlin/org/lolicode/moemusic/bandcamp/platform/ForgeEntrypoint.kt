package org.lolicode.moemusic.bandcamp.platform

import net.minecraftforge.fml.common.Mod
import org.lolicode.moemusic.api.MoeMusicApi
import org.lolicode.moemusic.bandcamp.BandcampPlugin

/**
 * Minecraft Forge mod entrypoint.
 *
 * Instantiated by Forge FML during mod loading to register [BandcampPlugin]
 * with MoeMusic's public plugin API.
 */
@Mod(BandcampPlugin.MOD_ID)
class ForgeEntrypoint {
    init {
        MoeMusicApi.registerPlugin(BandcampPlugin)
    }
}
