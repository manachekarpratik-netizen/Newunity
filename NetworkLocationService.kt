package com.aizoid.unityadsdemo.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class NetworkLocationInfo(
    val ip: String,
    val country: String,
    val countryFlag: String,
    val region: String,
    val city: String,
    val isp: String,
    val timezone: String,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)

class NetworkLocationService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchLocation(): Result<NetworkLocationInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://ipwho.is/")
                .header("User-Agent", "praapp-UnityAdsSuite/1.0 (Android)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP Error: ${response.code}"))
                }

                val bodyString = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response body"))

                val json = JSONObject(bodyString)
                val success = json.optBoolean("success", false)
                if (!success) {
                    val message = json.optString("message", "IP lookup failed")
                    return@withContext Result.failure(Exception(message))
                }

                val ip = json.optString("ip", "Unknown")
                val country = json.optString("country", "Unknown")
                val flagObj = json.optJSONObject("flag")
                val countryFlag = flagObj?.optString("emoji") ?: ""
                val region = json.optString("region", "Unknown")
                val city = json.optString("city", "Unknown")
                
                val connectionObj = json.optJSONObject("connection")
                val isp = connectionObj?.optString("isp") ?: json.optString("connection", "Unknown")

                val timezoneObj = json.optJSONObject("timezone")
                val timezone = timezoneObj?.optString("id") 
                    ?: timezoneObj?.optString("utc") 
                    ?: "UTC"

                Result.success(
                    NetworkLocationInfo(
                        ip = ip,
                        country = country,
                        countryFlag = countryFlag,
                        region = region,
                        city = city,
                        isp = isp,
                        timezone = timezone
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}