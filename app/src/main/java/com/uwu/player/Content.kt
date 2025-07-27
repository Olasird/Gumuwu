package com.uwu.player

import java.io.Serializable
import java.util.Date

data class Content(
    val id: String = "",
    val titulo: String = "",
    val titulo_serie: String = "", // <-- NUEVO
    val descripcion: String = "",
    val portadaUrl: String = "",
    val tipo: String = "",
    val saga_id: String = "",
    val categorias: List<String> = emptyList(),
    val sources: Map<String, String> = emptyMap(),
    val temporada: Long = 0,
    val año: Long = 0,
    val popularOrder: Long = 0,
    val rating_imdb: String = "",
    val rating_rotten: String = "",
    val fechaPublicacion: Date? = null
) : Serializable