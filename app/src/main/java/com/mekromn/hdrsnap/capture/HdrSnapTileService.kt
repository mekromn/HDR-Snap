package com.mekromn.hdrsnap.capture

import android.content.Intent
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class HdrSnapTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = if (HdrSnapBridge.isConnected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "HDR Screenshot"
            subtitle = if (HdrSnapBridge.isConnected) "System HDR path" else "Enable HDR Snap service"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (HdrSnapBridge.requestSystemScreenshot()) {
            qsTile?.apply {
                state = Tile.STATE_ACTIVE
                subtitle = "Capturing…"
                updateTile()
            }
        } else {
            startActivityAndCollapse(
                android.app.PendingIntent.getActivity(
                    this,
                    0,
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        }
    }
}
