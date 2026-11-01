package com.johnrodermund.castmanager.cast

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

/** Lists Cast Devices, and offers disconnecting during a Cast Session. Must be hosted by a FragmentActivity. */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            MediaRouteButton(context).also { CastButtonFactory.setUpMediaRouteButton(context, it) }
        },
        modifier = modifier,
    )
}
