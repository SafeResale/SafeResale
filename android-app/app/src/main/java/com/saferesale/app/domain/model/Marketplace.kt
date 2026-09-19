package com.saferesale.app.domain.model

data class MarketListing(
    val _id: String = "",
    val seller_id: String = "",
    val category: String = "",
    val title: String = "",
    val price: Double = 0.0,
    val brand: String? = null,
    val model: String? = null,
    val storage: String? = null,
    val battery_health: String? = null,
    val year: Int? = null,
    val odometer: Int? = null,
    val seller_condition: String? = null,
    val notes: String? = null,
    val status: String = "draft",
    val risk: MarketRisk? = null,
    val risk_band: String? = null,
    val diagnostic_score: Int? = null,
    val is_fit_to_show: Boolean? = null,
    val submission_url: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val created_at: Double? = null,
    val trust: TrustRating? = null,
    val ml_models: MlModelsReport? = null,
)

/** What each of SafeResale's 4 ML models concluded (backend-computed,
 * rendered verbatim on the product page). */
data class MlModelsReport(
    val m1: MlModelVerdict? = null,
    val m2: MlModelVerdict? = null,
    val m3: MlModelVerdict? = null,
    val m6: MlModelVerdict? = null,
) {
    val list: List<MlModelVerdict> get() = listOfNotNull(m1, m2, m3, m6)
}

data class MlModelVerdict(
    val id: String = "",
    val name: String = "",
    val model: String = "",
    val simulated: Boolean = true,
    val verdict: String = "",
    val ok: Boolean = true,
    val items: List<MlModelItem> = emptyList(),
)

data class MlModelItem(
    val label: String = "",
    val value: String = "",
)

/** Server-computed trust verdict — the app renders this verbatim. */
data class TrustRating(
    val score: Int? = null,
    val band: String? = null,
    val label: String? = null,
)

data class MarketRisk(
    val adjusted_score: Double? = null,
    val band: String? = null,
    val badge: String? = null,
)

data class ListingsResponse(
    val items: List<MarketListing> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val page_size: Int = 20,
)

data class ListingResponse(
    val listing: MarketListing = MarketListing(),
)

data class LatestScoresResponse(
    val listing_id: String = "",
    val diagnostics: Map<String, Any>? = null,
    val diagnostic_score: Int? = null,
    val risk: LatestRisk? = null,
    val decision: Map<String, Any>? = null,
    val condition: Map<String, Any>? = null,
    val images: Map<String, Any>? = null,
)

data class LatestRisk(
    val adjusted_score: Double? = null,
    val listing_id: String? = null,
)

data class ListingImageInfo(
    val _id: String = "",
    val listing_id: String = "",
    val angle: String = "",
    val stored_key: String = "",
    val sha256: String? = null,
    val quality: Map<String, Any>? = null,
    val server_quality: Map<String, Any>? = null,
    val created_at: Double? = null,
    val server_timestamp: Double? = null,
)

data class MarketCategory(
    val category: String = "",
    val schema: List<String> = emptyList(),
)

data class UserProfile(
    val user: UserProfileDetails = UserProfileDetails(),
)

data class UserProfileDetails(
    val id: String = "",
    val email: String = "",
    val name: String = "",
    val role: String = "seller",
    val verified: Boolean = false,
    val phone: String? = null,
)

data class ReportRequest(
    val reason: String,
    val description: String = "",
)

data class ContactRequest(
    val name: String,
    val email: String,
    val subject: String,
    val message: String,
)