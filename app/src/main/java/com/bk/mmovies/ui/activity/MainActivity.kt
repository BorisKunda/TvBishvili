package com.bk.mmovies.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.rememberNavController
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.bk.mmovies.R
import com.bk.mmovies.connectivity.InternetMonitor
import com.bk.mmovies.connectivity.openDeviceInternetSettings
import com.bk.mmovies.data.source.remote.keyrefresh.ApiKeyRejectionEvents
import com.bk.mmovies.ui.component.NoInternetView
import com.bk.mmovies.ui.navigation.AppDestination
import com.bk.mmovies.ui.navigation.AppNavigation
import com.bk.mmovies.ui.theme.MMoviesTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

private val OFFLINE_SCREEN_APPEAR_DELAY = 1000.milliseconds
private const val TABLET_MIN_SMALLEST_WIDTH_DP = 600

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var internetMonitor: InternetMonitor

    @Inject
    lateinit var apiKeyRejectionEvents: ApiKeyRejectionEvents

    private var isFoldableDevice by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@MainActivity)
                        .windowLayoutInfo(this@MainActivity)
                        .collect { layoutInfo ->
                            isFoldableDevice = layoutInfo.displayFeatures.any { it is FoldingFeature }
                        }
            }
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            val navController = rememberNavController()
            val context = LocalContext.current
            val isInternetAvailable by internetMonitor.isInternetAvailable.collectAsStateWithLifecycle()
            var isOfflineScreenVisible by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                apiKeyRejectionEvents.rejections.collect {
                    val currentDestination = navController.currentDestination
                    val isOnSplash = currentDestination?.hasRoute<AppDestination.SplashDestination>() == true
                    if (currentDestination != null && !isOnSplash) {
                        navController.navigate(AppDestination.SplashDestination) {
                            popUpTo(navController.graph.id) {
                                inclusive = true
                            }
                        }
                    }
                }
            }

            LaunchedEffect(isInternetAvailable) {
                if (isInternetAvailable) {
                    isOfflineScreenVisible = false
                } else {
                    delay(OFFLINE_SCREEN_APPEAR_DELAY)
                    isOfflineScreenVisible = true
                }
            }

            val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= TABLET_MIN_SMALLEST_WIDTH_DP

            MMoviesTheme {
                if (isTablet || isFoldableDevice) {
                    AlertDialog(
                            onDismissRequest = {},
                            title = { Text(stringResource(R.string.unsupported_device_title)) },
                            text = { Text(stringResource(R.string.unsupported_device_message)) },
                            confirmButton = {
                                TextButton(onClick = { finish() }) {
                                    Text(stringResource(R.string.unsupported_device_close_action))
                                }
                            }
                               )
                }
                Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = MaterialTheme.colorScheme.background
                        ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        AppNavigation(
                                innerPadding,
                                navController
                                     ) {
                            moveTaskToBack(true)
                        }
                        if (isOfflineScreenVisible) {
                            Box(
                                    modifier = Modifier
                                            .fillMaxSize()
                                            .padding(innerPadding)
                                            .background(MaterialTheme.colorScheme.background)
                                            .pointerInput(Unit) {}
                               ) {
                                NoInternetView(
                                        onSettingsButtonClicked = { openDeviceInternetSettings(context) }
                                              )
                            }
                        }
                    }
                }
            }
        }
    }
}
