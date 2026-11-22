package com.quern.app.player

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.util.TypedValue
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.quern.app.domain.model.AppLanguage
import com.quern.app.domain.model.AppSettings
import com.quern.app.domain.model.DefaultAspectMode
import com.quern.app.domain.model.MediaType
import com.quern.app.domain.model.PlayerEngine
import com.quern.app.domain.model.StreamSource
import com.quern.app.domain.model.SubtitleLanguage
import com.quern.app.domain.model.SubtitleTrack
import com.quern.app.ui.components.QuernBrandMark
import com.quern.app.ui.components.tvClickable
import com.quern.app.ui.theme.LocalQuernAccent
import com.quern.app.ui.theme.LocalQuernPalette
import com.quern.app.ui.theme.LumaCyan
import com.quern.app.ui.theme.LumaSurfaceElevated
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class PlaybackRequest(
    val contentId: String,
    val title: String,
    val subtitle: String? = null,
    val mediaType: MediaType = MediaType.MOVIE,
    val imdbId: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val episodeId: String? = null,
    val nextEpisodeTitle: String? = null,
    val stream: StreamSource,
    val externalSubtitles: List<SubtitleTrack> = emptyList(),
    val startPositionMs: Long = 0L,
    val introStartMs: Long? = null,
    val introEndMs: Long? = null,
    val outroStartMs: Long? = null,
    val outroEndMs: Long? = null
)

private data class TrackOption(
    val label: String,
    val group: Tracks.Group?,
    val trackIndex: Int,
    val selected: Boolean
)

@UnstableApi
private class OffsetSubtitleParserFactory(
    private val offsetMsRef: AtomicLong
) : SubtitleParser.Factory {
    private val delegate = DefaultSubtitleParserFactory()

    override fun supportsFormat(format: Format): Boolean = delegate.supportsFormat(format)

    override fun getCueReplacementBehavior(format: Format): Int = delegate.getCueReplacementBehavior(format)

    override fun create(format: Format): SubtitleParser {
        val inner = delegate.create(format)
        return object : SubtitleParser {
            override fun parse(
                data: ByteArray,
                offset: Int,
                length: Int,
                outputOptions: SubtitleParser.OutputOptions,
                output: Consumer<CuesWithTiming>
            ) {
                val shiftUs = offsetMsRef.get() * 1000L
                inner.parse(data, offset, length, outputOptions) { cues ->
                    val shiftedStart = if (cues.startTimeUs == C.TIME_UNSET) {
                        C.TIME_UNSET
                    } else {
                        (cues.startTimeUs + shiftUs).coerceAtLeast(0L)
                    }
                    output.accept(CuesWithTiming(cues.cues, shiftedStart, cues.durationUs))
                }
            }

            override fun reset() = inner.reset()

            override fun getCueReplacementBehavior(): Int = inner.cueReplacementBehavior
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun QuernPlayerScreen(
    request: PlaybackRequest,
    settings: AppSettings,
    okHttpClient: OkHttpClient,
    onProgressUpdate: (positionMs: Long, durationMs: Long, isPlaying: Boolean) -> Unit,
    onPlayNextEpisode: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val accent = LocalQuernAccent.current
    val palette = LocalQuernPalette.current
    val isAr = settings.effectiveLanguage == AppLanguage.ARABIC
    var engineNotice by remember { mutableStateOf<String?>(null) }
    var seekToast by remember { mutableStateOf<String?>(null) }

    // Respect user's Player Engine preference: External Player (incl. installed mpv/VLC/MX/Just) vs Media3 ExoPlayer
    LaunchedEffect(request.stream.url, settings.playerEngine, settings.preferExternalPlayer, settings.preferredExternalPlayerPackage) {
        val url = request.stream.url
        if (url.isNullOrBlank()) return@LaunchedEffect
        val prefSubIso = settings.subtitleLanguage.isoCode.takeIf { it.isNotBlank() }

        if (settings.playerEngine == PlayerEngine.EXTERNAL || settings.preferExternalPlayer) {
            when (
                val res = ExternalPlayerHelper.launchExternalPlayer(
                    context = context,
                    request = request,
                    targetPackage = settings.preferredExternalPlayerPackage.takeIf { it.isNotBlank() },
                    preferredSubLangIso = prefSubIso
                )
            ) {
                ExternalLaunchResult.Launched -> onBack()
                is ExternalLaunchResult.TargetNotInstalled -> {
                    engineNotice = if (isAr) {
                        "تطبيق ${res.displayName} غير مثبت — يتم التشغيل عبر Media3 ExoPlayer"
                    } else {
                        "${res.displayName} is not installed — playing via Media3 ExoPlayer"
                    }
                }
                ExternalLaunchResult.NoExternalPlayerAvailable,
                ExternalLaunchResult.Failed -> {
                    engineNotice = if (isAr) {
                        "لا يوجد مشغل خارجي متاح — يتم التشغيل عبر Media3 ExoPlayer"
                    } else {
                        "No external video player available — playing via Media3 ExoPlayer"
                    }
                }
            }
        }
    }

    LaunchedEffect(engineNotice) {
        if (engineNotice != null) {
            delay(4200L)
            engineNotice = null
        }
    }

    LaunchedEffect(seekToast) {
        if (seekToast != null) {
            delay(1100L)
            seekToast = null
        }
    }

    var controlsVisible by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var hasRenderedFirstFrame by remember(request.stream.url) { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentPosMs by remember { mutableLongStateOf(request.startPositionMs) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember(settings.defaultPlaybackSpeed) { mutableFloatStateOf(settings.defaultPlaybackSpeed) }
    var resizeMode by remember(settings.defaultAspectMode) {
        mutableIntStateOf(
            when (settings.defaultAspectMode) {
                DefaultAspectMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                DefaultAspectMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                DefaultAspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            }
        )
    }

    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showEngineDialog by remember { mutableStateOf(false) }
    var showSubtitleSyncBar by remember { mutableStateOf(false) }

    // Subtitle Timing Sync Offset (in ms: negative = earlier, positive = later)
    val subtitleOffsetRef = remember { AtomicLong(0L) }
    var subtitleOffsetMs by remember(request.contentId) { mutableLongStateOf(0L) }
    var appliedSubtitleOffsetMs by remember(request.contentId) { mutableLongStateOf(0L) }

    var introDismissed by remember(request.contentId) { mutableStateOf(false) }
    var nextEpisodeCancelled by remember(request.contentId) { mutableStateOf(false) }
    var nextEpisodeTriggered by remember(request.contentId) { mutableStateOf(false) }
    var countdownRemaining by remember(request.contentId, settings.nextEpisodeCountdownSec) {
        mutableIntStateOf(settings.nextEpisodeCountdownSec)
    }

    val anyModalOpen = showAudioDialog || showSubtitleDialog || showSpeedDialog || showEngineDialog || showSubtitleSyncBar

    BackHandler {
        when {
            showSubtitleSyncBar -> showSubtitleSyncBar = false
            showEngineDialog -> showEngineDialog = false
            showAudioDialog -> showAudioDialog = false
            showSubtitleDialog -> showSubtitleDialog = false
            showSpeedDialog -> showSpeedDialog = false
            controlsVisible -> controlsVisible = false
            else -> onBack()
        }
    }

    var detectedIntroStartMs by remember(request.contentId) { mutableStateOf<Long?>(null) }
    var detectedIntroEndMs by remember(request.contentId) { mutableStateOf<Long?>(null) }
    var detectedOutroStartMs by remember(request.contentId) { mutableStateOf<Long?>(null) }
    var detectedOutroEndMs by remember(request.contentId) { mutableStateOf<Long?>(null) }

    val effectiveIntroStartMs = request.introStartMs ?: detectedIntroStartMs
    val effectiveIntroEndMs = request.introEndMs ?: detectedIntroEndMs
    val hasRealIntroMarkers = effectiveIntroStartMs != null &&
        effectiveIntroEndMs != null &&
        effectiveIntroEndMs > effectiveIntroStartMs
    val showSkipIntro = settings.skipIntroEnabled &&
        hasRealIntroMarkers &&
        !introDismissed &&
        currentPosMs in effectiveIntroStartMs!!..effectiveIntroEndMs!! &&
        !showSubtitleSyncBar
    val skipIntroProgress = if (hasRealIntroMarkers) {
        ((currentPosMs - effectiveIntroStartMs!!).toFloat() /
            (effectiveIntroEndMs!! - effectiveIntroStartMs).coerceAtLeast(1L).toFloat())
            .coerceIn(0.06f, 1f)
    } else {
        0f
    }

    val effectiveOutroStartMs = request.outroStartMs ?: detectedOutroStartMs
    val effectiveOutroEndMs = request.outroEndMs ?: detectedOutroEndMs
    val hasRealOutroMarkers = effectiveOutroStartMs != null &&
        (effectiveOutroEndMs == null || effectiveOutroEndMs > effectiveOutroStartMs)
    val inOutroWindow = hasRealOutroMarkers &&
        currentPosMs >= effectiveOutroStartMs!! &&
        (effectiveOutroEndMs == null || currentPosMs <= effectiveOutroEndMs)
    val showNextEpisodeCard = !request.nextEpisodeTitle.isNullOrBlank() &&
        settings.skipOutroEnabled &&
        inOutroWindow && !nextEpisodeCancelled && !showSubtitleSyncBar

    LaunchedEffect(showNextEpisodeCard, settings.autoPlayNext) {
        if (showNextEpisodeCard && settings.autoPlayNext && !nextEpisodeTriggered) {
            countdownRemaining = settings.nextEpisodeCountdownSec.coerceAtLeast(3)
            while (countdownRemaining > 0 && showNextEpisodeCard && !nextEpisodeCancelled) {
                delay(1000L)
                countdownRemaining--
            }
            if (countdownRemaining <= 0 && !nextEpisodeCancelled && !nextEpisodeTriggered) {
                nextEpisodeTriggered = true
                onPlayNextEpisode()
            }
        }
    }

    val rootFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val skipIntroFocusRequester = remember { FocusRequester() }
    val syncBarFocusRequester = remember { FocusRequester() }

    val buildExoMediaItem = remember(request.stream.url, request.stream.subtitles, request.externalSubtitles) {
        {
            val subConfigs = (request.stream.subtitles + request.externalSubtitles)
                .distinctBy { it.url }
                .map { sub ->
                    ExoMediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                        .setMimeType(sub.mimeType)
                        .setLanguage(sub.language)
                        .setLabel(sub.label)
                        .build()
                }
            ExoMediaItem.Builder()
                .setUri(request.stream.url.orEmpty())
                .setSubtitleConfigurations(subConfigs)
                .build()
        }
    }

    val exoPlayer = remember(request.stream.url, settings.subtitleLanguage, settings.audioLanguage, settings.bufferProfile) {
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient).apply {
            if (request.stream.headers.isNotEmpty()) {
                setDefaultRequestProperties(request.stream.headers)
            }
        }
        val subtitleParserFactory = OffsetSubtitleParserFactory(subtitleOffsetRef)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            .setSubtitleParserFactory(subtitleParserFactory)

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                settings.bufferProfile.minBufferMs,
                settings.bufferProfile.maxBufferMs,
                settings.bufferProfile.playbackBufferMs,
                settings.bufferProfile.rebufferMs
            )
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .build()
            .apply {
                if (settings.defaultPlaybackSpeed != 1.0f) {
                    setPlaybackParameters(PlaybackParameters(settings.defaultPlaybackSpeed))
                }
                val trackParams = trackSelectionParameters.buildUpon().apply {
                    if (settings.subtitleLanguage == SubtitleLanguage.OFF) {
                        setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    } else {
                        setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        val code = settings.subtitleLanguage.isoCode
                        if (code == "ar") {
                            setPreferredTextLanguages("ar", "ara", "arb")
                        } else if (code.isNotBlank()) {
                            setPreferredTextLanguage(code)
                        }
                    }
                    if (settings.audioLanguage.isoCode.isNotBlank()) {
                        setPreferredAudioLanguage(settings.audioLanguage.isoCode)
                    }
                }.build()
                trackSelectionParameters = trackParams

                setMediaItem(buildExoMediaItem())
                if (request.startPositionMs > 5_000L) {
                    seekTo(request.startPositionMs)
                }
                playWhenReady = true
                prepare()
            }
    }

    // Debounced live application of Subtitle Timing Shift (shifts both external & embedded subtitles!)
    LaunchedEffect(subtitleOffsetMs) {
        if (subtitleOffsetMs == appliedSubtitleOffsetMs) return@LaunchedEffect
        delay(350L)
        subtitleOffsetRef.set(subtitleOffsetMs)
        appliedSubtitleOffsetMs = subtitleOffsetMs
        val resumePos = exoPlayer.currentPosition.coerceAtLeast(0L)
        val wasPlaying = exoPlayer.isPlaying
        val savedParams = exoPlayer.trackSelectionParameters
        exoPlayer.setMediaItem(buildExoMediaItem(), resumePos)
        exoPlayer.trackSelectionParameters = savedParams
        exoPlayer.prepare()
        exoPlayer.playWhenReady = wasPlaying
    }

    // Dedicated LoudnessEnhancer lifecycle: released on every audioSessionId change AND when player exits
    DisposableEffect(exoPlayer, settings.audioVolumeBoostEnabled) {
        var loudnessEnhancer: LoudnessEnhancer? = null

        fun releaseEnhancer() {
            runCatching {
                loudnessEnhancer?.enabled = false
                loudnessEnhancer?.release()
            }
            loudnessEnhancer = null
        }

        fun applyVolumeBoost(sessionId: Int) {
            releaseEnhancer()
            if (!settings.audioVolumeBoostEnabled || sessionId <= 0) return
            runCatching {
                val enhancer = LoudnessEnhancer(sessionId)
                enhancer.setTargetGain(600) // +6 dB volume boost
                enhancer.enabled = true
                loudnessEnhancer = enhancer
            }
        }

        applyVolumeBoost(exoPlayer.audioSessionId)

        val audioSessionListener = object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                applyVolumeBoost(audioSessionId)
            }
        }
        exoPlayer.addListener(audioSessionListener)
        onDispose {
            exoPlayer.removeListener(audioSessionListener)
            releaseEnhancer()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                onProgressUpdate(exoPlayer.currentPosition, exoPlayer.duration.coerceAtLeast(0L), playing)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                } else if (playbackState == Player.STATE_ENDED &&
                    settings.autoPlayNext &&
                    !request.nextEpisodeTitle.isNullOrBlank() &&
                    !nextEpisodeCancelled &&
                    !nextEpisodeTriggered
                ) {
                    nextEpisodeTriggered = true
                    onPlayNextEpisode()
                }
            }

            override fun onRenderedFirstFrame() {
                hasRenderedFirstFrame = true
                isBuffering = false
            }

            override fun onMetadata(metadata: Metadata) {
                for (i in 0 until metadata.length()) {
                    val entry = metadata.get(i)
                    if (entry is ChapterFrame) {
                        var title = entry.chapterId
                        for (j in 0 until entry.subFrameCount) {
                            val sub = entry.getSubFrame(j)
                            if (sub is TextInformationFrame && sub.values.isNotEmpty()) {
                                title = sub.values.first()
                            }
                        }
                        val lower = title.lowercase()
                        if ((lower.contains("intro") || lower.contains("opening")) &&
                            entry.endTimeMs > entry.startTimeMs &&
                            entry.startTimeMs >= 0
                        ) {
                            detectedIntroStartMs = entry.startTimeMs.toLong()
                            detectedIntroEndMs = entry.endTimeMs.toLong()
                        } else if ((lower.contains("outro") || lower.contains("credits") || lower.contains("ending")) &&
                            entry.endTimeMs > entry.startTimeMs &&
                            entry.startTimeMs >= 0
                        ) {
                            detectedOutroStartMs = entry.startTimeMs.toLong()
                            detectedOutroEndMs = entry.endTimeMs.toLong()
                        }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMessage = error.localizedMessage ?: "Playback failed (${error.errorCodeName})"
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            onProgressUpdate(exoPlayer.currentPosition, exoPlayer.duration.coerceAtLeast(0L), false)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        var tick = 0
        while (true) {
            currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            tick++
            if (tick % 10 == 0 && durationMs > 0L) {
                onProgressUpdate(currentPosMs, durationMs, exoPlayer.isPlaying)
            }
            delay(1000L)
        }
    }

    // Auto-hide controls after inactivity (pause timer while dialogs or subtitle sync bar are open)
    LaunchedEffect(controlsVisible, isPlaying, anyModalOpen) {
        if (controlsVisible && isPlaying && !anyModalOpen) {
            delay(6000L)
            controlsVisible = false
        }
    }

    // Smart TV Remote Focus Management:
    // - When Subtitle Sync Bar opens -> focus sync controls
    // - When Controls HUD opens -> focus Play/Pause so user can navigate ALL HUD buttons (Subtitles, Sync, Audio, Skip, etc.)
    // - When Controls HUD hides -> focus Skip Intro if visible, else root box
    LaunchedEffect(controlsVisible, showSubtitleSyncBar, showSkipIntro) {
        delay(60L)
        when {
            showSubtitleSyncBar -> runCatching { syncBarFocusRequester.requestFocus() }
            controlsVisible -> runCatching { playPauseFocusRequester.requestFocus() }
            showSkipIntro -> runCatching { skipIntroFocusRequester.requestFocus() }
            else -> runCatching { rootFocusRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val code = keyEvent.nativeKeyEvent.keyCode

                // Dedicated media keys ALWAYS work
                when (code) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        controlsVisible = true
                        return@onPreviewKeyEvent true
                    }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        exoPlayer.seekTo((exoPlayer.currentPosition + 10_000L).coerceAtMost(durationMs.coerceAtLeast(0L)))
                        seekToast = "+10s"
                        return@onPreviewKeyEvent true
                    }
                    KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        exoPlayer.seekTo((exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L))
                        seekToast = "-10s"
                        return@onPreviewKeyEvent true
                    }
                }

                // When HUD controls or dialogs or subtitle sync bar are visible:
                // DO NOT intercept DPAD_LEFT / RIGHT / UP / DOWN / CENTER!
                // Let the TV remote navigate & click every single HUD button freely!
                if (controlsVisible || anyModalOpen) {
                    return@onPreviewKeyEvent false
                }

                // When HUD is hidden (pure watching mode):
                when (code) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        exoPlayer.seekTo((exoPlayer.currentPosition + 10_000L).coerceAtMost(durationMs.coerceAtLeast(0L)))
                        seekToast = "+10s"
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        exoPlayer.seekTo((exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L))
                        seekToast = "-10s"
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                        controlsVisible = true
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        // If Skip Intro pill is visible & focused, let its onClick handle OK!
                        if (showSkipIntro) {
                            false
                        } else {
                            controlsVisible = true
                            true
                        }
                    }
                    else -> false
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (showSubtitleSyncBar) {
                    showSubtitleSyncBar = false
                } else {
                    controlsVisible = !controlsVisible
                }
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    // CRITICAL: Disable Media3's built-in boring spinner so ONLY our animated Quern logo shows!
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    this.resizeMode = resizeMode
                    subtitleView?.apply {
                        setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, settings.subtitleSize.spValue)
                        setStyle(
                            CaptionStyleCompat(
                                settings.subtitleColorHex.toInt(),
                                AndroidColor.argb(140, 0, 0, 0),
                                AndroidColor.TRANSPARENT,
                                CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                                AndroidColor.BLACK,
                                Typeface.DEFAULT_BOLD
                            )
                        )
                    }
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                view.resizeMode = resizeMode
            }
        )

        // Full-Bleed Cinema Backdrop Starting Screen (Screenshot #2) or Animated Logo on re-buffer
        if ((isBuffering || !hasRenderedFirstFrame) && errorMessage == null) {
            QuernPlayerLoadingOverlay(
                title = request.title,
                backdropUrl = request.backdropUrl ?: request.posterUrl,
                showFullBackdrop = !hasRenderedFirstFrame,
                subtitle = if (isAr) "جاري بدء التشغيل..." else "Starting stream...",
                modifier = Modifier.fillMaxSize()
            )
        }

        // Quick Seek Feedback Pill (+10s / -10s)
        seekToast?.let { txt ->
            Surface(
                color = Color(0xCC090D16),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.5.dp, palette.focusRing),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp)
            ) {
                Text(
                    text = txt,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        }

        // Engine Fallback Notice Pill
        engineNotice?.let { notice ->
            Surface(
                color = Color(0xE6101520),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, palette.focusRing.copy(alpha = 0.65f)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 74.dp)
            ) {
                Text(
                    text = notice,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Reference-Style Sleek White "⏭ Skip Intro" Pill at Bottom-Left (100% TV-Remote Focusable & Clickable!)
        AnimatedVisibility(
            visible = showSkipIntro && errorMessage == null,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(140)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 26.dp, bottom = if (controlsVisible) 118.dp else 32.dp)
        ) {
            var skipFocused by remember { mutableStateOf(false) }
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                border = if (skipFocused) BorderStroke(3.dp, palette.focusRing) else null,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .focusRequester(skipIntroFocusRequester)
                    .clip(RoundedCornerShape(14.dp))
                    .tvClickable(
                        onFocusChangedState = { skipFocused = it },
                        onClick = {
                            val markerEnd = effectiveIntroEndMs
                            if (markerEnd != null) {
                                val target = markerEnd.coerceAtMost((durationMs - 2_000L).coerceAtLeast(markerEnd))
                                exoPlayer.seekTo(target)
                                introDismissed = true
                                seekToast = if (isAr) "تم تخطي المقدمة" else "Skipped Intro"
                            }
                        }
                    )
            ) {
                Column(modifier = Modifier.width(172.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Skip Intro",
                            tint = Color(0xFF111827),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isAr) "تخطي المقدمة" else "Skip Intro",
                            color = Color(0xFF111827),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.5.dp)
                            .background(Color(0xFFE5E7EB))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(skipIntroProgress)
                                .height(3.5.dp)
                                .background(Color(0xFF6B7280))
                        )
                    }
                }
            }
        }

        // Skip Outro & Auto-Play Next Episode Countdown Card (TV-Remote Focusable)
        if (showNextEpisodeCard && errorMessage == null) {
            Surface(
                color = Color(0xEB0A0E16),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(2.dp, accent),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 28.dp, bottom = if (controlsVisible) 118.dp else 32.dp)
                    .width(310.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (settings.autoPlayNext) "UP NEXT IN ${countdownRemaining}s" else "UP NEXT",
                        color = LumaCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.4.sp
                    )
                    Text(
                        text = request.nextEpisodeTitle.orEmpty(),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PlayerPillButton(
                            label = if (isAr) "▶ تشغيل الآن" else "▶ Play Now",
                            isPrimary = true,
                            onClick = {
                                if (!nextEpisodeTriggered) {
                                    nextEpisodeTriggered = true
                                    onPlayNextEpisode()
                                }
                            }
                        )
                        PlayerPillButton(
                            label = if (isAr) "إلغاء" else "Cancel",
                            isPrimary = false,
                            onClick = { nextEpisodeCancelled = true }
                        )
                    }
                }
            }
        }

        // Live Non-Blocking Subtitle Timing / Delay Adjustment Bar (watch subtitles sync live!)
        AnimatedVisibility(
            visible = showSubtitleSyncBar,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(120)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            val formattedOffset = String.format(
                Locale.US,
                "%+.2fs",
                subtitleOffsetMs / 1000.0
            )
            Surface(
                color = Color(0xEB0B101A),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.5.dp, palette.focusRing),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (isAr) "مزامنة توقيت الترجمة (Subtitle Sync)" else "Subtitle Timing Sync",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isAr) {
                                    "السالب (−) لتبكير الترجمة • الموجب (+) لتأخير الترجمة"
                                } else {
                                    "Negative (−) shows subtitles earlier • Positive (+) delays subtitles"
                                },
                                color = Color(0xFF94A3B8),
                                fontSize = 11.5.sp
                            )
                        }
                        Surface(
                            color = palette.accent.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, palette.focusRing)
                        ) {
                            Text(
                                text = formattedOffset,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerPillButton(label = "-2.0s", isPrimary = false, onClick = { subtitleOffsetMs = (subtitleOffsetMs - 2000L).coerceIn(-30_000L, 30_000L) })
                        PlayerPillButton(label = "-0.5s", isPrimary = false, onClick = { subtitleOffsetMs = (subtitleOffsetMs - 500L).coerceIn(-30_000L, 30_000L) })
                        PlayerPillButton(
                            label = "-0.25s",
                            isPrimary = false,
                            modifier = Modifier.focusRequester(syncBarFocusRequester),
                            onClick = { subtitleOffsetMs = (subtitleOffsetMs - 250L).coerceIn(-30_000L, 30_000L) }
                        )
                        PlayerPillButton(label = "0.0s", isPrimary = subtitleOffsetMs == 0L, onClick = { subtitleOffsetMs = 0L })
                        PlayerPillButton(label = "+0.25s", isPrimary = false, onClick = { subtitleOffsetMs = (subtitleOffsetMs + 250L).coerceIn(-30_000L, 30_000L) })
                        PlayerPillButton(label = "+0.5s", isPrimary = false, onClick = { subtitleOffsetMs = (subtitleOffsetMs + 500L).coerceIn(-30_000L, 30_000L) })
                        PlayerPillButton(label = "+2.0s", isPrimary = false, onClick = { subtitleOffsetMs = (subtitleOffsetMs + 2000L).coerceIn(-30_000L, 30_000L) })
                        PlayerPillButton(
                            label = if (isAr) "تم" else "Done",
                            isPrimary = true,
                            onClick = { showSubtitleSyncBar = false }
                        )
                    }
                }
            }
        }

        if (errorMessage != null) {
            Surface(
                color = Color(0xF210131A),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.16f)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 36.dp, vertical = 24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isAr) "تعذر تشغيل هذا المصدر" else "Playback Error",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMessage.orEmpty(),
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PlayerPillButton(
                            label = if (isAr) "فتح بمشغل خارجي" else "Open in External Player",
                            isPrimary = true,
                            onClick = {
                                when (
                                    val res = ExternalPlayerHelper.launchExternalPlayer(
                                        context = context,
                                        request = request.copy(startPositionMs = currentPosMs),
                                        targetPackage = settings.preferredExternalPlayerPackage.takeIf { it.isNotBlank() },
                                        preferredSubLangIso = settings.subtitleLanguage.isoCode.takeIf { it.isNotBlank() }
                                    )
                                ) {
                                    ExternalLaunchResult.Launched -> onBack()
                                    is ExternalLaunchResult.TargetNotInstalled -> {
                                        engineNotice = if (isAr) {
                                            "تطبيق ${res.displayName} غير مثبت — يتم التشغيل عبر Media3 ExoPlayer"
                                        } else {
                                            "${res.displayName} is not installed — playing via Media3 ExoPlayer"
                                        }
                                    }
                                    ExternalLaunchResult.NoExternalPlayerAvailable,
                                    ExternalLaunchResult.Failed -> {
                                        engineNotice = if (isAr) {
                                            "لا يوجد مشغل خارجي متاح — يتم التشغيل عبر Media3 ExoPlayer"
                                        } else {
                                            "No external video player available — playing via Media3 ExoPlayer"
                                        }
                                    }
                                }
                            }
                        )
                        PlayerPillButton(
                            label = if (isAr) "الرجوع للمصادر" else "Back to Sources",
                            isPrimary = false,
                            onClick = onBack
                        )
                    }
                }
            }
        }

        // Minimalist Bottom Cinema Control Bar (EXACT match to Screenshot #3!)
        AnimatedVisibility(
            visible = controlsVisible && !showSubtitleSyncBar,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(140)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.25f),
                                0.58f to Color.Transparent,
                                0.80f to Color.Black.copy(alpha = 0.78f),
                                1.0f to Color.Black.copy(alpha = 0.95f)
                            )
                        )
                    )
            ) {
                // Clean Bottom Control Panel (Title + Stream Subtitle + Thin Progress Bar + 7 Circular Icon Buttons)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 26.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Title & Time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = request.title,
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = listOfNotNull(
                                    request.stream.title.takeIf { it.isNotBlank() },
                                    request.stream.providerName.takeIf { it.isNotBlank() }
                                ).joinToString("  "),
                                color = Color(0xFFD0D5DD),
                                fontSize = 12.sp,
                                fontStyle = FontStyle.Italic,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "${formatDuration(currentPosMs)} / ${formatDuration(durationMs)}",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Sleek Thin Rounded Progress Bar
                    val safeDuration = durationMs.coerceAtLeast(1L)
                    Slider(
                        value = (currentPosMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f),
                        onValueChange = { fraction ->
                            val target = (fraction * safeDuration).toLong()
                            currentPosMs = target
                            exoPlayer.seekTo(target)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF3B82F6),
                            activeTrackColor = Color(0xFF3B82F6),
                            inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                            .focusProperties { canFocus = false }
                    )

                    // Bottom-Left Row of 7 Clean Circular Icon Buttons (EXACT match to Screenshot #3!)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. Solid Blue/Accent Play/Pause Circle
                        PlayerCircleIconButton(
                            icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            size = 44.dp,
                            iconSize = 24.dp,
                            isPrimary = true,
                            primaryColorOverride = Color(0xFF2F80ED),
                            modifier = Modifier.focusRequester(playPauseFocusRequester),
                            onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() }
                        )

                        // 2. Aspect Ratio [ ]
                        PlayerMinimalIconButton(
                            icon = Icons.Default.AspectRatio,
                            contentDescription = "Aspect Ratio",
                            onClick = {
                                resizeMode = when (resizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                                seekToast = when (resizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom (Fill Screen)"
                                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch"
                                    else -> "Original Fit"
                                }
                            }
                        )

                        // 3. Playback Speed
                        PlayerMinimalIconButton(
                            icon = Icons.Default.Speed,
                            contentDescription = "Playback Speed",
                            onClick = { showSpeedDialog = true }
                        )

                        // 4. Subtitles (CC)
                        PlayerMinimalIconButton(
                            icon = Icons.Default.ClosedCaption,
                            contentDescription = "Subtitles",
                            onClick = { showSubtitleDialog = true }
                        )

                        // 5. Audio Tracks (Speaker)
                        PlayerMinimalIconButton(
                            icon = Icons.Default.Speaker,
                            contentDescription = "Audio Tracks",
                            onClick = { showAudioDialog = true }
                        )

                        // 6. Cloud / Sources / mpv Switch
                        PlayerMinimalIconButton(
                            icon = Icons.Default.CloudQueue,
                            contentDescription = "Switch Player / Sources",
                            onClick = { showEngineDialog = true }
                        )

                        // 7. Subtitle Sync & Info (i)
                        PlayerMinimalIconButton(
                            icon = Icons.Default.Info,
                            contentDescription = "Subtitle Timing Sync",
                            highlighted = subtitleOffsetMs != 0L,
                            onClick = {
                                controlsVisible = false
                                showSubtitleSyncBar = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showEngineDialog) {
        val installedPlayers = remember(context) { ExternalPlayerHelper.detectInstalledPlayers(context) }
        val prefSubIso = settings.subtitleLanguage.isoCode.takeIf { it.isNotBlank() }
        AlertDialog(
            onDismissRequest = { showEngineDialog = false },
            containerColor = palette.surfaceElevated,
            title = { Text(if (isAr) "المشغل الخارجي والمصادر" else "External Players & Sources", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    installedPlayers.forEachIndexed { idx, player ->
                        PlayerPillButton(
                            label = if (isAr) "تشغيل عبر ${player.displayName}" else "Play in ${player.displayName}",
                            isPrimary = idx == 0,
                            onClick = {
                                showEngineDialog = false
                                exoPlayer.pause()
                                val res = ExternalPlayerHelper.launchExternalPlayer(
                                    context = context,
                                    request = request.copy(startPositionMs = currentPosMs),
                                    targetPackage = player.packageName,
                                    preferredSubLangIso = prefSubIso
                                )
                                if (res != ExternalLaunchResult.Launched) {
                                    engineNotice = if (isAr) {
                                        "تعذر فتح ${player.displayName} — استمرار التشغيل بـ ExoPlayer"
                                    } else {
                                        "Could not open ${player.displayName} — continuing in ExoPlayer"
                                    }
                                    exoPlayer.play()
                                }
                            }
                        )
                    }
                    PlayerPillButton(
                        label = if (isAr) "اختيار مشغل من النظام…" else "Open with System Chooser…",
                        isPrimary = installedPlayers.isEmpty(),
                        onClick = {
                            showEngineDialog = false
                            exoPlayer.pause()
                            val res = ExternalPlayerHelper.launchExternalPlayer(
                                context = context,
                                request = request.copy(startPositionMs = currentPosMs),
                                targetPackage = null,
                                preferredSubLangIso = prefSubIso
                            )
                            if (res != ExternalLaunchResult.Launched) {
                                engineNotice = if (isAr) {
                                    "لا يوجد مشغل فيديو خارجي مثبت على هذا الجهاز"
                                } else {
                                    "No external video player installed on this device"
                                }
                                exoPlayer.play()
                            }
                        }
                    )
                    PlayerPillButton(
                        label = if (isAr) "الرجوع لقائمة المصادر" else "Back to Stream Sources",
                        isPrimary = false,
                        onClick = {
                            showEngineDialog = false
                            onBack()
                        }
                    )
                }
            },
            confirmButton = {
                PlayerPillButton(label = if (isAr) "إغلاق" else "Close", isPrimary = false, onClick = { showEngineDialog = false })
            }
        )
    }

    if (showAudioDialog) {
        val options = remember(exoPlayer.currentTracks) { extractTrackOptions(exoPlayer.currentTracks, C.TRACK_TYPE_AUDIO) }
        TrackPickerDialog(
            title = if (isAr) "مسارات الصوت" else "Audio Tracks",
            options = options,
            onSelect = { opt ->
                val params = exoPlayer.trackSelectionParameters.buildUpon()
                if (opt.group != null) {
                    params.setOverrideForType(TrackSelectionOverride(opt.group.mediaTrackGroup, opt.trackIndex))
                }
                exoPlayer.trackSelectionParameters = params.build()
                showAudioDialog = false
            },
            onDismiss = { showAudioDialog = false }
        )
    }

    if (showSubtitleDialog) {
        val options = remember(exoPlayer.currentTracks) {
            listOf(TrackOption(if (isAr) "إيقاف الترجمة (Off)" else "Off (Disable Subtitles)", null, -1, false)) +
                extractTrackOptions(exoPlayer.currentTracks, C.TRACK_TYPE_TEXT)
        }
        TrackPickerDialog(
            title = if (isAr) "الترجمة (Subtitles)" else "Subtitles",
            options = options,
            extraActionLabel = if (isAr) "تعديل توقيت الترجمة (Sync)" else "Adjust Subtitle Timing (Sync)",
            onExtraAction = {
                showSubtitleDialog = false
                controlsVisible = false
                showSubtitleSyncBar = true
            },
            onSelect = { opt ->
                val params = exoPlayer.trackSelectionParameters.buildUpon()
                if (opt.group == null) {
                    params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                } else {
                    params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    params.setOverrideForType(TrackSelectionOverride(opt.group.mediaTrackGroup, opt.trackIndex))
                }
                exoPlayer.trackSelectionParameters = params.build()
                showSubtitleDialog = false
            },
            onDismiss = { showSubtitleDialog = false }
        )
    }

    if (showSpeedDialog) {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            containerColor = palette.surfaceElevated,
            title = { Text(if (isAr) "سرعة التشغيل" else "Playback Speed", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    speeds.forEach { spd ->
                        val active = spd == playbackSpeed
                        var focused by remember { mutableStateOf(false) }
                        Surface(
                            color = if (focused) palette.accent else if (active) palette.accent.copy(alpha = 0.25f) else palette.surface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(if (focused) 2.dp else 1.dp, if (focused) palette.focusRing else palette.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .tvClickable(
                                    onFocusChangedState = { focused = it },
                                    onClick = {
                                        playbackSpeed = spd
                                        exoPlayer.playbackParameters = PlaybackParameters(spd)
                                        showSpeedDialog = false
                                    }
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${spd}x",
                                    color = if (focused) palette.onAccent else Color.White,
                                    fontWeight = if (active || focused) FontWeight.Bold else FontWeight.Normal
                                )
                                if (active) {
                                    Icon(Icons.Default.Check, null, tint = if (focused) palette.onAccent else LumaCyan, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                PlayerPillButton(label = if (isAr) "إغلاق" else "Close", isPrimary = false, onClick = { showSpeedDialog = false })
            }
        )
    }
}

@Composable
private fun PlayerCircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    size: Dp,
    iconSize: Dp,
    isPrimary: Boolean = false,
    primaryColorOverride: Color? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalQuernPalette.current
    var focused by remember { mutableStateOf(false) }
    val primaryBg = primaryColorOverride ?: palette.accent
    val bg = when {
        focused -> primaryBg
        isPrimary -> primaryBg
        else -> Color.White.copy(alpha = 0.14f)
    }
    val tint = if (primaryColorOverride != null) Color.White else if (focused || isPrimary) palette.onAccent else Color.White

    Surface(
        color = bg,
        shape = CircleShape,
        border = BorderStroke(
            width = if (focused) 2.5.dp else 1.dp,
            color = if (focused) Color.White else Color.Transparent
        ),
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .tvClickable(
                onFocusChangedState = { focused = it },
                onClick = onClick
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
        }
    }
}

@Composable
private fun PlayerMinimalIconButton(
    icon: ImageVector,
    contentDescription: String,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    val palette = LocalQuernPalette.current
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> Color(0xFF2F80ED)
        highlighted -> palette.accent.copy(alpha = 0.35f)
        else -> Color.Transparent
    }

    Surface(
        color = bg,
        shape = CircleShape,
        border = if (focused) BorderStroke(2.dp, Color.White) else null,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .tvClickable(
                onFocusChangedState = { focused = it },
                onClick = onClick
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun PlayerHudActionPill(
    icon: ImageVector,
    label: String,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    val palette = LocalQuernPalette.current
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> palette.accent
        highlighted -> palette.accent.copy(alpha = 0.35f)
        else -> Color.White.copy(alpha = 0.14f)
    }
    val fg = if (focused) palette.onAccent else Color.White

    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(
            width = if (focused) 2.5.dp else 1.dp,
            color = if (focused) palette.focusRing else if (highlighted) palette.focusRing else Color.White.copy(alpha = 0.22f)
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .tvClickable(
                onFocusChangedState = { focused = it },
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(16.dp))
            Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PlayerPillButton(
    label: String,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalQuernPalette.current
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> palette.accent
        isPrimary -> Color.White
        else -> Color.White.copy(alpha = 0.14f)
    }
    val fg = when {
        focused -> palette.onAccent
        isPrimary -> Color.Black
        else -> Color.White
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(
            width = if (focused) 2.5.dp else 1.dp,
            color = if (focused) palette.focusRing else Color.White.copy(alpha = 0.22f)
        ),
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .tvClickable(
                onFocusChangedState = { focused = it },
                onClick = onClick
            )
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun TrackPickerDialog(
    title: String,
    options: List<TrackOption>,
    extraActionLabel: String? = null,
    onExtraAction: (() -> Unit)? = null,
    onSelect: (TrackOption) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalQuernPalette.current
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { firstFocus.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceElevated,
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (extraActionLabel != null && onExtraAction != null) {
                    PlayerPillButton(
                        label = extraActionLabel,
                        isPrimary = true,
                        onClick = onExtraAction
                    )
                }
                if (options.isEmpty()) {
                    Text("No tracks available for this stream.", color = Color(0xFF94A3B8))
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.height(260.dp)
                    ) {
                        items(options) { opt ->
                            val isFirst = opt == options.firstOrNull()
                            var focused by remember { mutableStateOf(false) }
                            Surface(
                                color = if (focused) palette.accent else if (opt.selected) palette.accent.copy(alpha = 0.25f) else palette.surface,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    width = if (focused) 2.dp else 1.dp,
                                    color = if (focused) palette.focusRing else palette.border
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (isFirst) Modifier.focusRequester(firstFocus) else Modifier)
                                    .clip(RoundedCornerShape(10.dp))
                                    .tvClickable(
                                        onFocusChangedState = { focused = it },
                                        onClick = { onSelect(opt) }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = opt.label,
                                        color = if (focused) palette.onAccent else Color.White,
                                        fontWeight = if (opt.selected || focused) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (opt.selected) {
                                        Icon(
                                            Icons.Default.Check,
                                            null,
                                            tint = if (focused) palette.onAccent else LumaCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            PlayerPillButton(label = "Close", isPrimary = false, onClick = onDismiss)
        }
    )
}

@OptIn(UnstableApi::class)
private fun extractTrackOptions(tracks: Tracks, trackType: Int): List<TrackOption> {
    val result = mutableListOf<TrackOption>()
    for (group in tracks.groups) {
        if (group.type != trackType) continue
        for (i in 0 until group.length) {
            val format = group.getTrackFormat(i)
            val label = buildString {
                append(format.label ?: format.language?.uppercase() ?: "Track ${result.size + 1}")
                if (format.channelCount > 0) append(" (${format.channelCount}ch)")
            }
            result += TrackOption(
                label = label,
                group = group,
                trackIndex = i,
                selected = group.isTrackSelected(i)
            )
        }
    }
    return result
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

/**
 * Full-Bleed Cinema Backdrop Loading Overlay (Matches user reference Screenshot #2!):
 * Shows atmospheric Movie/Series backdrop + subtle breathing Quern icon + dramatic bold title + "Starting stream..."
 */
@Composable
private fun QuernPlayerLoadingOverlay(
    title: String,
    backdropUrl: String?,
    showFullBackdrop: Boolean,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalQuernPalette.current
    val infinite = rememberInfiniteTransition(label = "quernPlayerLoader")

    val pulseAlpha by infinite.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "statusPulse"
    )
    val logoScale by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoPulse"
    )

    val imgReq = remember(backdropUrl) {
        if (backdropUrl.isNullOrBlank()) null else {
            ImageRequest.Builder(context)
                .data(backdropUrl)
                .size(1280, 720)
                .crossfade(true)
                .build()
        }
    }

    Box(
        modifier = modifier.background(if (showFullBackdrop) Color.Black else Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center
    ) {
        if (showFullBackdrop && imgReq != null) {
            AsyncImage(
                model = imgReq,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.55f),
                                0.5f to Color.Black.copy(alpha = 0.52f),
                                1.0f to Color.Black.copy(alpha = 0.88f)
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 42.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                    }
            ) {
                QuernBrandMark(size = 42.dp)
            }

            if (showFullBackdrop) {
                Text(
                    text = title.uppercase(),
                    color = Color.White,
                    fontSize = 42.sp,
                    lineHeight = 46.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.6.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = subtitle,
                color = Color.White.copy(alpha = pulseAlpha * 0.88f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp
            )
        }
    }
}
