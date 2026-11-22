package com.quern.app.domain.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import kotlinx.serialization.Serializable
import java.util.Locale

enum class AppLanguage(val code: String, val displayName: String, val isRtl: Boolean) {
    SYSTEM("system", "System Default (Auto)", false),
    ARABIC("ar", "العربية", true),
    ENGLISH("en", "English", false),
    FRENCH("fr", "Français", false),
    SPANISH("es", "Español", false),
    GERMAN("de", "Deutsch", false),
    TURKISH("tr", "Türkçe", false),
    RUSSIAN("ru", "Русский", false),
    PORTUGUESE("pt", "Português", false);

    fun resolveEffective(): AppLanguage {
        if (this != SYSTEM) return this
        val deviceLang = Locale.getDefault().language.lowercase()
        return entries.firstOrNull { it != SYSTEM && it.code == deviceLang } ?: ENGLISH
    }
}

enum class AppThemePreset(
    val id: String,
    val displayNameEn: String,
    val displayNameAr: String,
    val accentHex: Long,
    val focusRingHex: Long,
    val onAccentHex: Long,
    val surfaceTintHex: Long,
    val surfaceElevatedTintHex: Long,
    val borderTintHex: Long
) {
    LIGHT_DARK_BLUE(
        id = "light_dark_blue",
        displayNameEn = "Slate Blue",
        displayNameAr = "أزرق داكن هادئ",
        accentHex = 0xFF345372,
        focusRingHex = 0xFF70A4D6,
        onAccentHex = 0xFFFFFFFF,
        surfaceTintHex = 0xFF0A121B,
        surfaceElevatedTintHex = 0xFF132131,
        borderTintHex = 0xFF28425C
    ),
    LIGHT_GRAY(
        id = "light_gray",
        displayNameEn = "Light Gray",
        displayNameAr = "رمادي فاتح",
        accentHex = 0xFFD3D3D3,
        focusRingHex = 0xFFFFFFFF,
        onAccentHex = 0xFF090A0D,
        surfaceTintHex = 0xFF141517,
        surfaceElevatedTintHex = 0xFF222428,
        borderTintHex = 0xFF474A52
    ),
    OCEAN_BLUE(
        id = "ocean_blue",
        displayNameEn = "Ocean Blue",
        displayNameAr = "أزرق محيطي",
        accentHex = 0xFF2997FF,
        focusRingHex = 0xFF7CC4FF,
        onAccentHex = 0xFF03101F,
        surfaceTintHex = 0xFF081220,
        surfaceElevatedTintHex = 0xFF10223A,
        borderTintHex = 0xFF1F4B7A
    ),
    EMERALD(
        id = "emerald",
        displayNameEn = "Emerald",
        displayNameAr = "زمردي",
        accentHex = 0xFF10B981,
        focusRingHex = 0xFF6EE7B7,
        onAccentHex = 0xFF03150E,
        surfaceTintHex = 0xFF071611,
        surfaceElevatedTintHex = 0xFF0F2920,
        borderTintHex = 0xFF1B5742
    ),
    CRIMSON(
        id = "crimson",
        displayNameEn = "Crimson",
        displayNameAr = "قرمزي",
        accentHex = 0xFFCC1D42,
        focusRingHex = 0xFFFDA4AF,
        onAccentHex = 0xFFFFFFFF,
        surfaceTintHex = 0xFF1A090E,
        surfaceElevatedTintHex = 0xFF2B1119,
        borderTintHex = 0xFF642236
    ),
    AMBER_GOLD(
        id = "amber_gold",
        displayNameEn = "Cinema Gold",
        displayNameAr = "ذهبي سينمائي",
        accentHex = 0xFFF59E0B,
        focusRingHex = 0xFFFDE68A,
        onAccentHex = 0xFF140D02,
        surfaceTintHex = 0xFF171106,
        surfaceElevatedTintHex = 0xFF281D0B,
        borderTintHex = 0xFF664816
    ),
    MIDNIGHT_CONSOLE(
        id = "midnight_console",
        displayNameEn = "Midnight Console",
        displayNameAr = "أزرق منتصف الليل",
        accentHex = 0xFF00D4FF,
        focusRingHex = 0xFF7CEEFF,
        onAccentHex = 0xFF04131D,
        surfaceTintHex = 0xFF070E1F,
        surfaceElevatedTintHex = 0xFF0F1D3A,
        borderTintHex = 0xFF1E3A6E
    ),
    EMBER(
        id = "ember",
        displayNameEn = "Ember",
        displayNameAr = "جمر دافئ",
        accentHex = 0xFFFF6B2B,
        focusRingHex = 0xFFFFB088,
        onAccentHex = 0xFF160702,
        surfaceTintHex = 0xFF130B08,
        surfaceElevatedTintHex = 0xFF23140E,
        borderTintHex = 0xFF5C2E1A
    ),
    AURORA(
        id = "aurora",
        displayNameEn = "Aurora",
        displayNameAr = "شفق أخضر",
        accentHex = 0xFF2EE6B8,
        focusRingHex = 0xFF8FFAE0,
        onAccentHex = 0xFF031A14,
        surfaceTintHex = 0xFF051618,
        surfaceElevatedTintHex = 0xFF0B2A2E,
        borderTintHex = 0xFF1A5960
    ),
    OBSIDIAN_VIOLET(
        id = "obsidian_violet",
        displayNameEn = "Obsidian Violet",
        displayNameAr = "أوبسيديان بنفسجي",
        accentHex = 0xFF9333EA,
        focusRingHex = 0xFFD8B4FE,
        onAccentHex = 0xFFFFFFFF,
        surfaceTintHex = 0xFF0E0919,
        surfaceElevatedTintHex = 0xFF1B1230,
        borderTintHex = 0xFF4A327A
    );

    val accentColor: Color get() = Color(accentHex)
    val focusRingColor: Color get() = Color(focusRingHex)
    val onAccentColor: Color get() = Color(onAccentHex)
    val surfaceTint: Color get() = Color(surfaceTintHex)
    val surfaceElevatedTint: Color get() = Color(surfaceElevatedTintHex)
    val borderTint: Color get() = Color(borderTintHex)
}

enum class AccentColorChoice(
    val colorHex: Long?,
    val labelEn: String,
    val labelAr: String
) {
    THEME_DEFAULT(null, "Theme Default", "افتراضي السمة"),
    OCEAN_BLUE(0xFF2997FF, "Ocean Blue", "أزرق محيطي"),
    SLATE_BLUE(0xFF345372, "Slate Blue", "أزرق داكن"),
    EMERALD(0xFF10B981, "Emerald", "زمردي"),
    CRIMSON(0xFFCC1D42, "Crimson", "قرمزي"),
    AMBER_GOLD(0xFFF59E0B, "Cinema Gold", "ذهبي"),
    CYAN(0xFF00D4FF, "Cyan", "سماوي ساطع"),
    EMBER_ORANGE(0xFFFF6B2B, "Ember Orange", "برتقالي جمر"),
    AURORA_MINT(0xFF2EE6B8, "Aurora Mint", "نعناعي"),
    OBSIDIAN_VIOLET(0xFF9333EA, "Obsidian Violet", "بنفسجي"),
    ICE_GRAY(0xFFD3D3D3, "Light Gray", "رمادي فاتح")
}

enum class AppFontChoice(
    val labelEn: String,
    val labelAr: String
) {
    DEFAULT("System Default", "خط النظام"),
    SANS_SERIF("Clean Sans", "خط عصري Sans"),
    SERIF("Editorial Serif", "خط كلاسيكي Serif"),
    MONOSPACE("Monospace", "أحادي العرض Mono");

    val fontFamily: FontFamily
        get() = when (this) {
            DEFAULT -> FontFamily.Default
            SANS_SERIF -> FontFamily.SansSerif
            SERIF -> FontFamily.Serif
            MONOSPACE -> FontFamily.Monospace
        }
}

enum class FontScalePreset(
    val multiplier: Float,
    val labelEn: String,
    val labelAr: String
) {
    COMPACT(0.92f, "Compact (92%)", "مضغوط (92%)"),
    STANDARD(1.0f, "Standard (100%)", "قياسي (100%)"),
    LARGE(1.12f, "Large (112%)", "كبير (112%)")
}

enum class AppIconOption(
    val aliasName: String,
    val displayNameEn: String,
    val displayNameAr: String,
    val previewColorHex: Long
) {
    SLATE_BLUE("com.quern.app.IconSlateBlue", "Slate Blue", "أزرق داكن", 0xFF345372),
    LIGHT_GRAY("com.quern.app.IconLightGray", "Light Gray", "رمادي فاتح", 0xFFD3D3D3),
    AMOLED("com.quern.app.IconAmoled", "AMOLED White", "أبيض نقي AMOLED", 0xFFFFFFFF),
    OCEAN_BLUE("com.quern.app.IconOceanBlue", "Ocean Blue", "أزرق محيطي", 0xFF2997FF)
}

enum class NavigationStyle(val labelEn: String, val labelAr: String) {
    SIDEBAR_LEFT("Left Sidebar", "شريط جانبي"),
    SIDEBAR_COLLAPSED("Auto-Hide Sidebar", "شريط جانبي مخفي"),
    TOP_BAR("Top Bar", "شريط علوي")
}

enum class SubtitleLanguage(val isoCode: String, val displayNameEn: String, val displayNameAr: String) {
    ARABIC("ar", "Arabic (العربية)", "العربية"),
    ENGLISH("en", "English", "الإنجليزية"),
    FRENCH("fr", "French (Français)", "الفرنسية"),
    SPANISH("es", "Spanish (Español)", "الإسبانية"),
    GERMAN("de", "German (Deutsch)", "الألمانية"),
    TURKISH("tr", "Turkish (Türkçe)", "التركية"),
    RUSSIAN("ru", "Russian (Русский)", "الروسية"),
    PORTUGUESE("pt", "Portuguese", "البرتغالية"),
    ITALIAN("it", "Italian", "الإيطالية"),
    JAPANESE("ja", "Japanese", "اليابانية"),
    OFF("", "Off (Disabled)", "إيقاف الترجمة")
}

enum class AudioLanguage(val isoCode: String, val displayNameEn: String, val displayNameAr: String) {
    ORIGINAL("", "Original Audio", "الصوت الأصلي"),
    ARABIC("ar", "Arabic (العربية)", "العربية"),
    ENGLISH("en", "English", "الإنجليزية"),
    FRENCH("fr", "French (Français)", "الفرنسية"),
    SPANISH("es", "Spanish (Español)", "الإسبانية"),
    GERMAN("de", "German (Deutsch)", "الألمانية"),
    TURKISH("tr", "Turkish (Türkçe)", "التركية"),
    JAPANESE("ja", "Japanese", "اليابانية"),
    RUSSIAN("ru", "Russian (Русский)", "الروسية"),
    PORTUGUESE("pt", "Portuguese", "البرتغالية")
}

enum class SubtitleSize(val spValue: Float, val displayNameEn: String, val displayNameAr: String) {
    SMALL(16f, "Small", "صغير"),
    MEDIUM(21f, "Medium", "متوسط"),
    LARGE(26f, "Large", "كبير"),
    XLARGE(32f, "Extra Large", "كبير جداً")
}

enum class PlayerEngine(
    val labelEn: String,
    val labelAr: String,
    val descEn: String,
    val descAr: String
) {
    MEDIA3_EXOPLAYER(
        labelEn = "Media3 ExoPlayer",
        labelAr = "Media3 ExoPlayer",
        descEn = "Built-in hardware 4K / HDR / Dolby Vision & FFmpeg audio player",
        descAr = "المشغل الداخلي المدمج بدعم 4K و HDR و Dolby Vision وصوتيات FFmpeg"
    ),
    EXTERNAL(
        labelEn = "External Player",
        labelAr = "مشغل خارجي",
        descEn = "Open stream in an installed player (VLC, MX Player, Just Player, mpv-android) or system chooser",
        descAr = "فتح الفيديو في مشغل مثبت (VLC أو MX Player أو Just Player أو mpv-android) أو اختيار النظام"
    )
}

enum class DefaultAspectMode(val labelEn: String, val labelAr: String) {
    FIT("Fit (Original)", "ملائم (الأصلي)"),
    ZOOM("Zoom (Crop)", "تكبير (ملء الشاشة)"),
    FILL("Stretch", "تمديد")
}

enum class BufferProfile(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val playbackBufferMs: Int,
    val rebufferMs: Int,
    val labelEn: String,
    val labelAr: String
) {
    LOW(10_000, 20_000, 1_500, 3_000, "Low (Save RAM)", "منخفض (توفير الذاكرة)"),
    BALANCED(20_000, 50_000, 2_500, 5_000, "Balanced", "متوازن"),
    HIGH(40_000, 120_000, 3_500, 6_000, "High (4K Remux)", "عالي (أفلام 4K)")
}

enum class CardSizePreset(val phoneWidthDp: Int, val tvWidthDp: Int, val labelEn: String, val labelAr: String) {
    COMPACT(64, 74, "Compact", "صغير"),
    STANDARD(74, 86, "Standard", "قياسي"),
    LARGE(92, 108, "Large", "كبير"),
    XLARGE(112, 130, "Extra Large", "كبير جداً")
}

enum class CardShapePreset(val aspectRatio: Float, val labelEn: String, val labelAr: String) {
    PORTRAIT(2f / 3f, "Portrait (2:3)", "عمودي (2:3)"),
    LANDSCAPE(16f / 9f, "Landscape (16:9)", "عريض (16:9)"),
    SQUARE(1f, "Square (1:1)", "مربع (1:1)")
}

enum class CardCornerPreset(val radiusDp: Int, val labelEn: String, val labelAr: String) {
    SHARP(4, "Sharp", "حواف حادة"),
    ROUNDED(14, "Rounded", "حواف دائرية"),
    EXTRA_ROUNDED(24, "Soft Curve", "حواف ناعمة جداً")
}

enum class RowOrderPreset(val labelEn: String, val labelAr: String) {
    DEFAULT("Default Order", "الترتيب الافتراضي"),
    MOVIES_FIRST("Movies First", "الأفلام أولاً"),
    SERIES_FIRST("Series First", "المسلسلات أولاً"),
    SERVERS_FIRST("Media Servers First", "الخوادم الشخصية أولاً")
}

enum class ContinueWatchingSort(val labelEn: String, val labelAr: String) {
    RECENT("Recently Watched", "الأحدث مشاهدة"),
    PROGRESS_DESC("Most Completed", "الأقرب للانتهاء"),
    TITLE_AZ("Title (A–Z)", "الاسم (أ–ي)")
}

enum class ContinueWatchingAction(val labelEn: String, val labelAr: String) {
    OPEN_DETAILS("Open Details Page", "فتح صفحة التفاصيل"),
    RESUME_DIRECT("Resume Playback Directly", "استئناف التشغيل مباشرة")
}

enum class UpdateChannel(val labelEn: String, val labelAr: String) {
    STABLE("Stable", "مستقر (Stable)"),
    BETA("Beta", "تجريبي (Beta)")
}

enum class QuernDevicePreset {
    TV,
    PHONE,
    LOW_END
}

@Serializable
data class UserProfile(
    val id: String,
    val name: String,
    val avatarColorHex: Long,
    val pinCode: String? = null // 4-digit optional PIN lock
) {
    val isLocked: Boolean get() = !pinCode.isNullOrBlank()
}

data class AppSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val theme: AppThemePreset = AppThemePreset.LIGHT_DARK_BLUE,
    val amoledPureBlack: Boolean = true,
    val accentColor: AccentColorChoice = AccentColorChoice.THEME_DEFAULT,
    val fontChoice: AppFontChoice = AppFontChoice.DEFAULT,
    val fontScale: FontScalePreset = FontScalePreset.STANDARD,
    val appIcon: AppIconOption = AppIconOption.SLATE_BLUE,
    val navigationStyle: NavigationStyle = NavigationStyle.SIDEBAR_LEFT,
    val subtitleLanguage: SubtitleLanguage = SubtitleLanguage.ARABIC,
    val subtitleSize: SubtitleSize = SubtitleSize.MEDIUM,
    val subtitleColorHex: Long = 0xFFFFFFFF,
    val audioLanguage: AudioLanguage = AudioLanguage.ORIGINAL,
    val skipIntroEnabled: Boolean = true,
    val skipOutroEnabled: Boolean = true,
    val autoPlayNext: Boolean = true,
    val nextEpisodeCountdownSec: Int = 10,
    val playerEngine: PlayerEngine = PlayerEngine.MEDIA3_EXOPLAYER,
    val preferExternalPlayer: Boolean = false,
    val preferredExternalPlayerPackage: String = "",
    val audioVolumeBoostEnabled: Boolean = false,
    val defaultPlaybackSpeed: Float = 1.0f,
    val defaultAspectMode: DefaultAspectMode = DefaultAspectMode.FIT,
    val bufferProfile: BufferProfile = BufferProfile.BALANCED,
    val showHeroBanner: Boolean = true,
    val showPosterLabels: Boolean = true,
    val hideUnreleasedContent: Boolean = false,
    val rowOrder: RowOrderPreset = RowOrderPreset.DEFAULT,
    val showContinueWatching: Boolean = true,
    val continueWatchingSort: ContinueWatchingSort = ContinueWatchingSort.RECENT,
    val continueWatchingAction: ContinueWatchingAction = ContinueWatchingAction.OPEN_DETAILS,
    val cardSize: CardSizePreset = CardSizePreset.STANDARD,
    val cardShape: CardShapePreset = CardShapePreset.PORTRAIT,
    val cardCorner: CardCornerPreset = CardCornerPreset.ROUNDED,
    val uiModeOverride: UiModeOverride = UiModeOverride.AUTO,
    val hiddenCatalogKeys: Set<String> = emptySet(),
    val updateChannel: UpdateChannel = UpdateChannel.STABLE,
    val crashReportingOptIn: Boolean = false,
    val activeProfileId: String = "main",
    val profiles: List<UserProfile> = listOf(
        UserProfile("main", "Shadow", 0xFFD97736, pinCode = null),
        UserProfile("family", "Family", 0xFF8C3B5E, pinCode = null),
        UserProfile("kids", "Kids", 0xFF0E7C7B, pinCode = null)
    )
) {
    val activeProfile: UserProfile
        get() = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.first()

    val effectiveLanguage: AppLanguage
        get() = language.resolveEffective()
}
