package com.shio.saikyo.ui

import kotlinx.serialization.Serializable


object Routes {
    @Serializable
    object Home

    @Serializable
    object Dictionary

    @Serializable
    data class WordDefinition(val lemmaId: Int)

    @Serializable
    object Settings {
        @Serializable
        object AllSettings

        @Serializable
        object LocalMediaSelection
    }

    @Serializable
    // definitely change this name
    object LanguageReactor

    @Serializable
    data class VideoPlayer(val videoUri: String, val subtitleUri: String)

    @Serializable
    data class AudioPlayer(val audioUri: String, val subtitleUri: String)

    @Serializable
    data object LR2
}