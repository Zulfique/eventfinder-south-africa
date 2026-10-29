package com.eventfinder.app.data.remote

import com.eventfinder.app.BuildConfig
import com.eventfinder.app.utils.AppLogger
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.File
import java.util.concurrent.TimeUnit

// ---------------------------------------------------------------------------
// DTOs — mirror the ASP.NET Core API envelope and payloads.
// ---------------------------------------------------------------------------

data class ApiEnvelope<T>(
    val success: Boolean? = null,
    val message: String? = null,
    val data: T? = null,
    val errors: Map<String, List<String>>? = null
)

data class ApiUserDto(
    val id: String,
    val email: String,
    val fullName: String,
    val preferredLanguage: String,
    val createdAt: String? = null,
    @SerializedName("hasGoogleAccount") val hasGoogleAccount: Boolean = false
)

data class FirebaseTokenResponse(
    @SerializedName("firebaseToken") val firebaseToken: String
)

/** Result of a successful blob upload to Azure Blob Storage. */
data class BlobUploadResponse(
    val url: String,
    val key: String,
    @SerializedName("sizeBytes") val sizeBytes: Long = 0L
)

/** Whether the server has Azure Blob Storage configured. */
data class BlobStatusResponse(
    val configured: Boolean = false
)

/** Payload sent to the blob upload endpoint. */
data class BlobUploadBody(
    val fileName: String,
    val contentBase64: String,
    val contentType: String? = null
)

data class ApiAuthResponse(
    val user: ApiUserDto,
    val token: String,
    val expiresAt: String? = null
)

data class ApiEventDto(
    val id: String,
    val title: String,
    val description: String,
    val venueName: String,
    val address: String,
    val city: String,
    val latitude: Double,
    val longitude: Double,
    val startUtc: String,
    val endUtc: String,
    val category: String,
    val imageUrl: String? = null,
    val organizerId: String,
    val organizerName: String,
    val isPublic: Boolean = true,
    val attendeeCount: Int = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class ApiRsvpRequest(val status: String)

data class RegisterBody(
    val fullName: String,
    val email: String,
    val password: String,
    val language: String
)

data class LoginBody(val email: String, val password: String)

data class GoogleBody(val idToken: String)

data class ForgotBody(val email: String)

data class ResetBody(val email: String, val token: String, val newPassword: String)

data class ChangePasswordBody(val currentPassword: String, val newPassword: String)

data class UpdateProfileBody(val fullName: String, val email: String)

data class RequestEmailChangeBody(val newEmail: String)

data class VerifyEmailChangeBody(val token: String)

data class CreateEventBody(
    val title: String,
    val description: String,
    val venueName: String,
    val address: String,
    val city: String,
    val latitude: Double,
    val longitude: Double,
    val startUtc: String,
    val endUtc: String,
    val category: String,
    val imageUrl: String?,
    val isPublic: Boolean
)

data class ApiReviewDto(
    val id: String,
    val eventId: String,
    val userId: String,
    val fullName: String,
    val rating: Int,
    val comment: String? = null,
    @SerializedName("isVerifiedAttendee") val isVerifiedAttendee: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class ApiReviewListDto(
    val average: Double = 0.0,
    val count: Int = 0,
    val items: List<ApiReviewDto> = emptyList(),
    val mine: ApiReviewDto? = null
)

data class ApiReviewSummaryDto(
    val eventId: String,
    val average: Double,
    val count: Int
)

data class ApiReviewBody(val rating: Int, val comment: String? = null)

/** Retrofit surface for the EventFinder ASP.NET Core backend. */
interface EventFinderApiService {
    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterBody): retrofit2.Response<ApiEnvelope<ApiAuthResponse>>

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginBody): retrofit2.Response<ApiEnvelope<ApiAuthResponse>>

    @POST("api/auth/google")
    suspend fun googleSignIn(@Body body: GoogleBody): retrofit2.Response<ApiEnvelope<ApiAuthResponse>>

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotBody): retrofit2.Response<ApiEnvelope<Unit>>

    @POST("api/auth/reset-password")
    suspend fun resetPassword(@Body body: ResetBody): retrofit2.Response<ApiEnvelope<Unit>>

    @POST("api/auth/change-password")
    suspend fun changePassword(
        @Header("Authorization") bearer: String,
        @Body body: ChangePasswordBody
    ): retrofit2.Response<ApiEnvelope<Unit>>

    @PUT("api/auth/me")
    suspend fun updateProfile(
        @Header("Authorization") bearer: String,
        @Body body: UpdateProfileBody
    ): retrofit2.Response<ApiEnvelope<ApiUserDto>>

    @GET("api/auth/me")
    suspend fun me(
        @Header("Authorization") bearer: String
    ): retrofit2.Response<ApiEnvelope<ApiUserDto>>

    @GET("api/auth/firebase-token")
    suspend fun getFirebaseToken(
        @Header("Authorization") bearer: String
    ): retrofit2.Response<ApiEnvelope<FirebaseTokenResponse>>

    @POST("api/auth/request-email-change")
    suspend fun requestEmailChange(
        @Header("Authorization") bearer: String,
        @Body body: RequestEmailChangeBody
    ): retrofit2.Response<ApiEnvelope<Unit>>

    @POST("api/auth/verify-email-change")
    suspend fun verifyEmailChange(
        @Body body: VerifyEmailChangeBody
    ): retrofit2.Response<ApiEnvelope<Unit>>

    @DELETE("api/auth/me")
    suspend fun deleteAccount(
        @Header("Authorization") bearer: String
    ): retrofit2.Response<ApiEnvelope<Unit>>

    @GET("api/events/public")
    suspend fun publicEvents(
        @Query("city") city: String? = null,
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
        @Query("radiusKm") radiusKm: Double? = null
    ): retrofit2.Response<ApiEnvelope<List<ApiEventDto>>>

    @POST("api/events")
    suspend fun createEvent(
        @Header("Authorization") bearer: String,
        @Body body: CreateEventBody
    ): retrofit2.Response<ApiEnvelope<ApiEventDto>>

    @PUT("api/events/{id}")
    suspend fun updateEvent(
        @Header("Authorization") bearer: String,
        @Path("id") id: String,
        @Body body: CreateEventBody
    ): retrofit2.Response<ApiEnvelope<ApiEventDto>>

    @DELETE("api/events/{id}")
    suspend fun deleteEvent(
        @Header("Authorization") bearer: String,
        @Path("id") id: String
    ): retrofit2.Response<ApiEnvelope<Unit>>

    @POST("api/events/{id}/rsvp")
    suspend fun rsvp(
        @Header("Authorization") bearer: String,
        @Path("id") id: String,
        @Body body: ApiRsvpRequest
    ): retrofit2.Response<ApiEnvelope<Unit>>

    @GET("api/events/{eventId}/reviews")
    suspend fun getReviews(
        @Path("eventId") eventId: String
    ): retrofit2.Response<ApiEnvelope<ApiReviewListDto>>

    @PUT("api/events/{eventId}/reviews")
    suspend fun putReview(
        @Header("Authorization") bearer: String,
        @Path("eventId") eventId: String,
        @Body body: ApiReviewBody
    ): retrofit2.Response<ApiEnvelope<ApiReviewListDto>>

    @DELETE("api/events/{eventId}/reviews")
    suspend fun deleteReview(
        @Header("Authorization") bearer: String,
        @Path("eventId") eventId: String
    ): retrofit2.Response<ApiEnvelope<ApiReviewListDto>>

    @GET("api/reviews/summary")
    suspend fun reviewSummary(
        @Query("id") ids: List<String>
    ): retrofit2.Response<ApiEnvelope<List<ApiReviewSummaryDto>>>

    @DELETE("api/events/{id}/rsvp")
    suspend fun cancelRsvp(
        @Header("Authorization") bearer: String,
        @Path("id") id: String
    ): retrofit2.Response<ApiEnvelope<Unit>>

    @GET("api/blobs/status")
    suspend fun blobStatus(): retrofit2.Response<ApiEnvelope<BlobStatusResponse>>

    @POST("api/blobs/upload")
    suspend fun uploadBlob(
        @Header("Authorization") bearer: String,
        @Body body: BlobUploadBody
    ): retrofit2.Response<ApiEnvelope<BlobUploadResponse>>

    @DELETE("api/blobs/{key}")
    suspend fun deleteBlob(
        @Header("Authorization") bearer: String,
        @Path("key") key: String
    ): retrofit2.Response<ApiEnvelope<Unit>>
}

/**
 * Thin wrapper around [EventFinderApiService] that normalises the API's
 * response envelope into Kotlin [Result]s. Every network call is optional:
 * when the backend is unreachable the callers fall back to offline-only
 * Room behaviour, so the app never hard-depends on the server.
 */
class EventFinderApiClient(
    baseUrl: String = BuildConfig.API_BASE_URL,
    private val gson: Gson = GsonBuilder().create()
) {

    val service: EventFinderApiService = Retrofit.Builder()
        .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
        .client(
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(EventFinderApiService::class.java)

    private val tag = "EventFinderApiClient"

    /** Raised when the server answers but reports success=false. */
    class ApiException(val code: Int, message: String, val fieldErrors: Map<String, List<String>> = emptyMap()) :
        Exception(message)

    /** Raised when the device could not reach the server at all. */
    class NetworkException(cause: Throwable) : Exception("unreachable", cause)

    suspend fun register(fullName: String, email: String, password: String, language: String): Result<ApiAuthResponse> =
        wrap("register") { service.register(RegisterBody(fullName, email, password, language)) }

    suspend fun login(email: String, password: String): Result<ApiAuthResponse> =
        wrap("login") { service.login(LoginBody(email, password)) }

    suspend fun googleSignIn(idToken: String): Result<ApiAuthResponse> =
        wrap("google") { service.googleSignIn(GoogleBody(idToken)) }

    suspend fun forgotPassword(email: String): Result<Unit> =
        wrap("forgotPassword") { service.forgotPassword(ForgotBody(email)) }

    suspend fun resetPassword(email: String, token: String, newPassword: String): Result<Unit> =
        wrap("resetPassword") { service.resetPassword(ResetBody(email, token, newPassword)) }

    suspend fun changePassword(bearer: String, currentPassword: String, newPassword: String): Result<Unit> =
        wrap("changePassword") { service.changePassword(bearer, ChangePasswordBody(currentPassword, newPassword)) }

    suspend fun updateProfile(bearer: String, fullName: String, email: String): Result<ApiUserDto> =
        wrap("updateProfile") { service.updateProfile(bearer, UpdateProfileBody(fullName, email)) }

    suspend fun me(bearer: String): Result<ApiUserDto> =
        wrap("me") { service.me(bearer) }

    suspend fun firebaseToken(bearer: String): Result<FirebaseTokenResponse> =
        wrap("firebaseToken") { service.getFirebaseToken(bearer) }

    suspend fun requestEmailChange(bearer: String, newEmail: String): Result<Unit> =
        wrap("requestEmailChange") { service.requestEmailChange(bearer, RequestEmailChangeBody(newEmail)) }

    suspend fun verifyEmailChange(token: String): Result<Unit> =
        wrap("verifyEmailChange") { service.verifyEmailChange(VerifyEmailChangeBody(token)) }

    suspend fun deleteAccount(bearer: String): Result<Unit> =
        wrap("deleteAccount") { service.deleteAccount(bearer) }

    suspend fun publicEvents(city: String? = null, lat: Double? = null, lng: Double? = null, radiusKm: Double? = null): Result<List<ApiEventDto>> =
        wrap("publicEvents") { service.publicEvents(city, lat, lng, radiusKm) }

    suspend fun createEvent(bearer: String, body: CreateEventBody): Result<ApiEventDto> =
        wrap("createEvent") { service.createEvent(bearer, body) }

    suspend fun updateEvent(bearer: String, id: String, body: CreateEventBody): Result<ApiEventDto> =
        wrap("updateEvent") { service.updateEvent(bearer, id, body) }

    suspend fun deleteEvent(bearer: String, id: String): Result<Unit> =
        wrap("deleteEvent") { service.deleteEvent(bearer, id) }

    suspend fun getReviews(eventId: String): Result<ApiReviewListDto> =
        wrap("getReviews") { service.getReviews(eventId) }

    suspend fun putReview(bearer: String, eventId: String, rating: Int, comment: String?): Result<ApiReviewListDto> =
        wrap("putReview") { service.putReview(bearer, eventId, ApiReviewBody(rating, comment)) }

    suspend fun deleteReview(bearer: String, eventId: String): Result<ApiReviewListDto> =
        wrap("deleteReview") { service.deleteReview(bearer, eventId) }

    suspend fun reviewSummary(ids: List<String>): Result<List<ApiReviewSummaryDto>> =
        wrap("reviewSummary") { service.reviewSummary(ids) }

    suspend fun rsvp(bearer: String, eventId: String, status: String): Result<Unit> =
        wrap("rsvp") { service.rsvp(bearer, eventId, ApiRsvpRequest(status)) }

    suspend fun cancelRsvp(bearer: String, eventId: String): Result<Unit> =
        wrap("cancelRsvp") { service.cancelRsvp(bearer, eventId) }

    /**
     * Uploads [bytes] as an event photo to the server's Azure blob container
     * and returns the permanent public URL of the stored object.
     */
    suspend fun uploadBlob(
        bearer: String,
        fileName: String,
        bytes: ByteArray,
        contentType: String? = null
    ): Result<BlobUploadResponse> = wrap("uploadBlob") {
        service.uploadBlob(
            bearer,
            BlobUploadBody(
                fileName = fileName,
                contentBase64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP),
                contentType = contentType
            )
        )
    }

    /** Removes a previously uploaded blob from the server's Azure container. */
    suspend fun deleteBlob(bearer: String, key: String): Result<Unit> =
        wrap("deleteBlob") { service.deleteBlob(bearer, key) }

    /** True when the server reports that Azure Blob Storage is configured. */
    suspend fun blobStorageStatus(): Result<Boolean> {
        val result = wrap("blobStatus") { service.blobStatus() }
        return result.map { it.configured }
    }

    /**
     * Extracts the container key from a stored public URL, or returns null
     * when the URL does not point at the blob store.
     */
    fun blobKeyFromUrl(url: String?): String? =
        com.eventfinder.app.data.cloud.AzureBlobKeys.fromUrl(url)

    private suspend fun <T> wrap(
        op: String,
        call: suspend () -> retrofit2.Response<ApiEnvelope<T>>
    ): Result<T> = try {
        val response = call()
        val envelope = response.body()
        when {
            response.isSuccessful && envelope?.success == true && envelope.data != null ->
                Result.success(envelope.data)

            response.isSuccessful && envelope?.success == true ->
                Result.success(@Suppress("UNCHECKED_CAST") (Unit as T))

            else -> {
                val message = envelope?.message ?: "HTTP ${response.code()}"
                AppLogger.w(tag, "$op failed (${response.code()}): $message")
                Result.failure(ApiException(response.code(), message, envelope?.errors ?: emptyMap()))
            }
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        // Preserve structured concurrency: cancelled jobs must stay cancelled.
        throw e
    } catch (e: Exception) {
        AppLogger.w(tag, "$op unreachable: ${e.message}")
        Result.failure(NetworkException(e))
    }

    companion object {
        /** Shared instance used by the AppContainer. */
        val default: EventFinderApiClient by lazy { EventFinderApiClient() }

        fun eventBody(
            title: String,
            description: String,
            venueName: String,
            address: String,
            city: String,
            latitude: Double,
            longitude: Double,
            startUtcMillis: Long,
            endUtcMillis: Long,
            category: String,
            imageUrl: String?,
            isPublic: Boolean
        ): CreateEventBody = CreateEventBody(
            title = title,
            description = description,
            venueName = venueName,
            address = address,
            city = city,
            latitude = latitude,
            longitude = longitude,
            startUtc = isoUtc(startUtcMillis),
            endUtc = isoUtc(endUtcMillis),
            category = category,
            imageUrl = imageUrl,
            isPublic = isPublic
        )

        fun isoUtc(epochMillis: Long): String =
            java.time.Instant.ofEpochMilli(epochMillis).toString()

        fun parseIso(iso: String?): Long? = iso?.let {
            runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
        }
    }
}
