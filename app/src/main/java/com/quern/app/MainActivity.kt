package com.quern.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModelProvider
import com.quern.app.ui.QuernApp
import com.quern.app.ui.QuernViewModel

class MainActivity : ComponentActivity() {

    private val pendingAddonUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        val container = (application as QuernApplication).container
        val viewModel = ViewModelProvider(
            this,
            QuernViewModel.Factory(
                container.repository,
                container.settingsStore,
                container.cloudSyncManager,
                container.quernAccount,
                container.syncEngine,
                container.updateManager
            )
        )[QuernViewModel::class.java]

        setContent {
            QuernApp(
                viewModel = viewModel,
                okHttpClient = container.httpClient,
                pendingDeepLinkAddonUrl = pendingAddonUrl.value,
                onDeepLinkConsumed = { pendingAddonUrl.value = null }
            )
        }
    }

    override fun onStart() {
        super.onStart()
        val container = (application as QuernApplication).container
        // Pull account changes made on other devices or the website while the app was in the background.
        container.syncEngine.onForeground()
        // Check GitHub Releases at most once every 12 hours (silent on rate limits / offline).
        container.updateManager.checkIfDue(container.settingsStore.settings.value.updateChannel)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.dataString ?: return
        if (data.startsWith("stremio://", ignoreCase = true)) {
            pendingAddonUrl.value = data
        }
    }
}
