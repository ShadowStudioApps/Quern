package com.quern.app.debug

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.quern.app.QuernApplication
import com.quern.app.R
import com.quern.app.domain.model.AppLanguage
import com.quern.app.domain.model.AppSettings
import com.quern.app.domain.model.MediaItem
import com.quern.app.domain.model.MediaType
import com.quern.app.domain.model.SourceOrigin
import com.quern.app.domain.model.StreamSource
import com.quern.app.player.PlaybackRequest
import com.quern.app.player.QuernPlayerScreen
import com.quern.app.ui.QuernViewModel
import com.quern.app.ui.theme.QuernTheme

/** Exercises production failover using an emulator-local synthetic clip provisioned by the test. */
class FailoverFixtureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as QuernApplication).container
        val viewModel = ViewModelProvider(
            this,
            QuernViewModel.Factory(
                container.repository, container.settingsStore, container.cloudSyncManager,
                container.quernAccount, container.syncEngine, container.updateManager, applicationContext
            )
        )[QuernViewModel::class.java]
        val title = MediaItem(id = "debug-failover", title = "Failover fixture", type = MediaType.MOVIE)
        val failed = StreamSource(
            id = "failed", title = "Unavailable", url = getString(R.string.debug_failover_url),
            providerName = "Fixture", origin = SourceOrigin.DIRECT
        )
        val fallback = StreamSource(
            id = "fallback", title = "Local generated clip",
            url = "http://127.0.0.1:18765/quern-failover-fixture.mp4",
            providerName = "Fixture", origin = SourceOrigin.DIRECT
        )
        viewModel.startDebugFailoverFixture(
            PlaybackRequest(
                contentId = title.id, title = title.title, stream = failed,
                startPositionMs = 12_000L, sourceMemoryTitle = title,
                availableSources = listOf(failed, fallback), failoverQueue = listOf(fallback)
            )
        )
        var firstPlayingSample = true
        setContent {
            val request by viewModel.activePlayback.collectAsState()
            QuernTheme(language = AppLanguage.ENGLISH) {
                request?.let {
                    QuernPlayerScreen(
                        request = it, settings = AppSettings(), okHttpClient = container.httpClient,
                        onProgressUpdate = { position, _, playing ->
                            if (playing && firstPlayingSample) {
                                firstPlayingSample = false
                                Log.i(TAG, "First playing position: ${position}ms")
                            }
                        },
                        onActiveStreamChanged = { stream ->
                            viewModel.onPlayerActiveStreamChanged(stream)
                            Log.i(TAG, "Switched to fixture source: ${stream.id}")
                        },
                        onRecordSourceResult = { stream, succeeded ->
                            viewModel.recordPlaybackSourceResult(stream, succeeded)
                            Log.i(TAG, "Fixture result: ${stream.id}, success=$succeeded")
                        },
                        onBack = { finish() }
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "QuernFailoverFixture"
    }
}
