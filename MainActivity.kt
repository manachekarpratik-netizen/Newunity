package com.aizoid.unityadsdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aizoid.unityadsdemo.data.UserUiEvent
import com.aizoid.unityadsdemo.ui.components.ActionButtonsCard
import com.aizoid.unityadsdemo.ui.components.AdPerformanceCard
import com.aizoid.unityadsdemo.ui.components.AdsEngineStatusCard
import com.aizoid.unityadsdemo.ui.components.CoinBalanceCard
import com.aizoid.unityadsdemo.ui.components.CoinCelebrationEffect
import com.aizoid.unityadsdemo.ui.components.DeviceSessionCard
import com.aizoid.unityadsdemo.ui.components.HeaderCard
import com.aizoid.unityadsdemo.ui.components.NetworkLocationCard
import com.aizoid.unityadsdemo.ui.components.ResetConfirmDialog
import com.aizoid.unityadsdemo.ui.theme.DarkNavy
import com.aizoid.unityadsdemo.ui.theme.DeepPurple
import com.aizoid.unityadsdemo.ui.theme.EmeraldSuccess
import com.aizoid.unityadsdemo.ui.theme.UnityAdsDemoTheme
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Unity Ads SDK on activity launch
        viewModel.initializeAds()

        setContent {
            UnityAdsDemoTheme {
                MainScreen(
                    viewModel = viewModel,
                    activity = this
                )
            }
        }
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    activity: ComponentActivity
) {
    val stats by viewModel.stats.collectAsState()
    val sdkStatus by viewModel.sdkStatus.collectAsState()
    val rewardedState by viewModel.rewardedState.collectAsState()
    val interstitialState by viewModel.interstitialState.collectAsState()
    val rawStatusMessage by viewModel.rawStatusMessage.collectAsState()
    val isRewardedPending by viewModel.isRewardedPending.collectAsState()
    val isInterstitialPending by viewModel.isInterstitialPending.collectAsState()
    val locationState by viewModel.locationState.collectAsState()
    val sessionSeconds by viewModel.sessionDurationSeconds.collectAsState()
    val isResetDialogOpen by viewModel.isResetDialogOpen.collectAsState()
    val celebrationTrigger by viewModel.celebrationEffect.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        viewModel.userEvents.collectLatest { event ->
            when (event) {
                is UserUiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is UserUiEvent.RewardedSuccess -> {}
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DeepPurple,
                        Color(0xFF0F0B26),
                        DarkNavy
                    )
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            modifier = Modifier.fillMaxSize()
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HeaderCard()
                    CoinBalanceCard(coins = stats.coins)
                    AdsEngineStatusCard(
                        sdkStatus = sdkStatus,
                        rewardedState = rewardedState,
                        interstitialState = interstitialState,
                        rawStatusMessage = rawStatusMessage,
                        onReloadAds = { viewModel.reloadAds() }
                    )
                    ActionButtonsCard(
                        isRewardedPending = isRewardedPending,
                        isInterstitialPending = isInterstitialPending,
                        onRewardedClick = { viewModel.onRewardedClicked(activity) },
                        onInterstitialClick = { viewModel.onInterstitialClicked(activity) }
                    )
                    NetworkLocationCard(
                        locationState = locationState,
                        onRefresh = { viewModel.fetchLocation() }
                    )
                    AdPerformanceCard(
                        stats = stats,
                        onResetStats = { viewModel.openResetDialog() }
                    )
                    DeviceSessionCard(
                        sessionSeconds = sessionSeconds,
                        sdkStatus = sdkStatus
                    )
                    FooterInfo()
                    Spacer(modifier = Modifier.height(20.dp))
                }

                CoinCelebrationEffect(trigger = celebrationTrigger)
            }
        }

        ResetConfirmDialog(
            isOpen = isResetDialogOpen,
            onDismiss = { viewModel.closeResetDialog() },
            onConfirm = { viewModel.confirmResetStats() }
        )
    }
}

@Composable
private fun FooterInfo() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(EmeraldSuccess.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (!Constants.TEST_MODE) "LIVE PRODUCTION" else "TEST MODE",
                    color = EmeraldSuccess,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "•",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.sp
            )
            Text(
                text = "Unity Ads SDK 4.21.0",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Text(
            text = Constants.PACKAGE_NAME,
            color = Color.White.copy(alpha = 0.35f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}