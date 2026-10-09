package com.saferesale.app.data.marketplace

import com.saferesale.app.data.ApiClient
import com.saferesale.app.data.InspectionReq
import com.saferesale.app.domain.model.ChatListingBrief
import com.saferesale.app.domain.model.ChatMessage
import com.saferesale.app.domain.model.ChatThread
import com.saferesale.app.domain.model.ChatUserBrief
import com.saferesale.app.domain.model.ContactRequest
import com.saferesale.app.domain.model.ListingImageInfo
import com.saferesale.app.domain.model.LatestRisk
import com.saferesale.app.domain.model.LatestScoresResponse
import com.saferesale.app.domain.model.ListingResponse
import com.saferesale.app.domain.model.ListingsResponse
import com.saferesale.app.domain.model.MarketCategory
import com.saferesale.app.domain.model.MarketListing
import com.saferesale.app.domain.model.ReportRequest
import com.saferesale.app.domain.model.UserProfile
import com.saferesale.app.domain.model.UserProfileDetails

object MarketplaceRepository {

    fun bearer(token: String?) = "Bearer ${token.orEmpty()}"

    suspend fun listings(token: String?, category: String?, page: Int, pageSize: Int): ListingsResponse =
        ApiClient.service.listings(category, page, pageSize, bearer(token))

    suspend fun myListings(token: String?): List<MarketListing> =
        ApiClient.service.myListings(bearer(token)).items

    suspend fun categories(): List<MarketCategory> = ApiClient.service.categories()

    suspend fun listing(token: String?, id: String): MarketListing =
        ApiClient.service.listing(id, bearer(token)).listing

    suspend fun images(token: String?, listingId: String): List<ListingImageInfo> =
        ApiClient.service.listImages(listingId, bearer(token)).map { m ->
            ListingImageInfo(
                _id = m["_id"]?.toString() ?: "",
                listing_id = m["listing_id"]?.toString() ?: "",
                angle = m["angle"]?.toString() ?: "",
                stored_key = m["stored_key"]?.toString() ?: "",
                sha256 = m["sha256"]?.toString(),
            )
        }

    suspend fun latestScores(token: String?, listingId: String): LatestScoresResponse {
        val m = ApiClient.service.latestScores(listingId, bearer(token))
        val risk = m["risk"] as? Map<*, *>
        return LatestScoresResponse(
            listing_id = m["listing_id"]?.toString() ?: listingId,
            diagnostic_score = (m["diagnostic_score"] as? Number)?.toInt(),
            risk = LatestRisk(
                adjusted_score = (risk?.get("adjusted_score") as? Number)?.toDouble(),
            ),
            decision = m["decision"] as? Map<String, Any>,
            condition = m["condition"] as? Map<String, Any>,
        )
    }

    suspend fun profile(token: String?): UserProfileDetails =
        ApiClient.service.me(bearer(token)).user

    suspend fun updateProfile(token: String?, name: String?, phone: String?): UserProfileDetails {
        val body = buildMap {
            if (!name.isNullOrBlank()) put("name", name)
            if (!phone.isNullOrBlank()) put("phone", phone)
        }
        val resp: UserProfile = ApiClient.service.updateMe(body, bearer(token))
        return resp.user
    }

    suspend fun report(token: String?, listingId: String, reason: String, description: String): String {
        val resp = ApiClient.service.reportListing(listingId, ReportRequest(reason, description), bearer(token))
        return (resp["report"] as? Map<*, *>)?.get("status")?.toString() ?: "pending"
    }

    suspend fun contact(name: String, email: String, subject: String, message: String): String {
        val resp = ApiClient.service.contact(ContactRequest(name, email, subject, message))
        return resp["message"]?.toString() ?: "Sent"
    }

    suspend fun inspections(token: String?): List<Map<String, Any>> =
        (ApiClient.service.listInspections(bearer(token))["items"] as? List<*>)?.filterIsInstance<Map<String, Any>>()
            ?: emptyList()

    suspend fun createInspection(token: String?, listingId: String, preferredDate: String?, note: String?): Map<String, Any> =
        ApiClient.service.createInspection(InspectionReq(listingId, preferredDate, note), bearer(token))

    // ── Chat ──

    @Suppress("UNCHECKED_CAST")
    suspend fun getThreads(token: String?): List<ChatThread> {
        val raw = ApiClient.service.chatThreads(bearer(token))
        val list = raw["threads"] as? List<*> ?: return emptyList()
        return list.filterIsInstance<Map<String, Any>>().map { m ->
            val listingMap = m["listing"] as? Map<String, Any>
            val otherMap = m["other_user"] as? Map<String, Any>
            ChatThread(
                thread_id = m["thread_id"]?.toString() ?: "",
                listing_id = m["listing_id"]?.toString() ?: "",
                listing = listingMap?.let {
                    ChatListingBrief(
                        id = it["id"]?.toString() ?: m["listing_id"]?.toString() ?: "",
                        title = it["title"]?.toString() ?: "",
                        price = (it["price"] as? Number)?.toDouble() ?: 0.0,
                        category = it["category"]?.toString() ?: "",
                        status = it["status"]?.toString() ?: "",
                    )
                },
                other_user = otherMap?.let {
                    ChatUserBrief(
                        id = it["id"]?.toString() ?: "",
                        name = it["name"]?.toString() ?: "User",
                        email = it["email"]?.toString() ?: "",
                    )
                },
                last_message = m["last_message"]?.toString() ?: "",
                last_message_at = (m["last_message_at"] as? Number)?.toDouble() ?: 0.0,
                updated_at = (m["updated_at"] as? Number)?.toDouble() ?: 0.0,
                unread = (m["unread"] as? Number)?.toInt() ?: 0,
                role = m["role"]?.toString() ?: "buying",
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun getMessages(token: String?, listingId: String, page: Int = 1, pageSize: Int = 50, withUser: String? = null): Pair<List<ChatMessage>, Int> {
        val raw = ApiClient.service.chatMessages(listingId, page, pageSize, withUser, bearer(token))
        val list = raw["messages"] as? List<*> ?: emptyList<Any>()
        val total = (raw["total"] as? Number)?.toInt() ?: 0
        val msgs = list.filterIsInstance<Map<String, Any>>().map { m ->
            ChatMessage(
                id = m["id"]?.toString() ?: m["_id"]?.toString() ?: "",
                thread_id = m["thread_id"]?.toString() ?: "",
                listing_id = m["listing_id"]?.toString() ?: listingId,
                sender_id = m["sender_id"]?.toString() ?: "",
                message = m["message"]?.toString() ?: m["body"]?.toString() ?: "",
                offer_price = (m["offer_price"] as? Number)?.toDouble(),
                created_at = (m["created_at"] as? Number)?.toDouble() ?: 0.0,
                type = m["type"]?.toString() ?: "text",
                read = m["read"] as? Boolean ?: false,
            )
        }
        return msgs to total
    }

    suspend fun sendMessage(token: String?, listingId: String, message: String, offerPrice: Double? = null, recipientId: String? = null): Map<String, Any> {
        val body = mutableMapOf<String, Any?>("message" to message)
        if (offerPrice != null) body["offer_price"] = offerPrice
        if (recipientId != null) body["recipient_id"] = recipientId
        return ApiClient.service.chatSend(listingId, body, bearer(token))
    }

    suspend fun blockUser(token: String?, userId: String): Map<String, Any> =
        ApiClient.service.chatBlock(userId, bearer(token))

    suspend fun unblockUser(token: String?, userId: String): Map<String, Any> =
        ApiClient.service.chatUnblock(userId, bearer(token))

    @Suppress("UNCHECKED_CAST")
    suspend fun blockedUsers(token: String?): List<Map<String, Any>> {
        val raw = ApiClient.service.chatBlocked(bearer(token))
        return (raw["blocked"] as? List<*>)?.filterIsInstance<Map<String, Any>>() ?: emptyList()
    }