package com.aizoid.unityadsdemo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aizoid.unityadsdemo.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ad_stats_prefs")

data class AdStats(
    val coins: Int = 0,
    val totalClicks: Int = 0,
    val rewardedWatched: Int = 0,
    val interstitialsShown: Int = 0,
    val adsSkipped: Int = 0,
    val loadSuccess: Int = 0,
    val loadFailed: Int = 0
) {
    val totalImpressions: Int
        get() = rewardedWatched + interstitialsShown

    val totalCoinsEarned: Int
        get() = rewardedWatched * Constants.REWARD_COINS_PER_AD

    val ctrPercent: Double
        get() = if (totalImpressions > 0) {
            (totalClicks.toDouble() / totalImpressions) * 100.0
        } else {
            0.0
        }

    val estimatedEarningsUsd: Double
        get() = (totalImpressions / 1000.0) * Constants.ESTIMATED_ECPM_USD
}

class AdStatsRepository(private val context: Context) {

    private object PreferencesKeys {
        val COINS = intPreferencesKey("user_coins")
        val TOTAL_CLICKS = intPreferencesKey("total_ad_clicks")
        val REWARDED_WATCHED = intPreferencesKey("rewarded_watched")
        val INTERSTITIALS_SHOWN = intPreferencesKey("interstitials_shown")
        val ADS_SKIPPED = intPreferencesKey("ads_skipped")
        val LOAD_SUCCESS = intPreferencesKey("load_success")
        val LOAD_FAILED = intPreferencesKey("load_failed")
    }

    val adStatsFlow: Flow<AdStats> = context.dataStore.data.map { preferences ->
        AdStats(
            coins = preferences[PreferencesKeys.COINS] ?: 0,
            totalClicks = preferences[PreferencesKeys.TOTAL_CLICKS] ?: 0,
            rewardedWatched = preferences[PreferencesKeys.REWARDED_WATCHED] ?: 0,
            interstitialsShown = preferences[PreferencesKeys.INTERSTITIALS_SHOWN] ?: 0,
            adsSkipped = preferences[PreferencesKeys.ADS_SKIPPED] ?: 0,
            loadSuccess = preferences[PreferencesKeys.LOAD_SUCCESS] ?: 0,
            loadFailed = preferences[PreferencesKeys.LOAD_FAILED] ?: 0
        )
    }

    suspend fun addRewardCoins(amount: Int = Constants.REWARD_COINS_PER_AD) {
        context.dataStore.edit { preferences ->
            val currentCoins = preferences[PreferencesKeys.COINS] ?: 0
            val currentRewarded = preferences[PreferencesKeys.REWARDED_WATCHED] ?: 0
            preferences[PreferencesKeys.COINS] = currentCoins + amount
            preferences[PreferencesKeys.REWARDED_WATCHED] = currentRewarded + 1
        }
    }

    suspend fun recordInterstitialShown() {
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.INTERSTITIALS_SHOWN] ?: 0
            preferences[PreferencesKeys.INTERSTITIALS_SHOWN] = current + 1
        }
    }

    suspend fun recordAdClick() {
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.TOTAL_CLICKS] ?: 0
            preferences[PreferencesKeys.TOTAL_CLICKS] = current + 1
        }
    }

    suspend fun recordAdSkipped() {
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.ADS_SKIPPED] ?: 0
            preferences[PreferencesKeys.ADS_SKIPPED] = current + 1
        }
    }

    suspend fun recordLoadSuccess() {
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.LOAD_SUCCESS] ?: 0
            preferences[PreferencesKeys.LOAD_SUCCESS] = current + 1
        }
    }

    suspend fun recordLoadFailed() {
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.LOAD_FAILED] ?: 0
            preferences[PreferencesKeys.LOAD_FAILED] = current + 1
        }
    }

    suspend fun resetAllStats() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.COINS] = 0
            preferences[PreferencesKeys.TOTAL_CLICKS] = 0
            preferences[PreferencesKeys.REWARDED_WATCHED] = 0
            preferences[PreferencesKeys.INTERSTITIALS_SHOWN] = 0
            preferences[PreferencesKeys.ADS_SKIPPED] = 0
            preferences[PreferencesKeys.LOAD_SUCCESS] = 0
            preferences[PreferencesKeys.LOAD_FAILED] = 0
        }
    }
}