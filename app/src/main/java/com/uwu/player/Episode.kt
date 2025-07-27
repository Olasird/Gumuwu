// Archivo: Episode.kt
package com.uwu.player

import java.io.Serializable

data class Episode(
    val titulo: String = "",
    // CAMBIO: Ahora es un mapa de String a String
    val sources: Map<String, String> = emptyMap(),
    val numeroEpisodio: Long = 0
) : Serializable