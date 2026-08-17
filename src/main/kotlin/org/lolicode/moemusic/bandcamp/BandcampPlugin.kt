package org.lolicode.moemusic.bandcamp

import org.lolicode.moemusic.api.LocalizedText
import org.lolicode.moemusic.api.plugin.Plugin
import org.lolicode.moemusic.api.plugin.PluginConfigSpec
import org.lolicode.moemusic.api.plugin.ServerRuntimeContext
import org.lolicode.moemusic.api.plugin.pluginConfigSpec

object BandcampPlugin : Plugin {
    const val PLUGIN_ID = "bandcamp-source"
    const val CONFIG_ID = "bandcamp-source"
    const val SOURCE_ID = "bandcamp"

    override val id: String = PLUGIN_ID
    override val configId: String = CONFIG_ID
    override val displayName: LocalizedText = LocalizedText.key("plugin.bandcamp.source")
    override val version: String = "1.1.0"
    override val supportedApiVersions: String = ">=2.2.0 <3.0.0"

    override val configSpec: PluginConfigSpec<BandcampConfig> =
        pluginConfigSpec(::BandcampConfig) {
            boolean(
                key = "enabled",
                getter = { it.enabled },
                updater = { config, value -> config.copy(enabled = value) },
            )
        }

    override fun onServerRuntimeLoad(ctx: ServerRuntimeContext) {
        val source = BandcampSource(ctx.loadConfig(configSpec))
        ctx.registerMusicSource(source)
        ctx.onConfigChanged(configSpec) { source.updateConfig(it) }
        ctx.logger.info("Registered Bandcamp source '{}'.", SOURCE_ID)
    }
}
