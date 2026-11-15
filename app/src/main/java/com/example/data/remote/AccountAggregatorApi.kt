package com.example.data.remote

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

/**
 * RBI-Regulated Account Aggregator (ReBIT / Sahamati AA v2.0) & Official Bank / FinTrack Backend API.
 * Never fabricates balances or transactions; reports SETUP_REQUIRED when credentials are absent.
 */
@JsonClass(generateAdapter = true)
data class AaConsentRequest(
    @Json(name = "ver") val ver: String = "2.0.0",
    @Json(name = "timestamp") val timestamp: String,
    @Json(name = "txnid") val txnId: String,
    @Json(name = "ConsentDetail") val consentDetail: AaConsentDetail
)

@JsonClass(generateAdapter = true)
data class AaConsentDetail(
    @Json(name = "consentStart") val consentStart: String,
    @Json(name = "consentExpiry") val consentExpiry: String,
    @Json(name = "consentMode") val consentMode: String = "VIEW",
    @Json(name = "fetchType") val fetchType: String = "PERIODIC",
    @Json(name = "fiTypes") val fiTypes: List<String> = listOf("DEPOSIT", "CREDIT_CARD"),
    @Json(name = "customerId") val customerId: String
)

@JsonClass(generateAdapter = true)
data class AaConsentResponse(
    @Json(name = "ConsentHandle") val consentHandle: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "redirectUrl") val redirectUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class AaFiFetchResponse(
    @Json(name = "status") val status: String = "",
    @Json(name = "accounts") val accounts: List<AaAccountPayload> = emptyList()
)

@JsonClass(generateAdapter = true)
data class AaAccountPayload(
    @Json(name = "maskedAccNumber") val maskedAccNumber: String = "",
    @Json(name = "fipName") val fipName: String = "",
    @Json(name = "currentBalance") val currentBalance: Double? = null,
    @Json(name = "transactions") val transactions: List<AaTransactionDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class AaTransactionDto(
    @Json(name = "txnId") val txnId: String = "",
    @Json(name = "amount") val amount: Double = 0.0,
    @Json(name = "type") val type: String = "DEBIT",
    @Json(name = "mode") val mode: String = "UPI",
    @Json(name = "narration") val narration: String = "",
    @Json(name = "reference") val reference: String = "",
    @Json(name = "valueDate") val valueDate: String = "",
    @Json(name = "transactionTimestamp") val transactionTimestamp: String = ""
)

interface AccountAggregatorApi {
    @POST("v2/consents")
    suspend fun createConsent(
        @Header("client_api_key") apiKey: String,
        @Header("x-fiu-id") fiuId: String,
        @Body body: AaConsentRequest
    ): Response<AaConsentResponse>

    @GET("v2/consents/handle/{consentHandle}")
    suspend fun getConsentStatus(
        @Header("client_api_key") apiKey: String,
        @Path("consentHandle") consentHandle: String
    ): Response<AaConsentResponse>

    @GET("v2/fi/fetch/{sessionId}")
    suspend fun fetchFinancialInformation(
        @Header("client_api_key") apiKey: String,
        @Path("sessionId") sessionId: String
    ): Response<AaFiFetchResponse>
}

sealed interface IntegrationHealth {
    data object Configured : IntegrationHealth
    data class SetupRequired(val missingKeys: List<String>, val instructions: String) : IntegrationHealth
}

object AccountAggregatorClient {

    fun checkConfiguration(): IntegrationHealth {
        val baseUrl = safeBuildConfigField("FINTRACK_AA_BASE_URL")
        val clientId = safeBuildConfigField("FINTRACK_AA_CLIENT_ID")
        val clientSecret = safeBuildConfigField("FINTRACK_AA_CLIENT_SECRET")

        val missing = mutableListOf<String>()
        if (baseUrl.isBlank()) missing.add("FINTRACK_AA_BASE_URL")
        if (clientId.isBlank()) missing.add("FINTRACK_AA_CLIENT_ID")
        if (clientSecret.isBlank()) missing.add("FINTRACK_AA_CLIENT_SECRET")

        return if (missing.isEmpty()) {
            IntegrationHealth.Configured
        } else {
            IntegrationHealth.SetupRequired(
                missingKeys = missing,
                instructions = "Configure RBI-licensed Account Aggregator FIU credentials (e.g., Setu AA, Finvu, or OneMoney) in the AI Studio Secrets panel."
            )
        }
    }

    fun createApiIfConfigured(): AccountAggregatorApi? {
        val baseUrl = safeBuildConfigField("FINTRACK_AA_BASE_URL")
        if (baseUrl.isBlank() || (!baseUrl.startsWith("https://") && !baseUrl.startsWith("http://"))) {
            return null
        }
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(AccountAggregatorApi::class.java)
    }

    fun safeBuildConfigField(name: String): String {
        return try {
            val field = BuildConfig::class.java.getField(name)
            (field.get(null) as? String).orEmpty().trim()
        } catch (_: Exception) {
            ""
        }
    }
}
