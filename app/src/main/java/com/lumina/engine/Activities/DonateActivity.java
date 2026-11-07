package com.lumina.engine.Activities;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.ProductDetails;
import com.lumina.engine.ui.CommunityLinks;
import com.lumina.engine.ui.StarfieldView;
import com.lumina.engine.ui.Tr;
import com.lumina.engine.ui.Ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/** Support page: Google Play support and community links. */
public class DonateActivity extends AppCompatActivity implements PurchasesUpdatedListener {

    private static final String TAG = "DonateActivity";
    private static final int PINK = 0xFFFF5C8D, PURPLE = 0xFF7C4DFF, CARD = 0x33FFFFFF;

    private BillingClient billingClient;
    private TextView tvStatus;
    private View playCard;
    private StarfieldView sky;
    private final Handler main = new Handler(Looper.getMainLooper());
    private float d;

    private final String ID_1 = "donation_1_usd";
    private final String ID_3 = "donation_3_usd";
    private final String ID_5 = "donation_5_usd";

    private final List<String> productIds = new ArrayList<>();
    private final HashMap<String, ProductDetails> productDetailsMap = new HashMap<>();

    private static String T(String k, String def) { return Tr.t(k, def); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tr.init(this);
        d = getResources().getDisplayMetrics().density;
        buildUi();

        productIds.add(ID_1);
        productIds.add(ID_3);
        productIds.add(ID_5);
        setupBillingClient();
    }

    @Override protected void onPause() { if (sky != null) sky.pause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if (sky != null) sky.resume(); }

    // ------------------------------------------------------------------ UI

    private int dp(float v) { return Math.round(v * d); }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setLayoutDirection(Ui.isRtl(this) ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        sky = new StarfieldView(this);
        root.addView(sky, new FrameLayout.LayoutParams(-1, -1));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(12), dp(16), dp(28));
        scroll.addView(col, new ViewGroup.LayoutParams(-1, -2));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, in) -> {
            Insets bars = in.getInsets(WindowInsetsCompat.Type.systemBars());
            scroll.setPadding(0, bars.top, 0, bars.bottom);
            return in;
        });

        List<View> animated = new ArrayList<>();

        // top bar
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("\u2190", 24, Color.WHITE, true);
        back.setGravity(Gravity.CENTER);
        back.setBackground(ripple(0x22FFFFFF, 22));
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        col.addView(bar);

        // hero
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER);
        hero.setPadding(0, dp(6), 0, dp(14));
        TextView heart = text("\u2764\uFE0F", 58, PINK, false);
        heart.setGravity(Gravity.CENTER);
        hero.addView(heart, new LinearLayout.LayoutParams(-2, -2));
        ObjectAnimator sx = ObjectAnimator.ofFloat(heart, View.SCALE_X, 1f, 1.18f, 1f, 1.1f, 1f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(heart, View.SCALE_Y, 1f, 1.18f, 1f, 1.1f, 1f);
        for (ObjectAnimator o : new ObjectAnimator[]{sx, sy}) {
            o.setDuration(1500); o.setRepeatCount(ObjectAnimator.INFINITE); o.start();
        }
        TextView title = text(T("support_title", "Support Lumina Engine"), 26, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        hero.addView(title);
        TextView sub = text(T("support_sub", "Your support keeps the engine growing. Thank you for being part of the journey!"), 14, 0xFFC9C9E6, false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(dp(12), dp(6), dp(12), 0);
        hero.addView(sub);
        col.addView(hero);
        animated.add(hero);

        // Google Play card (hidden if billing is unavailable, e.g. APK installed outside Play)
        LinearLayout play = card();
        playCard = play;
        play.setVisibility(View.GONE);
        play.addView(sectionTitle("\uD83C\uDF81  " + T("support_play_title", "Donate via Google Play")));
        LinearLayout amounts = new LinearLayout(this);
        amounts.setOrientation(LinearLayout.HORIZONTAL);
        String[] ids = {ID_1, ID_3, ID_5};
        String[] labels = {"$1", "$3", "$5"};
        for (int i = 0; i < 3; i++) {
            final String id = ids[i];
            TextView b = button(labels[i], 0xFF4C6FFF, 0xFF7C4DFF);
            b.setOnClickListener(v -> purchaseProduct(id));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
            lp.setMarginEnd(i < 2 ? dp(8) : 0);
            amounts.addView(b, lp);
        }
        LinearLayout.LayoutParams amLp = new LinearLayout.LayoutParams(-1, -2);
        amLp.topMargin = dp(12);
        play.addView(amounts, amLp);
        tvStatus = text("", 12, 0xFFC9C9E6, false);
        tvStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(-1, -2);
        stLp.topMargin = dp(8);
        play.addView(tvStatus, stLp);
        col.addView(play, cardLp());
        animated.add(play);

        // community
        LinearLayout comm = card();
        comm.addView(sectionTitle("\uD83C\uDF0D  " + T("home_tab_community", "Community")));
        for (String[] link : CommunityLinks.all()) {
            TextView row = text(link[4] + "   " + T("home_c_" + link[0], link[1]), 15, Color.WHITE, true);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(ripple(0x22FFFFFF, 12));
            row.setPadding(dp(12), 0, dp(12), 0);
            row.setOnClickListener(v -> openUrl(link[3]));
            attachPress(row);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
            lp.topMargin = dp(8);
            comm.addView(row, lp);
        }
        col.addView(comm, cardLp());
        animated.add(comm);

        // staggered entrance
        for (int i = 0; i < animated.size(); i++) {
            View v = animated.get(i);
            v.setAlpha(0f);
            v.setTranslationY(dp(50));
            v.animate().alpha(1f).translationY(0).setStartDelay(120L + i * 110L).setDuration(520)
                    .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, T("home_no_app", "No app found to open the link."), Toast.LENGTH_SHORT).show();
        }
    }

    // -- small view helpers

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private TextView sectionTitle(String s) { return text(s, 17, Color.WHITE, true); }

    private GradientDrawable rounded(int color, float radius, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius * d);
        if (stroke != 0) g.setStroke(Math.max(1, dp(1)), stroke);
        return g;
    }

    private RippleDrawable ripple(int color, float radius) {
        return new RippleDrawable(android.content.res.ColorStateList.valueOf(0x44FFFFFF), rounded(color, radius, 0), null);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(rounded(CARD, 18, 0x22FFFFFF));
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        return c;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(14);
        return lp;
    }

    private TextView button(String label, int c1, int c2) {
        TextView t = text(label, 15, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{c1, c2});
        g.setCornerRadius(14 * d);
        t.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x55FFFFFF), g, null));
        attachPress(t);
        return t;
    }

    /** Subtle press-down scale so every control feels alive (does not consume the touch). */
    private void attachPress(View v) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
                    break;
            }
            return false;
        });
    }

    private void setupBillingClient() {
        billingClient = BillingClient.newBuilder(this)
            .enablePendingPurchases(com.android.billingclient.api.PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .setListener(this)
            .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    log("Billing setup OK");
                    queryProducts();
                } else {
                    log("Billing setup failed: " + billingResult.getDebugMessage());
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                log("Billing service disconnected. Try reconnect.");
            }
        });
    }

    private void queryProducts() {
        List<QueryProductDetailsParams.Product> productList = new ArrayList<>();
        for (String id : productIds) {
            productList.add(QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build());
        }

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build();

        billingClient.queryProductDetailsAsync(params, (billingResult, queryProductDetailsResult) -> {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            List<ProductDetails> productDetailsList = queryProductDetailsResult.getProductDetailsList();
            if (productDetailsList != null) {
                for (ProductDetails pd : productDetailsList) {
                    productDetailsMap.put(pd.getProductId(), pd);
                }
                runOnUiThread(() -> {
                    if (!productDetailsMap.isEmpty()) playCard.setVisibility(View.VISIBLE);
                    tvStatus.setText("");
                });
            }
        } else log("Failed to fetch product details");
    });
    }

    private void purchaseProduct(String productId) {
        ProductDetails pd = productDetailsMap.get(productId);
        if (pd == null) {
            Toast.makeText(this, "Product details are not available now.", Toast.LENGTH_SHORT).show();
            return;
        }

        ProductDetails.OneTimePurchaseOfferDetails offer = pd.getOneTimePurchaseOfferDetails();
        if (offer == null) {
            Toast.makeText(this, "Purchase offer is not available for this product.", Toast.LENGTH_SHORT).show();
            return;
        }

        BillingFlowParams.ProductDetailsParams productDetailsParams =
                BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(pd)
                        .setOfferToken(offer.getOfferToken())
                        .build();

        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(productDetailsParams))
                .build();

        BillingResult result = billingClient.launchBillingFlow(this, flowParams);
        log("launchBillingFlow result: " + result.getDebugMessage());
    }

    @Override
    public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                handlePurchase(purchase);
            }
        } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
            Toast.makeText(this, "Purchase cancelled", Toast.LENGTH_SHORT).show();
        } else {
            log("onPurchasesUpdated error: " + billingResult.getDebugMessage());
            Toast.makeText(this, "Purchase error: " + billingResult.getDebugMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void handlePurchase(Purchase purchase) {
        // 1) Verify the purchase status locally, then, preferably, verify it on the server using the Google Play Developer API.
        // 2) When the verification is valid, award the user the benefit or record the donation on the server.
        // 3) Consume the product.
        consumePurchase(purchase.getPurchaseToken());
    }

    private void consumePurchase(String purchaseToken) {
        ConsumeParams consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchaseToken)
                .build();

        ConsumeResponseListener listener = (billingResult, outToken) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                log("Consume OK");
                runOnUiThread(() -> tvStatus.setText(T("support_thanks", "Thank you for your donation \u2764\uFE0F")));
            } else {
                log("Consume failed: " + billingResult.getDebugMessage());
                runOnUiThread(() -> tvStatus.setText("Purchase consumption failure: " + billingResult.getDebugMessage()));
            }
        };

        billingClient.consumeAsync(consumeParams, listener);
    }

    private void log(String s) {
        Log.d(TAG, s);
    }

    @Override
    protected void onDestroy() {
        if (billingClient != null && billingClient.isReady()) {
            billingClient.endConnection();
        }
        super.onDestroy();
    }
}