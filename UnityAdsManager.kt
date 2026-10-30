package com.aizoid.unityadsdemo.data

import android.app.Activity
import android.content.Context
import android.util.Log
import com.aizoid.unityadsdemo.Constants
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "UnityAdsManager"

enum class SdkStatus {
    NOT_INITIALIZED,
    INITIALIZING,
    INITIALIZED,
    FAILED
}

enum class AdAvailability {
    NOT_LOADED,
    LOADING,
    READY,
    FAILED
}

sealed class UserUiEvent {
    data class ShowSnackbar(val message: String) : UserUiEvent()
    data class RewardedSuccess(val coinsAwarded: Int) : UserUiEvent()
}

class UnityAdsManager(
    private val repository: AdStatsRepository
) {
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _sdkStatus = MutableStateFlow(SdkStatus.NOT_INITIALIZED)
    val sdkStatus: StateFlow<SdkStatus> = _sdkStatus.asStateFlow()

    private val _rewardedState = MutableStateFlow(AdAvailability.NOT_LOADED)
    val rewardedState: StateFlow<AdAvailability> = _rewardedState.asStateFlow()

    private val _interstitialState = MutableStateFlow(AdAvailability.NOT_LOADED)
    val interstitialState: StateFlow<AdAvailability> = _interstitialState.asStateFlow()

    private val _rawStatusMessage = MutableStateFlow("Ready to initialize")
    val rawStatusMessage: StateFlow<String> = _rawStatusMessage.asStateFlow()

    private val _userEvents = MutableSharedFlow<UserUiEvent>(extraBufferCapacity = 10)
    val userEvents: SharedFlow<UserUiEvent> = _userEvents.asSharedFlow()

    private val _isRewardedPending = MutableStateFlow(false)
    val isRewardedPending: StateFlow<Boolean> = _isRewardedPending.asStateFlow()

    private val _isInterstitialPending = MutableStateFlow(false)
    val isInterstitialPending: StateFlow<Boolean> = _isInterstitialPending.asStateFlow()

    private var pendingRewardedActivity: Activity? = null
    private var pendingInterstitialActivity: Activity? = null
    private var rewardedTimeoutJob: Job? = null
    private var interstitialTimeoutJob: Job? = null

    private var rewardedRetryJob: Job? = null
    private var interstitialRetryJob: Job? = null

    fun initialize(context: Context) {
        if (_sdkStatus.value == SdkStatus.INITIALIZING || _sdkStatus.value == SdkStatus.INITIALIZED) {
            return
        }

        _sdkStatus.value = SdkStatus.INITIALIZING
        _rawStatusMessage.value = "Initializing Unity Ads SDK..."

        UnityAds.initialize(
            context.applicationContext,
            Constants.UNITY_GAME_ID,
            Constants.TEST_MODE,
            object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() {
                    Log.d(TAG, "Unity Ads Initialization Complete")
                    _sdkStatus.value = SdkStatus.INITIALIZED
                    _rawStatusMessage.value = "Unity Ads initialized successfully (Production Mode: ${!Constants.TEST_MODE})"
                    
                    // Auto-load both Rewarded and Interstitial ads
                    loadRewardedAd()
                    loadInterstitialAd()
                }

                override fun onInitializationFailed(
                    error: UnityAds.UnityAdsInitializationError?,
                    message: String?
                ) {
                    val errorString = "UnityAdsInitializationError: $error - ${message ?: "Unknown reason"}"
                    Log.e(TAG, errorString)
                    _sdkStatus.value = SdkStatus.FAILED
                    _rawStatusMessage.value = errorString
                    _userEvents.tryEmit(UserUiEvent.ShowSnackbar(errorString))
                }
            }
        )
    }

    fun reloadAllAds() {
        loadRewardedAd()
        loadInterstitialAd()
    }

    fun loadRewardedAd() {
        if (!UnityAds.isInitialized()) {
            _rawStatusMessage.value = "Cannot load ads: Unity Ads not initialized yet"
            return
        }
        if (_rewardedState.value == AdAvailability.LOADING) {
            return
        }

        rewardedRetryJob?.cancel()
        _rewardedState.value = AdAvailability.LOADING
        _rawStatusMessage.value = "Loading Rewarded placement [${Constants.REWARDED_PLACEMENT_ID}]..."

        UnityAds.load(
            Constants.REWARDED_PLACEMENT_ID,
            object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String) {
                    Log.d(TAG, "Rewarded ad loaded: $placementId")
                    _rewardedState.value = AdAvailability.READY
                    _rawStatusMessage.value = "Rewarded ad loaded successfully: $placementId"
                    managerScope.launch {
                        repository.recordLoadSuccess()
                    }

                    // If a user click was pending, show immediately
                    if (_isRewardedPending.value && pendingRewardedActivity != null) {
                        rewardedTimeoutJob?.cancel()
                        val act = pendingRewardedActivity
                        pendingRewardedActivity = null
                        _isRewardedPending.value = false
                        act?.let { showRewardedAd(it) }
                    }
                }

                override fun onUnityAdsFailedToLoad(
                    placementId: String,
                    error: UnityAds.UnityAdsLoadError,
                    message: String
                ) {
                    val rawError = "UnityAdsLoadError: $error - $message"
                    Log.e(TAG, "Rewarded failed to load: $rawError")
                    _rewardedState.value = AdAvailability.FAILED
                    _rawStatusMessage.value = rawError
                    managerScope.launch {
                        repository.recordLoadFailed()
                    }

                    // Retry after 5 seconds repeatedly
                    rewardedRetryJob = managerScope.launch {
                        delay(5000)
                        Log.d(TAG, "Retrying Rewarded ad load after 5 seconds...")
                        loadRewardedAd()
                    }
                }
            }
        )
    }

    fun loadInterstitialAd() {
        if (!UnityAds.isInitialized()) {
            _rawStatusMessage.value = "Cannot load ads: Unity Ads not initialized yet"
            return
        }
        if (_interstitialState.value == AdAvailability.LOADING) {
            return
        }

        interstitialRetryJob?.cancel()
        _interstitialState.value = AdAvailability.LOADING
        _rawStatusMessage.value = "Loading Interstitial placement [${Constants.INTERSTITIAL_PLACEMENT_ID}]..."

        UnityAds.load(
            Constants.INTERSTITIAL_PLACEMENT_ID,
            object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String) {
                    Log.d(TAG, "Interstitial ad loaded: $placementId")
                    _interstitialState.value = AdAvailability.READY
                    _rawStatusMessage.value = "Interstitial ad loaded successfully: $placementId"
                    managerScope.launch {
                        repository.recordLoadSuccess()
                    }

                    // If a user click was pending, show immediately
                    if (_isInterstitialPending.value && pendingInterstitialActivity != null) {
                        interstitialTimeoutJob?.cancel()
                        val act = pendingInterstitialActivity
                        pendingInterstitialActivity = null
                        _isInterstitialPending.value = false
                        act?.let { showInterstitialAd(it) }
                    }
                }

                override fun onUnityAdsFailedToLoad(
                    placementId: String,
                    error: UnityAds.UnityAdsLoadError,
                    message: String
                ) {
                    val rawError = "UnityAdsLoadError: $error - $message"
                    Log.e(TAG, "Interstitial failed to load: $rawError")
                    _interstitialState.value = AdAvailability.FAILED
                    _rawStatusMessage.value = rawError
                    managerScope.launch {
                        repository.recordLoadFailed()
                    }

                    // Retry after 5 seconds repeatedly
                    interstitialRetryJob = managerScope.launch {
                        delay(5000)
                        Log.d(TAG, "Retrying Interstitial ad load after 5 seconds...")
                        loadInterstitialAd()
                    }
                }
            }
        )
    }

    fun onRewardedButtonClicked(activity: Activity) {
        if (_isRewardedPending.value) {
            return
        }

        if (_rewardedState.value == AdAvailability.READY) {
            showRewardedAd(activity)
            return
        }

        _isRewardedPending.value = true
        pendingRewardedActivity = activity

        if (_rewardedState.value != AdAvailability.LOADING) {
            loadRewardedAd()
        }

        rewardedTimeoutJob?.cancel()
        rewardedTimeoutJob = managerScope.launch {
            delay(30_000)
            if (_isRewardedPending.value) {
                _isRewardedPending.value = false
                pendingRewardedActivity = null
                _userEvents.tryEmit(UserUiEvent.ShowSnackbar("No ad available right now, please try again"))
            }
        }
    }

    fun onInterstitialButtonClicked(activity: Activity) {
        if (_isInterstitialPending.value) {
            return
        }

        if (_interstitialState.value == AdAvailability.READY) {
            showInterstitialAd(activity)
            return
        }

        _isInterstitialPending.value = true
        pendingInterstitialActivity = activity

        if (_interstitialState.value != AdAvailability.LOADING) {
            loadInterstitialAd()
        }

        interstitialTimeoutJob?.cancel()
        interstitialTimeoutJob = managerScope.launch {
            delay(30_000)
            if (_isInterstitialPending.value) {
                _isInterstitialPending.value = false
                pendingInterstitialActivity = null
                _userEvents.tryEmit(UserUiEvent.ShowSnackbar("No ad available right now, please try again"))
            }
        }
    }

    private fun showRewardedAd(activity: Activity) {
        _rewardedState.value = AdAvailability.NOT_LOADED

        UnityAds.show(
            activity,
            Constants.REWARDED_PLACEMENT_ID,
            UnityAdsShowOptions(),
            object : IUnityAdsShowListener {
                override fun onUnityAdsShowFailure(
                    placementId: String,
                    error: UnityAds.UnityAdsShowError,
                    message: String
                ) {
                    val rawError = "UnityAdsShowError: $error - $message"
                    Log.e(TAG, "Rewarded show failure: $rawError")
                    _rawStatusMessage.value = rawError
                    _userEvents.tryEmit(UserUiEvent.ShowSnackbar(rawError))
                    loadRewardedAd()
                }

                override fun onUnityAdsShowStart(placementId: String) {
                    Log.d(TAG, "Rewarded show start: $placementId")
                    _rawStatusMessage.value = "Rewarded ad impression started"
                }

                override fun onUnityAdsShowClick(placementId: String) {
                    Log.d(TAG, "Rewarded clicked: $placementId")
                    managerScope.launch {
                        repository.recordAdClick()
                    }
                }

                override fun onUnityAdsShowComplete(
                    placementId: String,
                    state: UnityAds.UnityAdsShowCompletionState
                ) {
                    Log.d(TAG, "Rewarded show complete: $placementId with state $state")
                    if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                        managerScope.launch {
                            repository.addRewardCoins(Constants.REWARD_COINS_PER_AD)
                            _userEvents.tryEmit(UserUiEvent.RewardedSuccess(Constants.REWARD_COINS_PER_AD))
                            _userEvents.tryEmit(UserUiEvent.ShowSnackbar("🎉 Congratulations! +${Constants.REWARD_COINS_PER_AD} Coins Added!"))
                        }
                    } else {
                        managerScope.launch {
                            repository.recordAdSkipped()
                            _userEvents.tryEmit(UserUiEvent.ShowSnackbar("Ad was skipped. No coins awarded."))
                        }
                    }
                    _rawStatusMessage.value = "Rewarded ad playback finished ($state)"
                    loadRewardedAd()
                }
            }
        )
    }

    private fun showInterstitialAd(activity: Activity) {
        _interstitialState.value = AdAvailability.NOT_LOADED

        UnityAds.show(
            activity,
            Constants.INTERSTITIAL_PLACEMENT_ID,
            UnityAdsShowOptions(),
            object : IUnityAdsShowListener {
                override fun onUnityAdsShowFailure(
                    placementId: String,
                    error: UnityAds.UnityAdsShowError,
                    message: String
                ) {
                    val rawError = "UnityAdsShowError: $error - $message"
                    Log.e(TAG, "Interstitial show failure: $rawError")
                    _rawStatusMessage.value = rawError
                    _userEvents.tryEmit(UserUiEvent.ShowSnackbar(rawError))
                    loadInterstitialAd()
                }

                override fun onUnityAdsShowStart(placementId: String) {
                    Log.d(TAG, "Interstitial show start: $placementId")
                    _rawStatusMessage.value = "Interstitial ad impression started"
                }

                override fun onUnityAdsShowClick(placementId: String) {
                    Log.d(TAG, "Interstitial clicked: $placementId")
                    managerScope.launch {
                        repository.recordAdClick()
                    }
                }

                override fun onUnityAdsShowComplete(
                    placementId: String,
                    state: UnityAds.UnityAdsShowCompletionState
                ) {
                    Log.d(TAG, "Interstitial show complete: $placementId with state $state")
                    if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                        managerScope.launch {
                            repository.recordInterstitialShown()
                        }
                    } else {
                        managerScope.launch {
                            repository.recordAdSkipped()
                        }
                    }
                    _rawStatusMessage.value = "Interstitial ad closed"
                    loadInterstitialAd()
                }
            }
        )
    }
}