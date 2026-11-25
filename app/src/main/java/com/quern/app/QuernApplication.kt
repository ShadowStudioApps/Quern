package com.quern.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.quern.app.data.local.AppIconManager
import com.quern.app.di.AppContainer
import kotlinx.coroutines.runBlocking

class QuernApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        if (container.syncEngine.hasPendingAccountReset()) {
            runBlocking { container.syncEngine.finishPendingAccountReset() }
        }
        applySavedLauncherIcon()
        container.syncEngine.start()
    }

    internal fun applySavedLauncherIcon() {
        runCatching {
            AppIconManager.applySavedIcon(this, container.settingsStore.settings.value.appIcon)
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { container.httpClient }
        .memoryCache {
            MemoryCache.Builder(this)
                // 36 MB cap avoids poster eviction during 85% scrolling while bounding TV Native Heap + GL mtrack.
                .maxSizeBytes(36 * 1024 * 1024)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("quern_image_cache"))
                .maxSizeBytes(150L * 1024L * 1024L)
                .build()
        }
        .allowHardware(true)
        .crossfade(180)
        .respectCacheHeaders(false)
        .build()
}
