package org.onebusaway.vehicletracker.data.api

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** The paths that hand out tokens. They go without one: see [TokenAuthenticator]. */
private val TOKEN_EXCHANGE_PATHS = listOf("/api/v1/auth/login", "/api/v1/auth/refresh")

private fun Request.isTokenExchange() = TOKEN_EXCHANGE_PATHS.any { url.encodedPath.endsWith(it) }

class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider()
        val request = if (token != null && !chain.request().isTokenExchange()) {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        } else chain.request()
        return chain.proceed(request)
    }
}

/**
 * Builds the app's [TrackerApi]. Given a [refresher], its client also refreshes the access token
 * after a 401 and sends the request again.
 */
class ApiFactory(
    private val refresher: TokenRefresher? = null,
    private val tokenProvider: () -> String?,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // The refresh call goes through this client as it is, with no access token and no
    // authenticator, so a refresh can never set off another. The API client shares its
    // connections.
    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val client = baseClient.newBuilder()
        .addInterceptor(AuthInterceptor(tokenProvider))
        .apply { if (refresher != null) authenticator(TokenAuthenticator(refresher)) }
        .build()

    fun create(baseUrl: String): TrackerApi = retrofit(baseUrl, client).create(TrackerApi::class.java)

    fun createRefreshApi(baseUrl: String): RefreshApi = retrofit(baseUrl, baseClient).create(RefreshApi::class.java)

    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}
