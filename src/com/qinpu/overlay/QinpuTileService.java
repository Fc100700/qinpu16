/*
 * 琴谱浮窗 (Qinpu Overlay) - Android 版
 * 作者: Fc100700
 * GitHub: https://github.com/Fc100700
 */

package com.qinpu.overlay;

import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/* JADX INFO: loaded from: classes.dex */
public class QinpuTileService extends TileService {
    @Override // android.service.quicksettings.TileService
    public void onStartListening() {
        super.onStartListening();
        syncTile();
    }

    @Override // android.service.quicksettings.TileService
    public void onClick() {
        super.onClick();
        boolean z = OverlayService.overlayActive;
        Intent intent = new Intent(this, (Class<?>) OverlayService.class);
        intent.setAction(OverlayService.ACTION_TILE);
        try {
            startForegroundService(intent);
        } catch (Exception unused) {
        }
        Tile qsTile = getQsTile();
        if (qsTile != null) {
            qsTile.setState(z ? 1 : 2);
            qsTile.updateTile();
        }
    }

    private void syncTile() {
        Tile qsTile = getQsTile();
        if (qsTile != null) {
            qsTile.setState(OverlayService.overlayActive ? 2 : 1);
            qsTile.updateTile();
        }
    }
}
