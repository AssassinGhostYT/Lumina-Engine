package com.lumina.engine.csharp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.lumina.engine.Utils;
import com.lumina.engine.ui.Dialogs;
import com.lumina.engine.ui.Tr;

import java.io.File;

/**
 * Runs Mono/Roslyn in a dedicated process.
 *
 * A native crash, an out-of-memory kill or a stop-the-world pause in Mono then
 * only takes down this activity; the code editor and the running game stay
 * alive in the main process.
 */
public final class CSharpRunActivity extends AppCompatActivity {
    public static final String EXTRA_FILES = "files";
    public static final String EXTRA_TEST = "test";

    private volatile boolean closed;
    private TextView loadingText;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Utils.setLanguage(this);
        setContentView(loadingView());

        String previous = CSharpTrace.incomplete(this);
        if (previous != null) {
            AlertDialog dialog = Dialogs.longText(this,
                    "La ejecucion C# anterior se cerro", previous);
            dialog.setOnDismissListener(d -> beginRun());
            return;
        }
        beginRun();
    }

    private void beginRun() {
        Intent intent = getIntent();
        CSharpTrace.begin(this, "activity start test=" + intent.getBooleanExtra(EXTRA_TEST, false));
        if (intent.getBooleanExtra(EXTRA_TEST, false)) {
            startTask(() -> CSharpRuntime.test(getApplicationContext()));
            return;
        }

        String[] paths = intent.getStringArrayExtra(EXTRA_FILES);
        if (paths == null || paths.length == 0) {
            showResult("No hay archivos C# para compilar.");
            return;
        }
        File[] files = new File[paths.length];
        for (int i = 0; i < paths.length; i++) files[i] = new File(paths[i]);
        startTask(() -> CSharpRuntime.compileAndRun(getApplicationContext(), files));
    }

    private interface Task {
        String run();
    }

    private void startTask(Task task) {
        new Thread(() -> {
            String result;
            try {
                result = task.run();
            } catch (Throwable error) {
                result = "Error C#: " + error;
            }
            CSharpTrace.done(getApplicationContext());
            final String text = result;
            runOnUiThread(() -> showResult(text));
        }, "nebulon-csharp-run").start();
    }

    private View loadingView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#080B10"));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);

        box.addView(new ProgressBar(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        loadingText = new TextView(this);
        loadingText.setTextColor(Color.WHITE);
        loadingText.setTextSize(14);
        loadingText.setGravity(Gravity.CENTER);
        loadingText.setPadding(0, pad / 2, 0, 0);
        loadingText.setText(Tr.t("ui_csharp_starting", "Starting C#..."));
        box.addView(loadingText);

        root.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    private void showResult(String text) {
        if (closed || isFinishing()) return;
        AlertDialog dialog = Dialogs.longText(this, Tr.t("ui_csharp", "C# / .NET"), text);
        dialog.setOnDismissListener(d -> finish());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!isChangingConfigurations()) closed = true;
    }
}
