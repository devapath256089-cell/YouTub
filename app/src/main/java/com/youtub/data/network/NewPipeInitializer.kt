/*
 * YouTub Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.youtub.data.network

import android.content.Context
import com.youtub.utils.PTLog
import com.youtub.utils.PlaybackDiagnostics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewPipeInitializer @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val okHttpClient: OkHttpClient
) {
    private val initializationDeferred = CompletableDeferred<Unit>()
    private val mutex = Mutex()
    private var isInitialized = false

    suspend fun ensureInitialized() {
        if (isInitialized) return
        
        mutex.withLock {
            if (isInitialized) return
            
            try {
                withContext(Dispatchers.IO) {
                    NewPipe.init(
                        YouTubeDownloader(okHttpClient),
                        Localization.DEFAULT,
                        ContentCountry("US")
                    )
                    // v1.4.6: default "visionos" (IOS-family) streams get bot-gated
                    // ("Sign in to confirm you're not a bot" -> intermittent 403 Playback
                    // Error). TVHTML5 via PS4 spoof needs no PoToken and serves direct URLs.
                    NewPipe.setYoutubePlayerClient("tv_downgraded")
                }
                PlaybackDiagnostics.init(appContext)
                PlaybackDiagnostics.log("INIT", "NewPipe ok, player client=${NewPipe.getYoutubePlayerClient()}")
                isInitialized = true
                initializationDeferred.complete(Unit)
                PTLog.d("NewPipeInitializer", "NewPipe initialized successfully (client=${NewPipe.getYoutubePlayerClient()})")
            } catch (e: Exception) {
                PTLog.e("NewPipeInitializer", "NewPipe initialization failed", e)
                // We don't completeExceptionally here to allow retries if needed, 
                // or we could, depending on the error type.
                throw e
            }
        }
    }
}
