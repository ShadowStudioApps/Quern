package com.quern.app.di

import android.content.Context
import com.quern.app.data.QuernRepository
import com.quern.app.data.addons.AddonRepository
import com.quern.app.data.addons.StremioAddonClient
import com.quern.app.data.debrid.AllDebridClient
import com.quern.app.data.debrid.DebridManager
import com.quern.app.data.debrid.PremiumizeClient
import com.quern.app.data.debrid.RealDebridClient
import com.quern.app.data.debrid.TorBoxClient
import com.quern.app.data.local.QuernDatabase
import com.quern.app.data.local.SecureVault
import com.quern.app.data.local.SettingsStore
import com.quern.app.data.metadata.TmdbClient
import com.quern.app.data.metadata.TraktClient
import com.quern.app.data.servers.JellyfinEmbyClient
import com.quern.app.data.servers.MediaServerManager
import com.quern.app.data.servers.PlexClient
import com.quern.app.data.servers.WebDavClient
import com.quern.app.data.sync.CloudSyncManager
import com.quern.app.data.sync.QuernAccount
import com.quern.app.data.sync.QuernSyncEngine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    val vault: SecureVault = SecureVault(context)
    val settingsStore: SettingsStore = SettingsStore(context)
    // Rows saved before profiles were separated move to the profile that is active at upgrade time.
    val database: QuernDatabase = QuernDatabase.build(context, settingsStore.settings.value.activeProfileId)

    val torBoxClient = TorBoxClient(httpClient, json)
    val realDebridClient = RealDebridClient(httpClient, json)
    val allDebridClient = AllDebridClient(httpClient, json)
    val premiumizeClient = PremiumizeClient(httpClient, json)

    val debridManager = DebridManager(
        vault = vault,
        torBoxClient = torBoxClient,
        realDebridClient = realDebridClient,
        allDebridClient = allDebridClient,
        premiumizeClient = premiumizeClient
    )

    val jellyfinEmbyClient = JellyfinEmbyClient(httpClient, json)
    val plexClient = PlexClient(httpClient, json)
    val webDavClient = WebDavClient(httpClient)

    val serverManager = MediaServerManager(
        dao = database.mediaServerDao(),
        vault = vault,
        jellyfinEmbyClient = jellyfinEmbyClient,
        plexClient = plexClient,
        webDavClient = webDavClient
    )

    val stremioAddonClient = StremioAddonClient(httpClient, json)
    val addonRepository = AddonRepository(
        dao = database.addonDao(),
        client = stremioAddonClient,
        debridManager = debridManager,
        json = json
    )

    val tmdbClient = TmdbClient(httpClient, json, vault)
    val traktClient = TraktClient(httpClient, json, vault)

    val repository = QuernRepository(
        addonRepository = addonRepository,
        debridManager = debridManager,
        serverManager = serverManager,
        tmdbClient = tmdbClient,
        traktClient = traktClient,
        historyDao = database.watchHistoryDao(),
        favoriteDao = database.favoriteDao(),
        activeProfileId = settingsStore.settings.map { it.activeProfileId },
        currentProfileId = { settingsStore.settings.value.activeProfileId }
    )

    /** Quern account on Shadow Studio's server (sign up / sign in / session / encryption key). */
    val quernAccount = QuernAccount(
        httpClient = httpClient,
        json = json,
        vault = vault
    )

    /** Account sync (docs/account-sync-format.md). Started by QuernApplication. */
    val syncEngine = QuernSyncEngine(
        context = context.applicationContext,
        account = quernAccount,
        settingsStore = settingsStore,
        addonDao = database.addonDao(),
        addonRepository = addonRepository,
        vault = vault,
        json = json
    )

    val cloudSyncManager = CloudSyncManager(
        httpClient = httpClient,
        json = json,
        vault = vault,
        repository = repository,
        settingsStore = settingsStore
    )
}
