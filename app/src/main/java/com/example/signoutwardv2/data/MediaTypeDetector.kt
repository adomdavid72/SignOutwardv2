package com.example.signoutwardv2.data

// ============================================================================
// MEDIA TYPE DETECTOR
// ============================================================================
// Detects file types by extension or MIME type
// Supports: Images (jpeg, jpg, png, webp, gif, bmp, tiff)
//           Videos (mp4, mov, avi, mkv, webm, flv, wmv)
// ============================================================================

enum class MediaType {
    IMAGE,
    VIDEO,
    UNSUPPORTED
}

object MediaTypeDetector {
    
    // Supported image extensions
    private val imageExtensions = setOf(
        "jpeg", "jpg", "png", "webp", "gif", "bmp", "tiff", "tif"
    )
    
    // Supported video extensions
    private val videoExtensions = setOf(
        "mp4", "mov", "avi", "mkv", "webm", "flv", "wmv"
    )
    
    // Image MIME types
    private val imageMimeTypes = setOf(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/webp",
        "image/gif",
        "image/bmp",
        "image/tiff",
        "image/x-tiff"
    )
    
    // Video MIME types
    private val videoMimeTypes = setOf(
        "video/mp4",
        "video/quicktime", // mov
        "video/x-msvideo", // avi
        "video/x-matroska", // mkv
        "video/webm",
        "video/x-flv",
        "video/x-ms-wmv" // wmv
    )
    
    /**
     * Detect media type from URL
     * Checks both file extension and MIME type if available
     */
    fun detectMediaType(url: String, mimeType: String? = null): MediaType {
        // First check MIME type if provided
        mimeType?.let { mime ->
            when {
                imageMimeTypes.contains(mime.lowercase()) -> return MediaType.IMAGE
                videoMimeTypes.contains(mime.lowercase()) -> return MediaType.VIDEO
            }
        }
        
        // Fall back to file extension
        val extension = url.substringAfterLast('.', "").lowercase()
        
        return when {
            imageExtensions.contains(extension) -> MediaType.IMAGE
            videoExtensions.contains(extension) -> MediaType.VIDEO
            else -> MediaType.UNSUPPORTED
        }
    }
    
    /**
     * Check if a file type is supported
     */
    fun isSupported(url: String, mimeType: String? = null): Boolean {
        return detectMediaType(url, mimeType) != MediaType.UNSUPPORTED
    }
    
    /**
     * Get file extension from URL
     */
    fun getExtension(url: String): String {
        return url.substringAfterLast('.', "").lowercase()
    }
}

