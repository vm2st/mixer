package com.vm2st.mixer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vm2st.mixer.ui.screens.EffectsScreen
import com.vm2st.mixer.ui.screens.MixerScreen
import com.vm2st.mixer.ui.theme.VMixerTheme
import kotlinx.coroutines.launch

@Composable
fun VMixerApp(viewModel: VmixerViewModel = viewModel()) {
    VMixerTheme(mode = viewModel.theme) {
        val snackbarHostState = remember { SnackbarHostState() }
        val pagerState = rememberPagerState(pageCount = { 2 })
        val scope = rememberCoroutineScope()

        LaunchedEffect(viewModel.message) {
            val text = viewModel.message
            if (text != null) {
                snackbarHostState.showSnackbar(
                    message = text,
                    withDismissAction = true,
                    duration = SnackbarDuration.Short
                )
                viewModel.clearMessage()
            }
        }

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.onAppResumed()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> MixerScreen(
                            viewModel = viewModel,
                            onOpenEffects = {
                                scope.launch { pagerState.animateScrollToPage(1) }
                            }
                        )

                        else -> EffectsScreen(
                            viewModel = viewModel,
                            onBackToMixer = {
                                scope.launch { pagerState.animateScrollToPage(0) }
                            }
                        )
                    }
                }
            }
        }
    }
}
