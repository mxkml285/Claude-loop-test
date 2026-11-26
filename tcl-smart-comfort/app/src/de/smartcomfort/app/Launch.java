package de.smartcomfort.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.tv.TvContract;
import android.net.Uri;
import android.util.Log;

/**
 * The app and HDMI calls the start page uses, shared with the remote keys in HomeKeyService so a tile
 * and a key always do exactly the same thing.
 */
final class Launch {
    private Launch() { }

    /** Opens an app the way Google TV does (leanback entry first). False if it is not installed. */
    static boolean app(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        return start(c, i);
    }

    /** Opens HDMI 2 (PS5) directly through TCL's own TV app (the red "TV" app), no input menu. */
    static boolean ps5(Context c) {
        Uri uri = TvContract.buildChannelUriForPassthroughInput(HomeActivity.PS5_INPUT);
        Intent i = new Intent(Intent.ACTION_VIEW, uri).setComponent(HomeActivity.TCL_TV_ACTIVITY);
        return start(c, i) || app(c, HomeActivity.TCL_TV);
    }

    static boolean start(Context c, Intent i) {
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            Log.w("SmartComfort", "start failed: " + i, e);
            return false;
        }
    }
}
