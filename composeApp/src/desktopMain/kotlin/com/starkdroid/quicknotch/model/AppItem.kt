package com.starkdroid.quicknotch.model

import kotlinx.serialization.Serializable

/**
 * Represents an application item that can be pinned to the Dynamic Island / Dock.
 */
@Serializable
data class AppItem(
    val id: String,
    val name: String,
    val executablePath: String,
    val arguments: String = "",
    val workingDirectory: String? = null,
    val iconPath: String? = null,
    val isCustom: Boolean = false,
    val isUwp: Boolean = false,
    val uwpAppUserModelId: String? = null
)
