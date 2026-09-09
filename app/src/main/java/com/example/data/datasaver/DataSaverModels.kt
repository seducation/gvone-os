package com.example.data.datasaver

enum class ImageCompressionMode(val label: String, val reductionFactor: Float, val description: String) {
    AGGRESSIVE("Aggressive (High)", 0.70f, "Downscales resolution & recompresses web images (70% savings)"),
    MODERATE("Moderate (Balanced)", 0.40f, "Strips metadata, serves modern WebP/AVIF (40% savings)"),
    OFF("Original Quality", 0.0f, "Loads all images at full uncompressed quality")
}

data class DataSaverConfig(
    val isEnabled: Boolean = true,
    val imageCompressionMode: ImageCompressionMode = ImageCompressionMode.AGGRESSIVE,
    val blockVideoAutoplay: Boolean = true,
    val textOnlyReadingMode: Boolean = false,
    val blockHeavyTelemetryScripts: Boolean = true,
    val sendSaveDataHeader: Boolean = true
)

data class DataSaverStats(
    val totalBytesSaved: Long = 184_200_000L,       // ~184.2 MB
    val totalBytesTransferred: Long = 158_000_000L,  // ~158.0 MB
    val savingsPercentage: Int = 54,
    val imagesSavedBytes: Long = 98_400_000L,        // ~98.4 MB
    val videoSavedBytes: Long = 62_100_000L,         // ~62.1 MB
    val scriptsSavedBytes: Long = 23_700_000L,       // ~23.7 MB
    val requestsFiltered: Int = 1420
) {
    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000L -> String.format(java.util.Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000L -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_000_000.0)
            bytes >= 1_000L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1_000.0)
            else -> "$bytes B"
        }
    }
}
