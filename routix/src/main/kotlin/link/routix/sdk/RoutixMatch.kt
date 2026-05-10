package link.routix.sdk

import java.util.*

data class RoutixMatch(
    val success: Boolean,
    val shortCode: String? = null,
    val originalUrl: String? = null,
    val matchSource: String? = null,
    val confidence: Double = 1.0,
    val metadata: Map<String, Any>? = null,
    val timestamp: String? = null
)
