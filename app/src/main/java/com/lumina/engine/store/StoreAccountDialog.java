package com.lumina.engine.store;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.lumina.engine.ui.Tr;
import com.lumina.engine.ui.Ui;

/** Sign in / create account / account dialogs for the free Store. */
public final class StoreAccountDialog {
    private StoreAccountDialog() {}

    public static void account(Activity a, Runnable changed) {
        if (!StoreAccount.loggedIn(a)) {
            login(a, changed);
            return;
        }
        LinearLayout col = Ui.column(a);
        col.setPadding(Ui.dp(a, 20), Ui.dp(a, 8), Ui.dp(a, 20), 0);
        TextView email = Ui.text(a, StoreAccount.email(a), 15, Ui.TEXT);
        col.addView(email, Ui.matchWrap());
        TextView note = Ui.text(a, T("store_account_note", "You can publish free assets and manage your uploads."), 12.5f, Ui.TEXT_3);
        note.setPadding(0, Ui.dp(a, 8), 0, 0);
        col.addView(note, Ui.matchWrap());
        TextView signOut = Ui.text(a, T("store_sign_out", "Sign out"), 13, Ui.DANGER);
        signOut.setPadding(0, Ui.dp(a, 16), 0, Ui.dp(a, 4));
        col.addView(signOut);
        AlertDialog d = new AlertDialog.Builder(a)
                .setTitle(T("store_account", "Store account"))
                .setView(col)
                .setNegativeButton(T("close", "Close"), null)
                .create();
        signOut.setOnClickListener(v -> {
            StoreAccount.logout(a);
            d.dismiss();
            if (changed != null) changed.run();
        });
        d.show();
    }

    public static void login(Activity a, Runnable done) {
        LinearLayout col = Ui.column(a);
        col.setPadding(Ui.dp(a, 20), Ui.dp(a, 6), Ui.dp(a, 20), 0);
        EditText email = Ui.input(a, StoreAccount.email(a), T("store_email", "Email"));
        email.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText pass = Ui.input(a, "", T("store_password", "Password"));
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        TextView status = Ui.text(a, "", 12.5f, Ui.DANGER);
        col.addView(email, Ui.matchWrap());
        col.addView(pass, Ui.margins(Ui.matchWrap(), a, 0, 8, 0, 0));
        col.addView(status);
        TextView reg = Ui.text(a, T("store_create_account", "Create an account"), 13, Ui.ACCENT);
        reg.setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, 4));
        col.addView(reg);
        AlertDialog d = new AlertDialog.Builder(a)
                .setTitle(T("store_sign_in", "Sign in to publish"))
                .setView(col)
                .setPositiveButton(T("store_sign_in", "Sign in"), null)
                .setNegativeButton(T("cancel", "Cancel"), null)
                .create();
        reg.setOnClickListener(v -> {
            d.dismiss();
            register(a, email.getText().toString().trim(), done);
        });
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String em = email.getText().toString().trim();
            String pw = pass.getText().toString();
            if (em.isEmpty() || pw.isEmpty()) {
                status.setTextColor(Ui.DANGER);
                status.setText(T("store_fill_email_pass", "Enter your email and password."));
                return;
            }
            status.setTextColor(Ui.TEXT_3);
            status.setText(T("store_signing_in", "Signing in..."));
            setEnabled(d, false);
            StoreAccount.login(a, em, pw, err -> {
                if (err != null) {
                    setEnabled(d, true);
                    status.setTextColor(Ui.DANGER);
                    status.setText(friendly(err));
                    return;
                }
                d.dismiss();
                if (done != null) done.run();
            });
        }));
        d.show();
    }

    private static void register(Activity a, String emailValue, Runnable done) {
        LinearLayout col = Ui.column(a);
        col.setPadding(Ui.dp(a, 20), Ui.dp(a, 6), Ui.dp(a, 20), 0);
        EditText email = Ui.input(a, emailValue == null ? "" : emailValue, T("store_email", "Email"));
        email.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText pass = Ui.input(a, "", T("store_password", "Password"));
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText pass2 = Ui.input(a, "", T("store_password_confirm", "Confirm password"));
        pass2.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        TextView status = Ui.text(a, "", 12.5f, Ui.DANGER);
        col.addView(email, Ui.matchWrap());
        col.addView(pass, Ui.margins(Ui.matchWrap(), a, 0, 8, 0, 0));
        col.addView(pass2, Ui.margins(Ui.matchWrap(), a, 0, 8, 0, 0));
        col.addView(status);
        TextView back = Ui.text(a, T("store_have_account", "I already have an account"), 13, Ui.ACCENT);
        back.setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, 4));
        col.addView(back);
        AlertDialog d = new AlertDialog.Builder(a)
                .setTitle(T("store_create_account", "Create an account"))
                .setView(col)
                .setPositiveButton(T("store_create_account", "Create account"), null)
                .setNegativeButton(T("cancel", "Cancel"), null)
                .create();
        back.setOnClickListener(v -> {
            d.dismiss();
            login(a, done);
        });
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String em = email.getText().toString().trim();
            String pw = pass.getText().toString();
            String pw2 = pass2.getText().toString();
            if (em.isEmpty() || pw.isEmpty()) {
                status.setTextColor(Ui.DANGER);
                status.setText(T("store_fill_email_pass", "Enter your email and password."));
                return;
            }
            if (pw.length() < 8) {
                status.setTextColor(Ui.DANGER);
                status.setText(T("store_password_short", "Use at least 8 characters."));
                return;
            }
            if (!pw.equals(pw2)) {
                status.setTextColor(Ui.DANGER);
                status.setText(T("store_password_mismatch", "Passwords do not match."));
                return;
            }
            status.setTextColor(Ui.TEXT_3);
            status.setText(T("store_creating", "Creating your account..."));
            setEnabled(d, false);
            StoreAccount.register(a, em, pw, err -> {
                if (err != null) {
                    setEnabled(d, true);
                    status.setTextColor(Ui.DANGER);
                    status.setText(friendly(err));
                    return;
                }
                d.dismiss();
                if (done != null) done.run();
            });
        }));
        d.show();
    }

    private static void setEnabled(AlertDialog d, boolean enabled) {
        try {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(enabled);
            d.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(enabled);
        } catch (Exception ignored) {
        }
    }

    private static String friendly(String err) {
        if ("invalid_credentials".equals(err)) return T("store_bad_credentials", "Wrong email or password.");
        if ("email_already_registered".equals(err)) return T("store_email_taken", "That email is already registered.");
        if ("daily_upload_limit".equals(err)) return T("store_daily_limit", "Daily upload limit reached.");
        if ("unauthorized".equals(err)) return T("store_sign_in_required", "Sign in to continue.");
        return err;
    }

    private static String T(String key, String def) {
        return Tr.t(key, def);
    }
}
