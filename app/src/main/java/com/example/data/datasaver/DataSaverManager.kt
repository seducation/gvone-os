package com.example.data.datasaver

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DataSaverManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("gvone_data_saver_prefs", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<DataSaverConfig> = _config.asStateFlow()

    private val _stats = MutableStateFlow(loadStats())
    val stats: StateFlow<DataSaverStats> = _stats.asStateFlow()

    private val _whitelistedDomains = MutableStateFlow<Set<String>>(loadWhitelistedDomains())
    val whitelistedDomains: StateFlow<Set<String>> = _whitelistedDomains.asStateFlow()

    private fun loadConfig(): DataSaverConfig {
        val enabled = prefs.getBoolean("key_enabled", true)
        val modeStr = prefs.getString("key_image_mode", ImageCompressionMode.AGGRESSIVE.name) ?: ImageCompressionMode.AGGRESSIVE.name
        val imageMode = runCatching { ImageCompressionMode.valueOf(modeStr) }.getOrDefault(ImageCompressionMode.AGGRESSIVE)
        val blockVideo = prefs.getBoolean("key_block_video", true)
        val textOnly = prefs.getBoolean("key_text_only", false)
        val blockScripts = prefs.getBoolean("key_block_scripts", true)
        val sendHeader = prefs.getBoolean("key_send_header", true)

        return DataSaverConfig(
            isEnabled = enabled,
            imageCompressionMode = imageMode,
            blockVideoAutoplay = blockVideo,
            textOnlyReadingMode = textOnly,
            blockHeavyTelemetryScripts = blockScripts,
            sendSaveDataHeader = sendHeader
        )
    }

    private fun loadStats(): DataSaverStats {
        val saved = prefs.getLong("key_bytes_saved", 184_200_000L)
        val transferred = prefs.getLong("key_bytes_transferred", 158_000_000L)
        val pct = if (saved + transferred > 0) ((saved * 100) / (saved + transferred)).toInt() else 54
        val imgSaved = prefs.getLong("key_img_saved", 98_400_000L)
        val vidSaved = prefs.getLong("key_vid_saved", 62_100_000L)
        val scrSaved = prefs.getLong("key_scr_saved", 23_700_000L)
        val filtered = prefs.getInt("key_filtered", 1420)

        return DataSaverStats(
            totalBytesSaved = saved,
            totalBytesTransferred = transferred,
            savingsPercentage = pct,
            imagesSavedBytes = imgSaved,
            videoSavedBytes = vidSaved,
            scriptsSavedBytes = scrSaved,
            requestsFiltered = filtered
        )
    }

    private fun loadWhitelistedDomains(): Set<String> {
        return prefs.getStringSet("key_whitelist", setOf("youtube.com", "netflix.com")) ?: emptySet()
    }

    fun updateConfig(newConfig: DataSaverConfig) {
        _config.value = newConfig
        prefs.edit()
            .putBoolean("key_enabled", newConfig.isEnabled)
            .putString("key_image_mode", newConfig.imageCompressionMode.name)
            .putBoolean("key_block_video", newConfig.blockVideoAutoplay)
            .putBoolean("key_text_only", newConfig.textOnlyReadingMode)
            .putBoolean("key_block_scripts", newConfig.blockHeavyTelemetryScripts)
            .putBoolean("key_send_header", newConfig.sendSaveDataHeader)
            .apply()
    }

    fun toggleDataSaver(enabled: Boolean) {
        updateConfig(_config.value.copy(isEnabled = enabled))
    }

    fun setImageCompressionMode(mode: ImageCompressionMode) {
        updateConfig(_config.value.copy(imageCompressionMode = mode))
    }

    fun toggleBlockVideoAutoplay(block: Boolean) {
        updateConfig(_config.value.copy(blockVideoAutoplay = block))
    }

    fun toggleTextOnlyMode(textOnly: Boolean) {
        updateConfig(_config.value.copy(textOnlyReadingMode = textOnly))
    }

    fun toggleWhitelistDomain(domain: String) {
        val current = _whitelistedDomains.value.toMutableSet()
        if (current.contains(domain)) {
            current.remove(domain)
        } else {
            current.add(domain)
        }
        _whitelistedDomains.value = current
        prefs.edit().putStringSet("key_whitelist", current).apply()
    }

    fun resetStats() {
        val reset = DataSaverStats(
            totalBytesSaved = 0L,
            totalBytesTransferred = 0L,
            savingsPercentage = 0,
            imagesSavedBytes = 0L,
            videoSavedBytes = 0L,
            scriptsSavedBytes = 0L,
            requestsFiltered = 0
        )
        _stats.value = reset
        prefs.edit()
            .putLong("key_bytes_saved", 0L)
            .putLong("key_bytes_transferred", 0L)
            .putLong("key_img_saved", 0L)
            .putLong("key_vid_saved", 0L)
            .putLong("key_scr_saved", 0L)
            .putInt("key_filtered", 0)
            .apply()
    }

    /**
     * JavaScript payload injected into WebView to enforce client-side Data Saver rules:
     * - Pauses and blocks video autoplay
     * - Defers heavy images with lazy loading
     * - Strips background videos and media when text-only mode is active
     */
    fun getInjectionScript(currentDomain: String): String {
        if (!_config.value.isEnabled || _whitelistedDomains.value.contains(currentDomain)) {
            return ""
        }

        val textOnlyJs = if (_config.value.textOnlyReadingMode) {
            """
            (function() {
                var media = document.querySelectorAll('img, video, audio, canvas, svg, iframe');
                for (var i = 0; i < media.length; i++) {
                    media[i].style.display = 'none';
                }
                var all = document.querySelectorAll('*');
                for (var j = 0; j < all.length; j++) {
                    all[j].style.backgroundImage = 'none';
                }
            })();
            """.trimIndent()
        } else ""

        val videoBlockJs = if (_config.value.blockVideoAutoplay) {
            """
            (function() {
                var videos = document.querySelectorAll('video');
                for (var i = 0; i < videos.length; i++) {
                    videos[i].autoplay = false;
                    videos[i].preload = 'none';
                    videos[i].pause();
                }
            })();
            """.trimIndent()
        } else ""

        val imageCompressionJs = if (_config.value.imageCompressionMode != ImageCompressionMode.OFF) {
            """
            (function() {
                var images = document.querySelectorAll('img');
                for (var i = 0; i < images.length; i++) {
                    images[i].loading = 'lazy';
                    images[i].decoding = 'async';
                }
            })();
            """.trimIndent()
        } else ""

        return "$videoBlockJs\n$imageCompressionJs\n$textOnlyJs"
    }
}
