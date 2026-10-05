package com.dlink.app
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.content.Intent
class DlinkQuickTileService:TileService(){override fun onClick(){qsTile.state=Tile.STATE_ACTIVE;qsTile.updateTile();startService(Intent(this,ShareForegroundService::class.java))}}
