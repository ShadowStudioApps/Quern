package com.quern.app.player

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Parcelable
import com.quern.app.domain.model.SubtitleTrack

enum class ExternalPlayerKind {
    VLC,
    MX_PLAYER,
    JUST_PLAYER,
    MPV,
    SYSTEM_CHOOSER
}

data class InstalledExternalPlayer(
    val packageName: String,
    val displayName: String,
    val kind: ExternalPlayerKind
)

sealed interface ExternalLaunchResult {
    data object Launched : ExternalLaunchResult
    data class TargetNotInstalled(val displayName: String) : ExternalLaunchResult
    data object NoExternalPlayerAvailable : ExternalLaunchResult
    data object Failed : ExternalLaunchResult
}

object ExternalPlayerHelper {

    private val knownPlayers = listOf(
        InstalledExternalPlayer("org.videolan.vlc", "VLC", ExternalPlayerKind.VLC),
        InstalledExternalPlayer("com.mxtech.videoplayer.pro", "MX Player Pro", ExternalPlayerKind.MX_PLAYER),
        InstalledExternalPlayer("com.mxtech.videoplayer.ad", "MX Player", ExternalPlayerKind.MX_PLAYER),
        InstalledExternalPlayer("com.brouken.player", "Just Player", ExternalPlayerKind.JUST_PLAYER),
        InstalledExternalPlayer("is.xyz.mpv", "mpv-android", ExternalPlayerKind.MPV),
        InstalledExternalPlayer("live.mehiz.mpvkt", "mpvKt", ExternalPlayerKind.MPV)
    )

    /**
     * Detects which of the supported external video players are actually installed on the device.
     * Never bundles any player.
     */
    fun detectInstalledPlayers(context: Context): List<InstalledExternalPlayer> {
        val pm = context.packageManager
        return knownPlayers.filter { candidate ->
            runCatching {
                pm.getLaunchIntentForPackage(candidate.packageName) != null ||
                    pm.resolveActivity(
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse("https://example.com/video.mp4"), "video/*")
                            setPackage(candidate.packageName)
                        },
                        0
                    ) != null
            }.getOrDefault(false)
        }
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return detectInstalledPlayers(context).any { it.packageName == packageName }
    }

    fun isVolumeBoostSupported(): Boolean = runCatching {
        val effects = AudioEffect.queryEffects() ?: return@runCatching false
        effects.any { it.type == AudioEffect.EFFECT_TYPE_LOUDNESS_ENHANCER }
    }.getOrDefault(false)

    fun displayNameForPackage(packageName: String): String =
        knownPlayers.firstOrNull { it.packageName == packageName }?.displayName ?: packageName

    /**
     * Launches an external player (or the system video chooser when targetPackage is null/blank),
     * passing stream URL, title, subtitle tracks, HTTP headers, and resume position.
     * Never silently falls back to an empty chooser when an explicit targetPackage is not installed.
     */
    fun launchExternalPlayer(
        context: Context,
        request: PlaybackRequest,
        targetPackage: String?,
        preferredSubLangIso: String? = null
    ): ExternalLaunchResult {
        val url = request.stream.url?.takeIf { it.isNotBlank() } ?: return ExternalLaunchResult.Failed
        val fullTitle = buildString {
            append(request.title)
            if (!request.subtitle.isNullOrBlank()) {
                append(" - ")
                append(request.subtitle)
            }
        }
        val allSubs = (request.stream.subtitles + request.externalSubtitles).distinctBy { it.url }
        val preferredSub = pickPreferredSubtitle(allSubs, preferredSubLangIso)
        val resumeMs = request.startPositionMs.coerceAtLeast(0L)

        val explicitTarget = targetPackage?.takeIf { it.isNotBlank() }
        if (explicitTarget != null && !isPackageInstalled(context, explicitTarget)) {
            return ExternalLaunchResult.TargetNotInstalled(displayNameForPackage(explicitTarget))
        }

        val kind = knownPlayers.firstOrNull { it.packageName == explicitTarget }?.kind
            ?: ExternalPlayerKind.SYSTEM_CHOOSER

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "video/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("title", fullTitle)

            if (explicitTarget != null) {
                setPackage(explicitTarget)
            }

            when (kind) {
                ExternalPlayerKind.VLC -> {
                    if (resumeMs > 0L) {
                        putExtra("position", resumeMs)
                        putExtra("from_start", false)
                    }
                    preferredSub?.let { sub ->
                        putExtra("subtitles_location", Uri.parse(sub.url))
                    }
                }

                ExternalPlayerKind.MX_PLAYER -> {
                    if (resumeMs > 0L) {
                        putExtra("position", resumeMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    }
                    putExtra("return_result", true)
                    attachSubtitleArrayExtras(this, allSubs, preferredSub, includeEnableList = true)
                    attachHeadersExtra(this, request.stream.headers)
                }

                ExternalPlayerKind.JUST_PLAYER -> {
                    if (resumeMs > 0L) {
                        putExtra("position", resumeMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    }
                    attachSubtitleArrayExtras(this, allSubs, preferredSub, includeEnableList = false)
                }

                ExternalPlayerKind.MPV -> {
                    if (resumeMs > 0L) {
                        putExtra("position", resumeMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    }
                    attachSubtitleArrayExtras(this, allSubs, preferredSub, includeEnableList = false)
                    attachHeadersExtra(this, request.stream.headers)
                }

                ExternalPlayerKind.SYSTEM_CHOOSER -> {
                    if (resumeMs > 0L) {
                        putExtra("position", resumeMs)
                    }
                    preferredSub?.let { sub ->
                        putExtra("subtitles_location", Uri.parse(sub.url))
                    }
                    attachSubtitleArrayExtras(this, allSubs, preferredSub, includeEnableList = false)
                    attachHeadersExtra(this, request.stream.headers)
                }
            }
        }

        if (explicitTarget == null) {
            val handlers = runCatching { context.packageManager.queryIntentActivities(intent, 0) }
                .getOrDefault(emptyList())
                .filter { it.activityInfo?.packageName != context.packageName }
            if (handlers.isEmpty()) {
                return ExternalLaunchResult.NoExternalPlayerAvailable
            }
        }

        return runCatching {
            if (explicitTarget == null) {
                context.startActivity(Intent.createChooser(intent, fullTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                context.startActivity(intent)
            }
            ExternalLaunchResult.Launched
        }.getOrDefault(ExternalLaunchResult.Failed)
    }

    private fun pickPreferredSubtitle(subs: List<SubtitleTrack>, iso: String?): SubtitleTrack? {
        if (subs.isEmpty()) return null
        if (!iso.isNullOrBlank()) {
            val match = subs.firstOrNull {
                it.language.equals(iso, ignoreCase = true) ||
                    it.language.startsWith(iso, ignoreCase = true) ||
                    it.label.contains(iso, ignoreCase = true)
            }
            if (match != null) return match
        }
        return subs.firstOrNull()
    }

    private fun attachSubtitleArrayExtras(
        intent: Intent,
        subs: List<SubtitleTrack>,
        preferredSub: SubtitleTrack?,
        includeEnableList: Boolean
    ) {
        if (subs.isEmpty()) return
        val uris = subs.map { Uri.parse(it.url) as Parcelable }.toTypedArray()
        val names = subs.map { it.label.ifBlank { it.language } }.toTypedArray()
        intent.putExtra("subs", uris)
        intent.putExtra("subs.name", names)
        if (includeEnableList && preferredSub != null) {
            intent.putExtra("subs.enable", arrayOf<Parcelable>(Uri.parse(preferredSub.url)))
        }
    }

    private fun attachHeadersExtra(intent: Intent, headers: Map<String, String>) {
        if (headers.isEmpty()) return
        val flat = headers.entries.flatMap { listOf(it.key, it.value) }.toTypedArray()
        intent.putExtra("headers", flat)
    }
}
