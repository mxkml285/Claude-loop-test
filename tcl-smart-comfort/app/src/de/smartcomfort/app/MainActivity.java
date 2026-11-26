package de.smartcomfort.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Status screen, operated with the remote: pause/resume and "apply now". */
public class MainActivity extends Activity {
    private TextView status, log;
    private Button toggle;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(96, 64, 96, 64);
        root.setBackgroundColor(Color.rgb(18, 22, 28));

        TextView title = text(34, Color.WHITE);
        title.setText("Smart Comfort");
        TextView plan = text(20, Color.rgb(170, 180, 190));
        plan.setText("07–17 Uhr Helligkeit 60 · 17–07 Uhr Helligkeit 20 · 18–07 Uhr Eye Care\n"
                + "Nur Fernsehen und Google-TV-Apps. HDMI (PS5) und Soundbar bleiben unberührt.");
        status = text(24, Color.rgb(120, 220, 160));
        status.setPadding(0, 32, 0, 16);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.START);
        toggle = new Button(this);
        Button apply = new Button(this);
        apply.setText("Jetzt anwenden");
        toggle.setOnClickListener(v -> {
            Controller.setEnabled(this, !Controller.enabled(this));
            Controller.tick(this, "enabled");
            refresh();
        });
        apply.setOnClickListener(v -> { Controller.tick(this, "manual"); refresh(); });
        buttons.addView(toggle);
        buttons.addView(apply);

        log = text(18, Color.rgb(150, 160, 170));
        log.setPadding(0, 32, 0, 0);

        root.addView(title);
        root.addView(plan);
        root.addView(status);
        root.addView(buttons);
        root.addView(log);
        setContentView(root);
        toggle.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Controller.tick(this, "App geöffnet");
        refresh();
    }

    private void refresh() {
        boolean on = Controller.enabled(this);
        toggle.setText(on ? "Pausieren" : "Einschalten");
        String next = Schedule.nextBoundary(ZonedDateTime.now(Schedule.ZONE))
                .format(DateTimeFormatter.ofPattern("HH:mm"));
        status.setText(Controller.prefs(this).getString("status", "–") + "\nNächste Änderung: " + next + " Uhr");
        log.setText(Controller.prefs(this).getString("log", ""));
    }

    private TextView text(float size, int color) {
        TextView t = new TextView(this);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }
}
