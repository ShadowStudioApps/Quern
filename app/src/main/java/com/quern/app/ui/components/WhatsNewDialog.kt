package com.quern.app.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.quern.app.R
import com.quern.app.data.update.AvailableUpdate
import com.quern.app.data.update.ReleaseNoteBlock
import com.quern.app.data.update.ReleaseNoteKind
import com.quern.app.data.update.ReleaseNoteSpan
import com.quern.app.data.update.ReleaseNotesEntry
import com.quern.app.data.update.ReleaseNotesParser
import com.quern.app.ui.theme.LocalQuernPalette
import com.quern.app.ui.theme.LumaTextPrimary
import com.quern.app.ui.theme.LumaTextSecondary
import com.quern.app.ui.theme.qString
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class ParsedReleaseNotes(
    val version: String,
    val publishedDateText: String,
    val isPrerelease: Boolean,
    val blocks: List<ReleaseNoteBlock>
)

internal fun parseReleaseEntries(entries: List<ReleaseNotesEntry>, arabic: Boolean): List<ParsedReleaseNotes> {
    var remaining = ReleaseNotesParser.MAX_DISPLAY_CHARS
    val out = mutableListOf<ParsedReleaseNotes>()
    for (entry in entries) {
        if (remaining <= 0) break
        val blocks = ReleaseNotesParser.parse(entry.releaseNotes, arabic = arabic, maxChars = remaining)
        if (blocks.isEmpty()) continue
        out += ParsedReleaseNotes(
            version = entry.version,
            publishedDateText = entry.publishedDateText,
            isPrerelease = entry.isPrerelease,
            blocks = blocks
        )
        remaining = (remaining - blocks.sumOf { it.text.length + 1 }).coerceAtLeast(0)
    }
    return out
}

internal fun parseAvailableUpdateNotes(update: AvailableUpdate, arabic: Boolean): List<ParsedReleaseNotes> =
    parseReleaseEntries(update.releaseEntries, arabic)

internal fun buildReleaseNotesPreview(sections: List<ParsedReleaseNotes>): AnnotatedString {
    val firstBlocks = sections.firstOrNull()?.blocks ?: return AnnotatedString("")
    return buildAnnotatedString {
        firstBlocks.forEachIndexed { idx, block ->
            if (idx > 0) append('\n')
            if (block.kind == ReleaseNoteKind.BULLET) {
                append("• ")
            }
            val start = length
            appendStyledSpans(block.spans)
            if (block.kind == ReleaseNoteKind.HEADING) {
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
            }
        }
    }
}

private fun AnnotatedString.Builder.appendStyledSpans(spans: List<ReleaseNoteSpan>) {
    for (span in spans) {
        if (span.text.isEmpty()) continue
        if (span.bold) {
            val start = length
            append(span.text)
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
        } else {
            append(span.text)
        }
    }
}

private fun buildStyledSpans(spans: List<ReleaseNoteSpan>): AnnotatedString = buildAnnotatedString {
    appendStyledSpans(spans)
}

@Composable
internal fun WhatsNewDialog(
    release: AvailableUpdate,
    sections: List<ParsedReleaseNotes>,
    isTv: Boolean,
    updateLabel: String = qString(R.string.update_action_view),
    updateEnabled: Boolean = true,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onDismiss: () -> Unit = onLater
) {
    WhatsNewDialog(
        version = release.version,
        sections = sections,
        isTv = isTv,
        primaryLabel = updateLabel,
        primaryEnabled = updateEnabled,
        secondaryLabel = qString(R.string.update_action_later),
        onPrimary = onUpdate,
        onSecondary = onLater,
        onDismiss = onDismiss
    )
}

@Composable
internal fun WhatsNewDialog(
    version: String,
    sections: List<ParsedReleaseNotes>,
    isTv: Boolean,
    primaryLabel: String,
    primaryEnabled: Boolean = true,
    secondaryLabel: String? = null,
    onPrimary: () -> Unit,
    onSecondary: (() -> Unit)? = null,
    onDismiss: () -> Unit = onSecondary ?: onPrimary
) {
    val palette = LocalQuernPalette.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val isCompact = LocalQuernLayout.current == QuernLayoutClass.COMPACT
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    val scrollStepPx = with(LocalDensity.current) { 72.dp.roundToPx() }
    val primaryFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (isTv) {
            withFrameNanos { }
            runCatching { primaryFocus.requestFocus() }
        }
    }

    QuernAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceElevated,
        properties = DialogProperties(usePlatformDefaultWidth = true),
        modifier = Modifier.onPreviewKeyEvent { ev ->
            if (!isTv || ev.type != KeyEventType.KeyDown || scrollState.maxValue <= 0) {
                return@onPreviewKeyEvent false
            }
            val delta = when (ev.key) {
                Key.DirectionDown -> scrollStepPx
                Key.DirectionUp -> -scrollStepPx
                else -> return@onPreviewKeyEvent false
            }
            val target = (scrollState.value + delta).coerceIn(0, scrollState.maxValue)
            if (target != scrollState.value) {
                scrollJob?.cancel()
                scrollJob = scope.launch {
                    scrollState.animateScrollTo(target, tween(120))
                }
            }
            true
        },
        title = {
            Text(
                text = qString(R.string.update_whats_new_title, version),
                color = LumaTextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            val thumbColor = palette.focusRing.copy(alpha = 0.45f)
            Surface(
                color = palette.surface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (isTv) 320.dp else 360.dp)
                        .drawWithContent {
                            drawContent()
                            val max = scrollState.maxValue
                            if (max > 0) {
                                val viewportH = size.height
                                val totalH = viewportH + max.toFloat()
                                val thumbH = (viewportH * (viewportH / totalH)).coerceAtLeast(24.dp.toPx())
                                val frac = scrollState.value.toFloat() / max.toFloat()
                                val thumbY = (viewportH - thumbH) * frac
                                val thumbW = 3.dp.toPx()
                                val margin = 5.dp.toPx()
                                val thumbX = if (isRtl) margin else size.width - margin - thumbW
                                drawRoundRect(
                                    color = thumbColor,
                                    topLeft = Offset(thumbX, thumbY),
                                    size = Size(thumbW, thumbH),
                                    cornerRadius = CornerRadius(thumbW / 2f, thumbW / 2f)
                                )
                            }
                        }
                        .verticalScroll(scrollState)
                        .padding(14.dp)
                ) {
                    val multiRelease = sections.size > 1
                    sections.forEachIndexed { sectionIndex, section ->
                        if (sectionIndex > 0) {
                            Spacer(Modifier.height(4.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                            Spacer(Modifier.height(4.dp))
                        }
                        if (multiRelease || section.publishedDateText.isNotBlank() || section.isPrerelease) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (multiRelease) {
                                    Surface(
                                        color = palette.accent.copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(999.dp),
                                        border = BorderStroke(1.dp, palette.focusRing.copy(alpha = 0.65f))
                                    ) {
                                        Text(
                                            text = "v${section.version}",
                                            color = palette.focusRing,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (section.isPrerelease) {
                                    Surface(
                                        color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(999.dp),
                                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f))
                                    ) {
                                        Text(
                                            text = qString(R.string.update_prerelease_badge),
                                            color = Color(0xFFFDE68A),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (section.publishedDateText.isNotBlank()) {
                                    Text(
                                        text = qString(R.string.update_released_on, section.publishedDateText),
                                        color = LumaTextSecondary,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                        }

                        section.blocks.forEachIndexed { blockIdx, block ->
                            when (block.kind) {
                                ReleaseNoteKind.HEADING -> {
                                    if (blockIdx > 0) Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = buildStyledSpans(block.spans),
                                        color = palette.focusRing,
                                        fontSize = 14.sp,
                                        lineHeight = 19.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                ReleaseNoteKind.BULLET -> {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "•",
                                            color = palette.focusRing,
                                            fontSize = 13.sp,
                                            lineHeight = 19.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = buildStyledSpans(block.spans),
                                            color = LumaTextPrimary.copy(alpha = 0.92f),
                                            fontSize = 13.sp,
                                            lineHeight = 19.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                ReleaseNoteKind.PARAGRAPH -> {
                                    Text(
                                        text = buildStyledSpans(block.spans),
                                        color = LumaTextPrimary.copy(alpha = 0.90f),
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = if (secondaryLabel != null && onSecondary != null) {
            {
                WhatsNewActionPill(
                    label = secondaryLabel,
                    isPrimary = false,
                    isCompact = isCompact,
                    onClick = onSecondary
                )
            }
        } else null,
        confirmButton = {
            WhatsNewActionPill(
                label = primaryLabel,
                isPrimary = true,
                isCompact = isCompact,
                enabled = primaryEnabled,
                modifier = Modifier.focusRequester(primaryFocus),
                onClick = onPrimary
            )
        }
    )
}

@Composable
private fun WhatsNewActionPill(
    label: String,
    isPrimary: Boolean,
    isCompact: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalQuernPalette.current
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> palette.accent
        isPrimary -> Color.White
        else -> palette.glassElevated
    }
    val fg = when {
        focused -> palette.onAccent
        isPrimary -> Color.Black
        else -> LumaTextPrimary
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(
            width = if (focused) 2.5.dp else 1.dp,
            color = if (focused) palette.focusRing else if (isPrimary) Color.Transparent else palette.border
        ),
        modifier = modifier
            .then(if (isCompact) Modifier.heightIn(min = 48.dp) else Modifier)
            .widthIn(min = 76.dp)
            .clip(RoundedCornerShape(999.dp))
            .tvClickable(
                enabled = enabled,
                onFocusChangedState = { focused = it },
                onClick = onClick
            )
    ) {
        androidx.compose.foundation.layout.Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = if (isCompact) 12.dp else 9.dp
            )
        ) {
            Text(
                text = label,
                color = fg,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
