package de.smartcomfort.app;

import android.os.Build;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * The transaction codes are only valid for the firmware they were read from. After any TCL update
 * (other fingerprint or other jar) the app stops touching the TV until the codes are re-checked.
 */
final class FirmwareGuard {
    static final String FINGERPRINT = "TCL/G08_4K_GB/G08:14/UTT2.250416.001/AU02:user/release-keys";
    static final String JAR = "/system_ext/framework/com.tcl.tv.display.jar";
    static final String JAR_SHA256 = "c14f5ea2d1374ab5a6abeae0c6bbfa15a6a0129a12aad7908332c62fc44a4600";

    private static String cached;

    private FirmwareGuard() { }

    /** null if this is the checked firmware, otherwise the reason (shown on screen). */
    static synchronized String problem() {
        if (cached != null) return cached.isEmpty() ? null : cached;
        String p;
        if (!FINGERPRINT.equals(Build.FINGERPRINT)) {
            p = "Andere Firmware: " + Build.FINGERPRINT;
        } else {
            String h = sha256(JAR);
            p = JAR_SHA256.equals(h) ? "" : "TCL-Bibliothek verändert (" + h + ")";
        }
        cached = p;
        return p.isEmpty() ? null : p;
    }

    static String sha256(String path) {
        try (InputStream in = new FileInputStream(path)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "nicht lesbar: " + e.getClass().getSimpleName();
        }
    }
}
