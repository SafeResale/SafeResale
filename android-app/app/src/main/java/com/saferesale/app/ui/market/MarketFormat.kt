package com.saferesale.app.ui.market

import androidx.compose.ui.graphics.Color
import com.saferesale.app.ui.theme.ActivateGreen
import com.saferesale.app.ui.theme.DeactivateRed
import com.saferesale.app.ui.theme.PendingBlue
import com.saferesale.app.ui.theme.SoldOutAmber
import com.saferesale.app.ui.theme.StatusDanger
import com.saferesale.app.ui.theme.StatusGood
import com.saferesale.app.ui.theme.StatusNeutral
import com.saferesale.app.ui.theme.StatusWarning
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt
import com.saferesale.app.domain.model.MarketListing

/**
 * Score shown on the trust badge. The Risk Score pipeline is independent and
 * untouched; the badge is a separate layer that generates a quality score from
 * the product data (higher = more trustworthy). Prefer the backend's
 * `trust.score` (source of truth); fall back to inverting the risk score
 * locally only for older backends that omit `trust`.
 */
fun trustScore(listing: MarketListing): Int? {
    listing.trust?.score?.let { return it }
    listing.risk?.adjusted_score?.let { return (100f - it.toFloat()).roundToInt().coerceIn(0, 100) }
    return listing.diagnostic_score
}

/**
 * Verdict under the badge. Prefer the backend's `trust.band/label` verbatim;
 * fall back to the same ranges only if the backend omitted it:
 * 1-30 Poor, 31-70 Moderate, 71-100 Good (higher = better).
 */
fun trustVerdict(listing: MarketListing): Pair<Int?, String> {
    val score = trustScore(listing) ?: return null to "No score"
    val band = listing.trust?.band
    val label = listing.trust?.label
    if (band != null) return score to (label ?: band.replaceFirstChar { it.uppercase() })
    return score to when {
        score >= 71 -> "Good"
        score >= 31 -> "Moderate"
        else -> "Poor"
    }
}

/**
 * Trust badge tint: backend `band` wins (source of truth); otherwise fall back
 * to the same classification: 71-100 Good (green), 31-70 Moderate (amber),
 * 1-30 Poor (red). Higher number = better trust.
 */
fun trustTint(listing: MarketListing): Color = when (listing.trust?.band) {
    "good" -> StatusGood
    "moderate" -> StatusWarning
    "poor" -> StatusDanger
    else -> trustTint(trustScore(listing))
}

fun trustTint(score: Int?): Color = when {
    score == null -> StatusNeutral
    score >= 71 -> StatusGood
    score >= 31 -> StatusWarning
    else -> StatusDanger
}

fun trustColorOf(listing: MarketListing): Color = trustTint(listing)

fun formatPrice(value: Double): String {
    val rounded = floor(value)
    val v = if (rounded == value) value.toLong() else value
    return "₹" + DecimalFormat("#,##,##0.##").format(v)
}

fun formatDate(ts: Double?): String {
    if (ts == null) return ""
    return SimpleDateFormat("d MMM yyyy", Locale.ROOT).format(Date((ts * 1000).toLong()))
}

fun listingStatusLabel(status: String): Pair<String, Color> = when (status) {
    "published", "approved" -> "Active" to ActivateGreen
    "verifying", "inspection_pending", "submitted" -> "In review" to PendingBlue
    "sold_out", "soldOut" -> "Sold out" to SoldOutAmber
    "blocked", "restricted", "deactivated" -> "Inactive" to DeactivateRed
    else -> "Draft" to StatusNeutral
}

fun formatCategory(category: String): String = when (category) {
    "mobile" -> "Mobiles"
    "laptop" -> "Laptops"
    "electronics" -> "Electronics"
    "camera" -> "Cameras"
    "gaming" -> "Gaming"
    "appliance" -> "Appliances"
    "car" -> "Cars"
    "bike" -> "Bikes"
    "furniture" -> "Furniture"
    "accessory" -> "Accessories"
    "vehicle" -> "Vehicles" // legacy
    "tablet" -> "Tablets" // legacy
    else -> category.replaceFirstChar { it.uppercase(Locale.ROOT) }
}

fun formatCondition(condition: String?): String? =
    condition?.replaceFirstChar { it.uppercase(Locale.ROOT) }

fun riskBandOf(listing: com.saferesale.app.domain.model.MarketListing): String? =
    listing.risk_band ?: listing.risk?.band

fun scoreToBand(score: Double?): String? = when {
    score == null -> null
    score <= 30 -> "low"
    score <= 60 -> "medium"
    else -> "high"
}

fun riskLabel(band: String?): String = when (band) {
    "low" -> "Low risk"
    "medium" -> "Medium risk"
    "high" -> "High risk"
    "critical" -> "Critical risk"
    else -> "Review"
}

fun riskColor(band: String?): Color = when (band) {
    "low" -> StatusGood
    "medium" -> StatusWarning
    "high", "critical" -> StatusDanger
    else -> StatusNeutral
}

fun verifiedListing(listing: com.saferesale.app.domain.model.MarketListing): Boolean =
    listing.status == "published" || listing.status == "approved"

fun timeAgo(ts: Double?): String {
    if (ts == null) return ""
    val seconds = ((System.currentTimeMillis() / 1000) - ts).toLong().coerceAtLeast(0)
    return when {
        seconds < 60 -> "just now"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86400 -> "${seconds / 3600}h ago"
        seconds < 604800 -> "${seconds / 86400}d ago"
        else -> "${seconds / 604800}w ago"
    }
}