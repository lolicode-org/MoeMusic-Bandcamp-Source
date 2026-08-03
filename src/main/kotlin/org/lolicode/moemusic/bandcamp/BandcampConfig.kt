package org.lolicode.moemusic.bandcamp

import kotlinx.serialization.Serializable

@Serializable
data class BandcampConfig(
    val enabled: Boolean = true,
)
