package com.johnrodermund.castmanager.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions

/** Named in the manifest. The Cast SDK creates this itself when it starts. */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .setStopReceiverApplicationWhenEndingSession(true)
            // The app will have its own notification, so the SDK's is turned off.
            .setCastMediaOptions(CastMediaOptions.Builder().setNotificationOptions(null).build())
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
