package com.sebtransfer.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final Object CRYPTO_TAG = new Object();
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private static final class Selection {
        Geo.Country country;
        SebPayApi.Operator operator; // null si crypto
        Geo.Crypto crypto;           // null si mobile money
        String number;               // numéro (chiffres) ou adresse crypto
        String otp = "";
    }

    private SharedPreferences prefs;
    private Store store;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private FrameLayout host;
    private View currentPage;
    private boolean splashing;
    private List<Geo.Country> countries;
    private List<SebPayApi.Operator> operators; // null tant que non chargé
    private String operatorsError;
    private String screen = "";

    private EndpointPicker recipientPicker;
    private EndpointPicker senderPicker;
    private EditText recipientName;
    private EditText amountField;
    private Selection toSel;
    private String toName;

    private Dialog progressDialog;
    private TextView progressText;
    private TransferOverlay overlay;

    // ================================================================== Cycle de vie

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Fx.PRIMARY_DARK);
        prefs = getSharedPreferences("sebtransfert", MODE_PRIVATE);
        store = new Store(this);
        countries = Geo.countries();

        root = new FrameLayout(this);
        root.setBackgroundColor(Fx.BG);
        host = new FrameLayout(this);
        root.addView(host, new FrameLayout.LayoutParams(MATCH, MATCH));
        setContentView(root);

        loadOperators();
        splashing = true;
        if (hasProfile()) showHome(0);
        else showProfile(false);

        // Effet d'OUVERTURE : écran animé, puis l'accueil entre en scène
        final SplashView sp = new SplashView(this);
        root.addView(sp, new FrameLayout.LayoutParams(MATCH, MATCH));
        sp.start(() -> {
            splashing = false;
            sp.animate().alpha(0f).scaleX(1.15f).scaleY(1.15f).setDuration(450)
                    .withEndAction(() -> root.removeView(sp)).start();
            if (currentPage != null) enter(currentPage, 0);
        });
    }

    @Override
    public void onBackPressed() {
        if (overlay != null) {
            if (overlay.isFinished()) closeOverlay();
            return; // transfert en cours : on ne quitte pas
        }
        if ("sender".equals(screen)) showHome(-1);
        else if ("settings".equals(screen)) showHome(-1);
        else if ("profile".equals(screen) && hasProfile()) showSettings(-1);
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        hideProgress();
        if (overlay != null) overlay.dismiss();
        io.shutdown();
        super.onDestroy();
    }

    private boolean hasProfile() {
        return !prefs.getString("nom", "").isEmpty();
    }

    // ================================================================== Chargement des réseaux

    private void loadOperators() {
        io.execute(() -> {
            try {
                final List<SebPayApi.Operator> list = SebPayApi.getOperators(null);
                ui.post(() -> {
                    operators = list;
                    operatorsError = null;
                    if ("loading".equals(screen)) showHome(0);
                });
            } catch (SebPayApi.ApiException e) {
                ui.post(() -> {
                    operatorsError = e.getMessage();
                    if ("loading".equals(screen)) showLoading();
                });
            }
        });
    }

    private void showLoading() {
        screen = "loading";
        LinearLayout col = column();
        col.addView(title("Transfert d'argent"));
        if (operatorsError == null) {
            ProgressBar pb = new ProgressBar(this);
            pb.setIndeterminateTintList(ColorStateList.valueOf(Fx.PRIMARY));
            col.addView(pb);
            col.addView(muted("Chargement des réseaux disponibles…"));
        } else {
            col.addView(muted("Impossible de charger les réseaux : " + operatorsError));
            col.addView(button("Réessayer", v -> {
                operatorsError = null;
                showLoading();
                loadOperators();
            }));
        }
        setScreen(col, hasProfile(), 0);
    }

    // ================================================================== Profil

    private void showProfile(final boolean editing) {
        screen = "profile";
        LinearLayout col = column();
        if (editing) {
            col.addView(topBar("Mon profil", () -> showSettings(-1)));
        } else {
            col.addView(hero());
        }

        final LinearLayout card = card();
        final EditText nom = field("Nom", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final EditText prenom = field("Prénom", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final EditText adresse = field("Adresse", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        final EditText numero = field("Numéro (téléphone ou adresse crypto)", InputType.TYPE_CLASS_TEXT);
        nom.setText(prefs.getString("nom", ""));
        prenom.setText(prefs.getString("prenom", ""));
        adresse.setText(prefs.getString("adresse", ""));
        numero.setText(prefs.getString("numero", ""));
        card.addView(label("Nom"));
        card.addView(nom);
        card.addView(label("Prénom"));
        card.addView(prenom);
        card.addView(label("Adresse"));
        card.addView(adresse);
        card.addView(label("Numéro"));
        card.addView(numero);
        col.addView(card);

        col.addView(button("Enregistrer", v -> {
            if (nom.getText().toString().trim().isEmpty() || prenom.getText().toString().trim().isEmpty()
                    || adresse.getText().toString().trim().isEmpty() || numero.getText().toString().trim().isEmpty()) {
                toast("Veuillez remplir tous les champs.");
                Fx.shake(card);
                return;
            }
            prefs.edit()
                    .putString("nom", nom.getText().toString().trim())
                    .putString("prenom", prenom.getText().toString().trim())
                    .putString("adresse", adresse.getText().toString().trim())
                    .putString("numero", numero.getText().toString().trim())
                    .apply();
            if (editing) showSettings(-1);
            else showHome(1);
        }));
        if (editing) col.addView(link("Annuler", v -> showSettings(-1)));
        setScreen(col, false, 0);
    }

    private String profileName() {
        return (prefs.getString("prenom", "") + " " + prefs.getString("nom", "")).trim();
    }

    /** Bandeau de bienvenue avec le logo. */
    private View hero() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setGravity(Gravity.CENTER_HORIZONTAL);
        h.setPadding(dp(20), dp(26), dp(20), dp(24));
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Fx.PRIMARY_DARK, Fx.PRIMARY});
        hg.setCornerRadius(dp(26));
        h.setBackground(hg);
        h.addView(logo(), new LinearLayout.LayoutParams(dp(72), dp(72)));
        TextView t = text("Bienvenue", 24, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(14), 0, dp(4));
        h.addView(t);
        TextView s = text("Renseignez vos informations. Elles restent sur ce téléphone.", 14, Fx.ACCENT_SOFT, false);
        s.setGravity(Gravity.CENTER);
        h.addView(s);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.bottomMargin = dp(8);
        h.setLayoutParams(lp);
        return h;
    }

    private View logo() {
        return new View(this) {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(Canvas c) {
                float cx = getWidth() / 2f;
                float cy = getHeight() / 2f;
                float r = Math.min(getWidth(), getHeight()) / 2f;
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.WHITE);
                c.drawCircle(cx, cy, r, p);
                p.setColor(Fx.PRIMARY);
                Fx.drawSwap(c, cx, cy, r, p);
            }
        };
    }

    // ================================================================== En-tête : nom de l'utilisateur + Paramètres

    private View header() {
        LinearLayout bar = new LinearLayout(this);
        bar.setTag("hdr");
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(18), dp(16), dp(14), dp(22));
        float r = dp(28);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Fx.PRIMARY_DARK, Fx.PRIMARY});
        g.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
        bar.setBackground(g);
        bar.setElevation(dp(6));

        TextView av = text(initials(profileName()), 18, Fx.PRIMARY, true);
        av.setGravity(Gravity.CENTER);
        av.setBackground(Fx.circle(Color.WHITE));
        bar.addView(av, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(12), 0, dp(8), 0);
        names.addView(text("Bonjour", 12, Color.argb(190, 255, 255, 255), false));
        TextView nm = text(profileName(), 18, Color.WHITE, true);
        nm.setSingleLine(true);
        nm.setEllipsize(TextUtils.TruncateAt.END);
        names.addView(nm);
        bar.addView(names, new LinearLayout.LayoutParams(0, WRAP, 1f));

        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(12), dp(9), dp(14), dp(9));
        pill.setBackground(Fx.round(Color.argb(46, 255, 255, 255), dp(22)));
        pill.addView(new IconView(this, IconView.SLIDERS, Color.WHITE), new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView pt = text("Paramètres", 13, Color.WHITE, true);
        pt.setPadding(dp(6), 0, 0, 0);
        pill.addView(pt);
        Fx.pressable(pill);
        pill.setOnClickListener(v -> showSettings(1));
        bar.addView(pill, new LinearLayout.LayoutParams(WRAP, WRAP));
        return bar;
    }

    private View topBar(String ttl, final Runnable back) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout btn = new FrameLayout(this);
        btn.setBackground(Fx.circle(Color.WHITE));
        btn.setElevation(dp(2));
        btn.addView(new IconView(this, IconView.BACK, Fx.PRIMARY), new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        Fx.pressable(btn);
        btn.setOnClickListener(v -> back.run());
        bar.addView(btn, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView t = text(ttl, 22, Fx.TEXT, true);
        t.setPadding(dp(14), 0, 0, 0);
        bar.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.bottomMargin = dp(8);
        bar.setLayoutParams(lp);
        return bar;
    }

    private static String initials(String s) {
        String[] parts = s.trim().split("\\s+");
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty() && b.length() < 2) b.append(Character.toUpperCase(p.charAt(0)));
        }
        return b.length() == 0 ? "?" : b.toString();
    }

    private static String firstWord(String s) {
        String t = s == null ? "" : s.trim();
        int i = t.indexOf(' ');
        return i > 0 ? t.substring(0, i) : t;
    }

    // ================================================================== Paramètres : profil, historique, références

    private void showSettings(int dir) {
        screen = "settings";
        LinearLayout col = column();
        col.addView(topBar("Paramètres", () -> showHome(-1)));

        // Profil
        LinearLayout pc = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView av = text(initials(profileName()), 20, Color.WHITE, true);
        av.setGravity(Gravity.CENTER);
        av.setBackground(Fx.circle(Fx.PRIMARY));
        row.addView(av, new LinearLayout.LayoutParams(dp(58), dp(58)));
        LinearLayout who = new LinearLayout(this);
        who.setOrientation(LinearLayout.VERTICAL);
        who.setPadding(dp(14), 0, 0, 0);
        who.addView(text(profileName(), 18, Fx.TEXT, true));
        who.addView(text(prefs.getString("numero", ""), 13, Fx.MUTED, false));
        who.addView(text(prefs.getString("adresse", ""), 13, Fx.MUTED, false));
        row.addView(who, new LinearLayout.LayoutParams(0, WRAP, 1f));
        pc.addView(row);
        pc.addView(outlineButton("Modifier mon profil", Fx.PRIMARY, v -> showProfile(true)));
        col.addView(pc);

        // Historique
        List<Store.Tx> list = store.all();
        col.addView(section("Historique des transferts (" + list.size() + ")"));
        if (list.isEmpty()) {
            LinearLayout empty = card();
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            FrameLayout badge = new FrameLayout(this);
            badge.setBackground(Fx.circle(Fx.TINT));
            badge.addView(new IconView(this, IconView.CLOCK, Fx.PRIMARY), new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
            empty.addView(badge, new LinearLayout.LayoutParams(dp(52), dp(52)));
            TextView t = muted("Aucun transfert pour le moment. Vos envois, avec leurs références, apparaîtront ici.");
            t.setGravity(Gravity.CENTER);
            empty.addView(t);
            col.addView(empty);
        } else {
            for (Store.Tx t : list) col.addView(historyItem(t));
        }

        // Données
        col.addView(section("Données sur ce téléphone"));
        LinearLayout dc = card();
        dc.addView(muted("Votre profil, l'historique et toutes les références sont enregistrés uniquement sur ce "
                + "téléphone. Si vous effacez les données de l'application (ou si vous la désinstallez), tout est "
                + "supprimé définitivement et ne peut pas être récupéré."));
        dc.addView(outlineButton("Effacer l'historique", Fx.ERR, v -> confirm("Effacer l'historique ?",
                "Tous les transferts et leurs références seront supprimés de ce téléphone. Cette action est définitive.",
                "Effacer", () -> {
                    store.clear();
                    showSettings(0);
                })));
        dc.addView(outlineButton("Effacer toutes mes données", Fx.ERR, v -> confirm("Tout effacer ?",
                "Votre profil et l'historique seront supprimés de ce téléphone. Vous devrez recréer votre profil.",
                "Tout effacer", () -> {
                    store.clear();
                    prefs.edit().clear().apply();
                    resetForm();
                    showProfile(false);
                })));
        col.addView(dc);
        setScreen(col, false, dir);
    }

    private View historyItem(final Store.Tx t) {
        final LinearLayout card = card();
        int color = statusColor(t.status);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout dot = new FrameLayout(this);
        dot.setBackground(Fx.circle(color));
        dot.addView(new IconView(this, statusIcon(t.status), Color.WHITE), new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        top.addView(dot, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(dp(12), 0, dp(8), 0);
        TextView n = text(t.toName.isEmpty() ? "—" : t.toName, 15, Fx.TEXT, true);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        mid.addView(n);
        mid.addView(text(t.toOperator + " • " + dateStr(t.time), 12, Fx.MUTED, false));
        top.addView(mid, new LinearLayout.LayoutParams(0, WRAP, 1f));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        TextView amount = text(fmt(t.amount) + " " + t.currency, 15, Fx.TEXT, true);
        amount.setGravity(Gravity.END);
        right.addView(amount);
        TextView chip = text(statusLabel(t.status), 12, color, true);
        chip.setGravity(Gravity.END);
        right.addView(chip);
        top.addView(right, new LinearLayout.LayoutParams(WRAP, WRAP));

        final IconView chevron = new IconView(this, IconView.CHEVRON, Fx.MUTED);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(dp(20), dp(20));
        cl.leftMargin = dp(6);
        top.addView(chevron, cl);
        card.addView(top);

        final LinearLayout detail = new LinearLayout(this);
        detail.setOrientation(LinearLayout.VERTICAL);
        detail.setPadding(0, dp(12), 0, 0);
        detail.setVisibility(View.GONE);
        if (!t.note.isEmpty()) detailRow(detail, "Statut", t.note, false);
        if (!t.ref.isEmpty()) detailRow(detail, "Réf. versement", t.ref, true);
        if (!t.refC.isEmpty()) detailRow(detail, "Réf. paiement", t.refC, true);
        if (!t.txId.isEmpty() && !t.txId.equals(t.ref)) detailRow(detail, "ID SebPay", t.txId, true);
        detailRow(detail, "Bénéficiaire", t.toName + "\n" + t.toOperator + " — " + t.toCountry + "\n+" + t.toNumber, false);
        detailRow(detail, "Expéditeur", t.fromOperator + " — " + t.fromCountry + "\n+" + t.fromNumber, false);
        detailRow(detail, "Date", dateStr(t.time), false);
        if (t.collected && !Store.SUCCESS.equals(t.status)) {
            detailRow(detail, "Important", "L'argent a été encaissé chez l'expéditeur. En cas de souci, "
                    + "contactez le support SebPay avec la référence de paiement.", false);
        }
        card.addView(detail);

        Fx.pressable(card);
        card.setOnClickListener(v -> {
            if (detail.getVisibility() == View.VISIBLE) {
                detail.setVisibility(View.GONE);
                chevron.animate().setStartDelay(0).rotation(0f).setDuration(220).start();
            } else {
                Fx.reveal(detail);
                chevron.animate().setStartDelay(0).rotation(180f).setDuration(220).start();
            }
        });
        return card;
    }

    private void detailRow(LinearLayout parent, String lab, final String value, boolean copyable) {
        View line = new View(this);
        line.setBackgroundColor(Color.parseColor("#E8EEF0"));
        parent.addView(line, new LinearLayout.LayoutParams(MATCH, dp(1)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(9), 0, dp(9));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(lab, 12, Fx.MUTED, false));
        texts.addView(text(value, 14, Fx.TEXT, true));
        row.addView(texts, new LinearLayout.LayoutParams(0, WRAP, 1f));
        if (copyable) {
            row.addView(new IconView(this, IconView.COPY, Fx.PRIMARY), new LinearLayout.LayoutParams(dp(22), dp(22)));
            row.setOnClickListener(v -> copyText(value));
        }
        parent.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    private void copyText(String value) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Référence", value));
            Toast.makeText(this, "Référence copiée", Toast.LENGTH_SHORT).show();
        }
    }

    private static int statusColor(String s) {
        if (Store.SUCCESS.equals(s)) return Fx.OK;
        if (Store.FAILED.equals(s)) return Fx.ERR;
        return Fx.WARN;
    }

    private static int statusIcon(String s) {
        if (Store.SUCCESS.equals(s)) return IconView.CHECK;
        if (Store.FAILED.equals(s)) return IconView.CROSS;
        return IconView.CLOCK;
    }

    private static String statusLabel(String s) {
        if (Store.SUCCESS.equals(s)) return "Réussi";
        if (Store.FAILED.equals(s)) return "Échoué";
        return "En cours";
    }

    private String dateStr(long ms) {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(new Date(ms));
    }

    // ================================================================== Écran 1 : accueil (destinataire)

    private void showHome(int dir) {
        if (operators == null) {
            showLoading();
            return;
        }
        screen = "home";
        LinearLayout col = column();
        col.addView(title("Nouveau transfert"));
        col.addView(muted("Choisissez où l'argent sera reçu."));

        if (recipientPicker == null) {
            recipientName = field("Nom complet du bénéficiaire", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            recipientPicker = new EndpointPicker(this, false);
        }
        detach(recipientName);
        detach(recipientPicker);
        LinearLayout card = card();
        card.addView(cardTitle("Destinataire"));
        card.addView(recipientName);
        card.addView(recipientPicker);
        col.addView(card);

        col.addView(button("Continuer", v -> {
            String n = recipientName.getText().toString().trim();
            if (n.length() < 3) {
                toast("Saisissez le nom complet du bénéficiaire.");
                Fx.shake(recipientName);
                return;
            }
            String err = recipientPicker.validate();
            if (err != null) {
                toast(err);
                Fx.shake(recipientPicker);
                return;
            }
            toName = n;
            toSel = recipientPicker.selection();
            showSender();
        }));
        setScreen(col, true, dir);
    }

    // ================================================================== Écran 2 : qui paie + montant

    private void showSender() {
        screen = "sender";
        LinearLayout col = column();
        col.addView(topBar("Qui paie ?", () -> showHome(-1)));

        // Rappel du destinataire
        LinearLayout rc = card();
        LinearLayout rrow = new LinearLayout(this);
        rrow.setOrientation(LinearLayout.HORIZONTAL);
        rrow.setGravity(Gravity.CENTER_VERTICAL);
        TextView rav = text(initials(toName), 15, Color.WHITE, true);
        rav.setGravity(Gravity.CENTER);
        rav.setBackground(Fx.circle(Fx.PRIMARY));
        rrow.addView(rav, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout rt = new LinearLayout(this);
        rt.setOrientation(LinearLayout.VERTICAL);
        rt.setPadding(dp(12), 0, 0, 0);
        rt.addView(text("Vers", 11, Fx.MUTED, true));
        rt.addView(text(toName, 16, Fx.TEXT, true));
        rt.addView(text(networkLabel(toSel) + " — " + toSel.country.name, 12, Fx.MUTED, false));
        rrow.addView(rt, new LinearLayout.LayoutParams(0, WRAP, 1f));
        rc.addView(rrow);
        col.addView(rc);

        if (senderPicker == null) {
            senderPicker = new EndpointPicker(this, true);
            amountField = field("Montant que le bénéficiaire reçoit", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            amountField.setTextSize(26);
            amountField.setTypeface(Typeface.DEFAULT_BOLD);
            amountField.setGravity(Gravity.CENTER);
        }
        detach(senderPicker);
        detach(amountField);

        LinearLayout sc = card();
        sc.addView(cardTitle("Expéditeur"));
        sc.addView(muted("Le numéro (ou portefeuille) qui sera débité."));
        sc.addView(senderPicker);
        col.addView(sc);

        LinearLayout ac = card();
        ac.addView(cardTitle("Montant"));
        ac.addView(amountField);
        String cur = toSel.country.currency == null ? "?" : toSel.country.currency;
        ac.addView(muted("Devise : " + cur + " (pays du destinataire)"));
        col.addView(ac);

        col.addView(button("Vérifier et continuer", v -> onVerify()));
        col.addView(link("Retour", v -> showHome(-1)));
        setScreen(col, true, 1);
    }

    private static String networkLabel(Selection s) {
        if (s.operator != null) return s.operator.name;
        if (s.crypto != null) return s.crypto.label;
        return "";
    }

    private void onVerify() {
        String err = senderPicker.validate();
        if (err != null) {
            toast(err);
            Fx.shake(senderPicker);
            return;
        }
        final double amount;
        try {
            amount = Double.parseDouble(amountField.getText().toString().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            toast("Montant invalide.");
            Fx.shake(amountField);
            return;
        }
        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0) {
            toast("Le montant doit être supérieur à 0.");
            Fx.shake(amountField);
            return;
        }

        final Selection from = senderPicker.selection();
        final Selection to = toSel;
        final String name = toName;

        String problem = staticCheck(from, to);
        if (problem != null) {
            alert("Transfert impossible", problem);
            return;
        }

        // Vérification en direct auprès de SebPay avant tout opérateur
        showProgress("Vérification de la disponibilité des réseaux…");
        io.execute(() -> {
            String p;
            try {
                p = liveCheck(from.country.code, from.operator.slug, "l'expéditeur");
                if (p == null) p = liveCheck(to.country.code, to.operator.slug, "le destinataire");
            } catch (SebPayApi.ApiException e) {
                p = "Vérification impossible : " + e.getMessage();
            }
            final String result = p;
            ui.post(() -> {
                hideProgress();
                if (result != null) alert("Transfert impossible", result);
                else showSummary(from, to, name, amount);
            });
        });
    }

    /** Contrôles qui ne nécessitent pas de réseau. */
    private String staticCheck(Selection from, Selection to) {
        if (to.crypto != null) {
            return "Réception en crypto-monnaie (" + to.crypto.label + ") : la documentation SebPay fournie "
                    + "ne contient aucun service crypto. Ce transfert n'est pas encore activable.";
        }
        if (from.crypto != null) {
            return "Paiement depuis un portefeuille crypto : non disponible avec l'API SebPay actuelle.";
        }
        if (!from.operator.active) {
            return "Le réseau " + from.operator.name + " (" + from.country.name + ") est indisponible actuellement.";
        }
        if (!to.operator.active) {
            return "Le réseau " + to.operator.name + " (" + to.country.name + ") est indisponible actuellement.";
        }
        String c1 = from.country.currency;
        String c2 = to.country.currency;
        if (c1 == null || c2 == null || !c1.equals(c2)) {
            return "Les devises sont différentes (" + c1 + " → " + c2 + "). L'API SebPay ne fournit pas de taux de change : "
                    + "seuls les transferts entre pays de même devise (ex. zone XOF) sont possibles pour l'instant.";
        }
        return null;
    }

    private String liveCheck(String countryCode, String slug, String who) throws SebPayApi.ApiException {
        List<SebPayApi.Operator> list = SebPayApi.getOperators(countryCode);
        for (SebPayApi.Operator op : list) {
            if (op.slug.equals(slug)) {
                return op.active ? null : "Le réseau choisi pour " + who + " est actuellement indisponible.";
            }
        }
        return "Le réseau choisi pour " + who + " n'est plus proposé par SebPay.";
    }

    // ================================================================== Récapitulatif

    private void showSummary(final Selection from, final Selection to, final String name, final double amount) {
        if (isFinishing()) return;
        String cur = to.country.currency;
        LinearLayout box = dialogBox();
        TextView t = text("Récapitulatif", 20, Fx.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 0, 0, dp(10));
        box.addView(t);

        box.addView(party("EXPÉDITEUR", profileName(),
                from.operator.name + " — " + from.country.name, "+" + from.number, Fx.PRIMARY));
        FrameLayout arrow = new FrameLayout(this);
        arrow.setBackground(Fx.circle(Fx.TINT));
        arrow.addView(new IconView(this, IconView.ARROW_DOWN, Fx.PRIMARY), new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(dp(34), dp(34));
        al.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(arrow, al);
        box.addView(party("BÉNÉFICIAIRE", name, to.operator.name + " — " + to.country.name, "+" + to.number, Fx.OK));

        LinearLayout amt = new LinearLayout(this);
        amt.setOrientation(LinearLayout.VERTICAL);
        amt.setGravity(Gravity.CENTER_HORIZONTAL);
        amt.setPadding(dp(12), dp(14), dp(12), dp(14));
        amt.setBackground(Fx.round(Fx.TINT, dp(16)));
        TextView a = text(fmt(amount) + " " + cur, 28, Fx.PRIMARY, true);
        a.setGravity(Gravity.CENTER);
        amt.addView(a);
        TextView am = text("Montant reçu par le bénéficiaire", 12, Fx.MUTED, false);
        am.setGravity(Gravity.CENTER);
        amt.addView(am);
        LinearLayout.LayoutParams amp = new LinearLayout.LayoutParams(MATCH, WRAP);
        amp.topMargin = dp(12);
        box.addView(amt, amp);

        final Dialog[] ref = new Dialog[1];
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.addView(dlgButton("Annuler", false, v -> ref[0].dismiss()));
        btns.addView(dlgButton("Envoyer", true, v -> {
            ref[0].dismiss();
            startTransfer(from, to, name, amount);
        }));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(MATCH, WRAP);
        bl.topMargin = dp(16);
        box.addView(btns, bl);

        ref[0] = dialogOf(box, false);
        ref[0].show();
    }

    private View party(String role, String name, String net, String num, int color) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(6), 0, dp(6));
        TextView av = text(initials(name), 15, Color.WHITE, true);
        av.setGravity(Gravity.CENTER);
        av.setBackground(Fx.circle(color));
        r.addView(av, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(dp(12), 0, 0, 0);
        tx.addView(text(role, 11, Fx.MUTED, true));
        tx.addView(text(name, 16, Fx.TEXT, true));
        tx.addView(text(net + "  •  " + num, 12, Fx.MUTED, false));
        r.addView(tx, new LinearLayout.LayoutParams(0, WRAP, 1f));
        return r;
    }

    // ================================================================== Envoi (écran de progression)

    private void startTransfer(final Selection from, final Selection to, final String name, final double amount) {
        final String currency = to.country.currency;
        final String base = "SEB" + System.currentTimeMillis() + (int) (Math.random() * 900 + 100);
        final String refC = base + "C";
        final String refP = base + "P";

        // Enregistré tout de suite : même si l'appli est fermée en route, la référence reste dans l'historique.
        final Store.Tx tx = new Store.Tx();
        tx.refC = refC;
        tx.ref = refP;
        tx.toName = name;
        tx.toNumber = to.number;
        tx.toOperator = to.operator.name;
        tx.toCountry = to.country.name;
        tx.fromNumber = from.number;
        tx.fromOperator = from.operator.name;
        tx.fromCountry = from.country.name;
        tx.amount = amount;
        tx.currency = currency;
        tx.time = System.currentTimeMillis();
        tx.status = Store.PENDING;
        tx.note = "Demande en cours d'envoi";
        store.save(tx);

        openOverlay(name, amount, currency);
        ovStep(0, "Envoi de la demande de paiement à " + from.operator.name + "…");

        io.execute(() -> {
            boolean collected = false;
            try {
                // 1) Encaissement chez l'expéditeur
                JSONObject c = SebPayApi.createCollection(amount, currency, from.number,
                        from.operator.slug, from.country.code, refC, from.otp);
                String cid = idOf(c, refC);
                final String link = SebPayApi.str(c, "provider_link");
                if (!link.isEmpty()) ui.post(() -> openLink(link));
                tx.note = "Validation du paiement en attente";
                store.save(tx);
                ovStep(1, "Validez le paiement sur le téléphone +" + from.number + "…");

                String st = poll(true, cid, SebPayApi.str(c, "status"), 150_000);
                if (SebPayApi.isRejected(st)) {
                    endTransfer(tx, Store.FAILED, "Paiement refusé ou annulé", TransferOverlay.ERR, "Paiement refusé",
                            "Le paiement a été refusé ou annulé. Aucun transfert n'a été effectué.");
                    return;
                }
                if (!SebPayApi.isApproved(st)) {
                    endTransfer(tx, Store.PENDING, "Paiement non confirmé", TransferOverlay.WAIT, "Paiement en attente",
                            "Le paiement n'a pas encore été confirmé. Ne recommencez pas tout de suite : "
                                    + "vérifiez d'abord votre solde.");
                    return;
                }
                collected = true;
                tx.collected = true;
                tx.note = "Paiement reçu, versement en cours";
                store.save(tx);

                // 2) Versement au bénéficiaire
                ovStep(2, "Paiement reçu. Versement au bénéficiaire…");
                JSONObject p = SebPayApi.createPayout(name, to.number, to.operator.slug, to.country.code,
                        amount, currency, refP, "Transfert de " + profileName());
                String pid = idOf(p, refP);
                String ps = poll(false, pid, SebPayApi.str(p, "status"), 90_000);

                if (SebPayApi.isApproved(ps)) {
                    String txId = SebPayApi.str(p, "transaction_id");
                    tx.txId = txId.equals(refP) ? "" : txId;
                    endTransfer(tx, Store.SUCCESS, "Transfert effectué", TransferOverlay.OK, "Transfert réussi",
                            "Vous avez envoyé " + fmt(amount) + " " + currency + " à " + name + ".");
                } else if (SebPayApi.isRejected(ps)) {
                    endTransfer(tx, Store.FAILED, "Versement refusé après encaissement", TransferOverlay.ERR, "Versement refusé",
                            "Votre paiement de " + fmt(amount) + " " + currency
                                    + " a été encaissé mais le versement au bénéficiaire a été refusé. "
                                    + "Contactez le support SebPay avec la référence de paiement.");
                } else {
                    endTransfer(tx, Store.PENDING, "Versement en cours chez l'opérateur", TransferOverlay.WAIT, "Transfert en cours",
                            "Le versement est en cours de traitement chez l'opérateur. "
                                    + "Le statut final peut arriver dans quelques minutes.");
                }
            } catch (SebPayApi.ApiException e) {
                if (collected) {
                    endTransfer(tx, Store.FAILED, "Versement impossible : " + e.getMessage(), TransferOverlay.ERR, "Versement impossible",
                            "Votre paiement de " + fmt(amount) + " " + currency
                                    + " a été encaissé mais le versement a échoué : " + e.getMessage()
                                    + ". Contactez le support SebPay avec la référence de paiement.");
                } else {
                    endTransfer(tx, Store.FAILED, e.getMessage(), TransferOverlay.ERR, "Échec du transfert", e.getMessage());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                endTransfer(tx, Store.PENDING, "Opération interrompue", TransferOverlay.WAIT, "Interrompu",
                        "Opération interrompue. Conservez les références ci-dessous.");
            }
        });
    }

    /** Interroge le statut toutes les 4 s jusqu'à un statut final ou la fin du délai. */
    private String poll(boolean collection, String id, String firstStatus, long maxMs)
            throws SebPayApi.ApiException, InterruptedException {
        String st = firstStatus;
        long end = System.currentTimeMillis() + maxMs;
        while (!SebPayApi.isApproved(st) && !SebPayApi.isRejected(st) && System.currentTimeMillis() < end) {
            Thread.sleep(4000);
            try {
                JSONObject d = collection ? SebPayApi.getCollection(id) : SebPayApi.getPayout(id);
                st = SebPayApi.str(d, "status");
            } catch (SebPayApi.ApiException e) {
                if (e.http == 401 || e.http == 403) throw e;
                // autres erreurs (réseau, 404 transitoire, 429) : on réessaie
            }
        }
        return st;
    }

    private String idOf(JSONObject d, String fallback) {
        String t = SebPayApi.str(d, "transaction_id");
        return t.isEmpty() ? fallback : t;
    }

    private void ovStep(final int s, final String m) {
        ui.post(() -> {
            if (overlay != null) overlay.step(s, m);
        });
    }

    /** Enregistre le résultat dans l'historique puis l'affiche (coche, croix ou attention). */
    private void endTransfer(final Store.Tx tx, String status, String note, final int mode,
                             final String head, final String msg) {
        tx.status = status;
        tx.note = note;
        store.save(tx);
        final String[][] rows = receiptRows(tx, mode);
        ui.post(() -> {
            if (overlay == null) return;
            if (mode == TransferOverlay.OK) {
                overlay.finish(mode, head, msg, rows, "Nouveau transfert",
                        () -> {
                            closeOverlay();
                            resetForm();
                            showHome(-1);
                        },
                        "Voir l'historique",
                        () -> {
                            closeOverlay();
                            resetForm();
                            showSettings(1);
                        });
            } else {
                overlay.finish(mode, head, msg, rows, "Fermer",
                        this::closeOverlay,
                        "Voir l'historique",
                        () -> {
                            closeOverlay();
                            showSettings(1);
                        });
            }
        });
    }

    private String[][] receiptRows(Store.Tx tx, int mode) {
        List<String[]> r = new ArrayList<>();
        r.add(new String[]{"Bénéficiaire", tx.toName});
        r.add(new String[]{mode == TransferOverlay.OK ? "Montant envoyé" : "Montant", fmt(tx.amount) + " " + tx.currency});
        r.add(new String[]{"Réseau", tx.toOperator + " — " + tx.toCountry + "\n+" + tx.toNumber});
        r.add(new String[]{"Date", dateStr(tx.time)});
        r.add(new String[]{"Réf. versement", tx.ref});
        r.add(new String[]{"Réf. paiement", tx.refC});
        if (!tx.txId.isEmpty()) r.add(new String[]{"ID SebPay", tx.txId});
        return r.toArray(new String[0][]);
    }

    private void openOverlay(String name, double amount, String currency) {
        final TransferOverlay o = new TransferOverlay(this);
        overlay = o;
        root.addView(o, new FrameLayout.LayoutParams(MATCH, MATCH));
        String me = prefs.getString("prenom", "");
        o.begin("Transfert en cours", fmt(amount) + " " + currency + " → " + name,
                me.isEmpty() ? "Vous" : me, firstWord(name));
    }

    private void closeOverlay() {
        if (overlay == null) return;
        final TransferOverlay o = overlay;
        overlay = null;
        o.dismiss();
        o.animate().setStartDelay(0).alpha(0f).translationY(dp(30)).setDuration(260)
                .withEndAction(() -> root.removeView(o)).start();
    }

    private void resetForm() {
        recipientPicker = null;
        senderPicker = null;
        recipientName = null;
        amountField = null;
        toSel = null;
        toName = null;
    }

    // ================================================================== Sélecteur pays / réseau / numéro

    private final class EndpointPicker extends LinearLayout {
        private final boolean sender;
        private final Spinner countrySpinner;
        private final TextView netLabel;
        private final RadioGroup networkGroup;
        private final Spinner cryptoSpinner;
        private final View cryptoBox;
        private final TextView info;
        private final EditText number;
        private final EditText otp;
        private Geo.Country country;
        private SebPayApi.Operator operator;
        private boolean cryptoChosen;

        EndpointPicker(Context ctx, boolean sender) {
            super(ctx);
            this.sender = sender;
            setOrientation(VERTICAL);

            addView(label("Pays"));
            countrySpinner = new Spinner(ctx);
            List<String> labels = new ArrayList<>();
            labels.add("— Choisir un pays —");
            for (Geo.Country c : countries) {
                labels.add(c.name + (operatorsFor(c.code).isEmpty() ? "  (crypto uniquement)" : ""));
            }
            ArrayAdapter<String> ad = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item, labels);
            ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            countrySpinner.setAdapter(ad);
            addView(boxed(countrySpinner));

            netLabel = label("Réseau");
            netLabel.setVisibility(GONE);
            addView(netLabel);
            networkGroup = new RadioGroup(ctx);
            networkGroup.setOrientation(VERTICAL);
            networkGroup.setVisibility(GONE);
            addView(networkGroup);

            cryptoSpinner = new Spinner(ctx);
            ArrayAdapter<Geo.Crypto> cad = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item, Geo.CRYPTOS);
            cad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            cryptoSpinner.setAdapter(cad);
            cryptoBox = boxed(cryptoSpinner);
            cryptoBox.setVisibility(GONE);
            addView(cryptoBox);

            info = muted("");
            info.setVisibility(GONE);
            addView(info);

            number = field("Numéro", InputType.TYPE_CLASS_PHONE);
            number.setVisibility(GONE);
            addView(number);

            otp = field("Code OTP reçu de l'opérateur", InputType.TYPE_CLASS_NUMBER);
            otp.setVisibility(GONE);
            addView(otp);

            countrySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                    Geo.Country sel = pos == 0 ? null : countries.get(pos - 1);
                    if (sel != country) {
                        country = sel;
                        rebuildNetworks();
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }

        private void rebuildNetworks() {
            networkGroup.setOnCheckedChangeListener(null);
            networkGroup.removeAllViews();
            networkGroup.clearCheck();
            operator = null;
            cryptoChosen = false;
            cryptoBox.setVisibility(GONE);
            number.setVisibility(GONE);
            otp.setVisibility(GONE);
            info.setVisibility(GONE);
            if (country == null) {
                networkGroup.setVisibility(GONE);
                netLabel.setVisibility(GONE);
                return;
            }
            Fx.reveal(netLabel);
            Fx.reveal(networkGroup);

            List<SebPayApi.Operator> ops = operatorsFor(country.code);
            for (SebPayApi.Operator op : ops) {
                RadioButton rb = radio(op.name + (op.active ? "" : " (indisponible)"));
                rb.setTag(op);
                rb.setEnabled(op.active);
                rb.setAlpha(op.active ? 1f : 0.5f);
                networkGroup.addView(rb);
            }
            RadioButton crypto = radio("Crypto-monnaie");
            crypto.setTag(CRYPTO_TAG);
            networkGroup.addView(crypto);

            networkGroup.setOnCheckedChangeListener((g, id) -> onNetworkChecked(g.findViewById(id)));
            if (ops.isEmpty()) {
                info.setText("Ce pays n'est pas proposé par l'API : seule la crypto-monnaie est disponible.");
                Fx.reveal(info);
                crypto.setChecked(true);
            }
        }

        private void onNetworkChecked(View v) {
            if (v == null) return;
            v.setScaleX(0.94f);
            v.setScaleY(0.94f);
            v.animate().setStartDelay(0).scaleX(1f).scaleY(1f).setDuration(280)
                    .setInterpolator(new OvershootInterpolator(3f)).start();
            Fx.reveal(number);
            if (v.getTag() == CRYPTO_TAG) {
                cryptoChosen = true;
                operator = null;
                Fx.reveal(cryptoBox);
                otp.setVisibility(GONE);
                number.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                number.setHint(sender ? "Adresse du portefeuille qui paie" : "Adresse du portefeuille qui reçoit");
                number.setText("");
                if (country != null && operatorsFor(country.code).isEmpty()) Fx.reveal(info);
                else info.setVisibility(GONE);
            } else {
                cryptoChosen = false;
                operator = (SebPayApi.Operator) v.getTag();
                cryptoBox.setVisibility(GONE);
                number.setInputType(InputType.TYPE_CLASS_PHONE);
                number.setHint("Numéro, format international sans +");
                number.setText(country.dial == null ? "" : country.dial);
                number.setSelection(number.length());
                if (sender && operator.otpRequired) {
                    Fx.reveal(otp);
                    info.setText(operator.ussd.isEmpty()
                            ? "Cet opérateur exige un code OTP : générez-le avec le code USSD de l'opérateur."
                            : "Composez " + operator.ussd + " pour obtenir votre code OTP.");
                    Fx.reveal(info);
                } else {
                    otp.setVisibility(GONE);
                    info.setVisibility(GONE);
                }
            }
        }

        /** Message d'erreur ou null si la saisie est complète et plausible. */
        String validate() {
            if (country == null) return "Choisissez un pays.";
            if (!cryptoChosen && operator == null) return "Choisissez un réseau.";
            if (cryptoChosen) {
                Geo.Crypto cr = (Geo.Crypto) cryptoSpinner.getSelectedItem();
                return cr.validate(number.getText().toString());
            }
            String n = digits(number.getText().toString());
            if (n.length() < 8 || n.length() > 15) return "Numéro invalide (8 à 15 chiffres, format international).";
            if (country.dial != null && !n.startsWith(country.dial)) {
                return "Le numéro doit commencer par l'indicatif +" + country.dial + ".";
            }
            if (sender && operator.otpRequired && otp.getText().toString().trim().isEmpty()) {
                return "Saisissez le code OTP de l'opérateur.";
            }
            return null;
        }

        Selection selection() {
            Selection s = new Selection();
            s.country = country;
            s.operator = operator;
            s.crypto = cryptoChosen ? (Geo.Crypto) cryptoSpinner.getSelectedItem() : null;
            s.number = cryptoChosen ? number.getText().toString().trim() : digits(number.getText().toString());
            s.otp = otp.getText().toString().trim();
            return s;
        }
    }

    private List<SebPayApi.Operator> operatorsFor(String countryCode) {
        List<SebPayApi.Operator> r = new ArrayList<>();
        if (operators != null) {
            for (SebPayApi.Operator o : operators) {
                if (countryCode.equals(o.countryCode)) r.add(o);
            }
        }
        Collections.sort(r, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return r;
    }

    private static String digits(String s) {
        return s.replaceAll("[^0-9]", "");
    }

    // ================================================================== Boîtes de dialogue

    private LinearLayout dialogBox() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(dp(20), dp(20), dp(20), dp(18));
        b.setBackground(Fx.round(Color.WHITE, dp(24)));
        return b;
    }

    /** Fenêtre arrondie qui apparaît avec un petit rebond. */
    private Dialog dialogOf(View content, boolean cancelable) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout wrap = new FrameLayout(this);
        wrap.addView(content, new FrameLayout.LayoutParams(MATCH, WRAP));
        d.setContentView(wrap);
        d.setCancelable(cancelable);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.92f), WRAP);
        }
        content.setScaleX(0.88f);
        content.setScaleY(0.88f);
        content.setAlpha(0f);
        content.animate().setStartDelay(60).scaleX(1f).scaleY(1f).alpha(1f).setDuration(320)
                .setInterpolator(new OvershootInterpolator(1.4f)).start();
        return d;
    }

    private Button dlgButton(String label, boolean filled, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setStateListAnimator(null);
        if (filled) {
            b.setTextColor(Color.WHITE);
            b.setBackground(Fx.round(Fx.PRIMARY, dp(14)));
        } else {
            b.setTextColor(Fx.MUTED);
            b.setBackground(Fx.stroke(Color.WHITE, dp(14), Fx.BORDER, dp(1)));
        }
        Fx.pressable(b);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void showProgress(String msg) {
        hideProgress();
        LinearLayout l = dialogBox();
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        ProgressBar pb = new ProgressBar(this);
        pb.setIndeterminateTintList(ColorStateList.valueOf(Fx.PRIMARY));
        l.addView(pb);
        progressText = text(msg, 15, Fx.TEXT, false);
        progressText.setPadding(dp(16), 0, 0, 0);
        l.addView(progressText, new LinearLayout.LayoutParams(0, WRAP, 1f));
        progressDialog = dialogOf(l, false);
        if (!isFinishing()) progressDialog.show();
    }

    private void hideProgress() {
        if (progressDialog != null) {
            try {
                progressDialog.dismiss();
            } catch (Exception ignored) {
                // fenêtre déjà fermée
            }
            progressDialog = null;
            progressText = null;
        }
    }

    private void alert(String ttl, String msg) {
        notice(ttl, msg, TransferOverlay.ERR);
    }

    private void notice(String ttl, String msg, int mode) {
        if (isFinishing()) return;
        LinearLayout box = dialogBox();
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        int color = mode == TransferOverlay.OK ? Fx.OK : (mode == TransferOverlay.ERR ? Fx.ERR : Fx.WARN);
        int icon = mode == TransferOverlay.OK ? IconView.CHECK : (mode == TransferOverlay.ERR ? IconView.CROSS : IconView.CLOCK);
        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(Fx.circle(color));
        badge.addView(new IconView(this, icon, Color.WHITE), new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER));
        box.addView(badge, new LinearLayout.LayoutParams(dp(60), dp(60)));
        badge.setScaleX(0f);
        badge.setScaleY(0f);
        badge.animate().setStartDelay(180).scaleX(1f).scaleY(1f).setDuration(420)
                .setInterpolator(new OvershootInterpolator(2.2f)).start();
        TextView t = text(ttl, 19, Fx.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(14), 0, 0);
        box.addView(t);
        TextView m = text(msg, 15, Fx.MUTED, false);
        m.setGravity(Gravity.CENTER);
        m.setPadding(0, dp(8), 0, 0);
        box.addView(m);
        final Dialog[] ref = new Dialog[1];
        LinearLayout row = new LinearLayout(this);
        row.addView(dlgButton("OK", true, v -> ref[0].dismiss()));
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(MATCH, WRAP);
        rl.topMargin = dp(18);
        box.addView(row, rl);
        ref[0] = dialogOf(box, true);
        ref[0].show();
    }

    private void confirm(String ttl, String msg, String okLabel, final Runnable onOk) {
        if (isFinishing()) return;
        LinearLayout box = dialogBox();
        TextView t = text(ttl, 19, Fx.TEXT, true);
        box.addView(t);
        TextView m = text(msg, 15, Fx.MUTED, false);
        m.setPadding(0, dp(8), 0, 0);
        box.addView(m);
        final Dialog[] ref = new Dialog[1];
        LinearLayout row = new LinearLayout(this);
        row.addView(dlgButton("Annuler", false, v -> ref[0].dismiss()));
        Button ok = dlgButton(okLabel, true, v -> {
            ref[0].dismiss();
            onOk.run();
        });
        ok.setBackground(Fx.round(Fx.ERR, dp(14)));
        row.addView(ok);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(MATCH, WRAP);
        rl.topMargin = dp(18);
        box.addView(row, rl);
        ref[0] = dialogOf(box, true);
        ref[0].show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private void openLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
            // aucun navigateur disponible
        }
    }

    private String fmt(double v) {
        boolean whole = v == Math.floor(v);
        return String.format(Locale.FRANCE, whole ? "%,.0f" : "%,.2f", v);
    }

    // ================================================================== Pages et transitions

    /** header = bandeau avec le nom de l'utilisateur et le bouton Paramètres. dir : 0 ouverture, 1 avant, -1 retour. */
    private void setScreen(View col, boolean withHeader, int dir) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        if (withHeader) page.addView(header(), new LinearLayout.LayoutParams(MATCH, WRAP));
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(col);
        page.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        host.removeAllViews();
        host.addView(page, new FrameLayout.LayoutParams(MATCH, MATCH));
        currentPage = page;
        if (!splashing) enter(page, dir);
    }

    private void enter(View page, int dir) {
        LinearLayout pg = (LinearLayout) page;
        View hdr = pg.findViewWithTag("hdr");
        ScrollView sv = (ScrollView) pg.getChildAt(pg.getChildCount() - 1);
        ViewGroup col = (ViewGroup) sv.getChildAt(0);
        if (dir == 0) {
            if (hdr != null) {
                hdr.setTranslationY(-dp(110));
                hdr.animate().setStartDelay(0).translationY(0f).setDuration(650)
                        .setInterpolator(new OvershootInterpolator(0.9f)).start();
            }
            Fx.cascade(col, 250);
        } else {
            sv.setAlpha(0f);
            sv.setTranslationX(dir * dp(56));
            sv.animate().setStartDelay(0).alpha(1f).translationX(0f).setDuration(300)
                    .setInterpolator(new DecelerateInterpolator(1.8f)).start();
            Fx.cascade(col, 40);
        }
    }

    private void detach(View v) {
        if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
    }

    // ================================================================== Éléments d'interface

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(18), dp(18), dp(18), dp(28));
        return l;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(16));
        c.setBackground(Fx.round(Color.WHITE, dp(18)));
        c.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(0, dp(8), 0, dp(8));
        c.setLayoutParams(lp);
        return c;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView title(String s) {
        TextView t = text(s, 26, Fx.TEXT, true);
        t.setPadding(0, dp(4), 0, dp(2));
        return t;
    }

    private TextView muted(String s) {
        TextView t = text(s, 14, Fx.MUTED, false);
        t.setPadding(0, dp(4), 0, dp(8));
        return t;
    }

    private TextView label(String s) {
        TextView t = text(s, 13, Fx.MUTED, true);
        t.setPadding(0, dp(12), 0, dp(4));
        return t;
    }

    private TextView section(String s) {
        TextView t = text(s, 17, Fx.PRIMARY, true);
        t.setPadding(dp(2), dp(18), 0, dp(2));
        return t;
    }

    private TextView cardTitle(String s) {
        TextView t = text(s, 17, Fx.PRIMARY, true);
        t.setPadding(0, 0, 0, dp(4));
        return t;
    }

    private RadioButton radio(String s) {
        RadioButton r = new RadioButton(this);
        r.setId(View.generateViewId());
        r.setText(s);
        r.setTextSize(15);
        r.setTextColor(Fx.TEXT);
        r.setButtonTintList(ColorStateList.valueOf(Fx.PRIMARY));
        r.setPadding(dp(10), dp(12), dp(12), dp(12));
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_checked}, Fx.stroke(Fx.TINT, dp(12), Fx.PRIMARY, dp(2)));
        sl.addState(new int[]{}, Fx.stroke(Color.WHITE, dp(12), Fx.BORDER, dp(1)));
        r.setBackground(sl);
        RadioGroup.LayoutParams lp = new RadioGroup.LayoutParams(MATCH, WRAP);
        lp.setMargins(0, dp(4), 0, dp(4));
        r.setLayoutParams(lp);
        return r;
    }

    /** Encadre un Spinner dans un cadre arrondi. */
    private View boxed(View inner) {
        FrameLayout f = new FrameLayout(this);
        f.setBackground(Fx.stroke(Color.WHITE, dp(12), Fx.BORDER, dp(1)));
        f.setPadding(dp(6), dp(2), dp(6), dp(2));
        f.addView(inner, new FrameLayout.LayoutParams(MATCH, dp(48)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(0, dp(4), 0, dp(4));
        f.setLayoutParams(lp);
        return f;
    }

    private EditText field(String hint, int inputType) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(inputType);
        e.setTextSize(16);
        e.setTextColor(Fx.TEXT);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, Fx.stroke(Color.WHITE, dp(12), Fx.PRIMARY, dp(2)));
        sl.addState(new int[]{}, Fx.stroke(Color.WHITE, dp(12), Fx.BORDER, dp(1)));
        e.setBackground(sl);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(0, dp(6), 0, dp(6));
        e.setLayoutParams(lp);
        return e;
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.WHITE);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Fx.PRIMARY, Color.parseColor("#14A394")});
        g.setCornerRadius(dp(14));
        b.setBackground(g);
        b.setStateListAnimator(null);
        b.setElevation(dp(3));
        Fx.pressable(b);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, dp(54));
        lp.setMargins(0, dp(18), 0, dp(6));
        b.setLayoutParams(lp);
        return b;
    }

    private Button outlineButton(String s, int color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(color);
        b.setBackground(Fx.stroke(Color.WHITE, dp(14), color, dp(1)));
        b.setStateListAnimator(null);
        Fx.pressable(b);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, dp(48));
        lp.setMargins(0, dp(12), 0, dp(2));
        b.setLayoutParams(lp);
        return b;
    }

    private TextView link(String s, View.OnClickListener l) {
        TextView t = text(s, 15, Fx.PRIMARY, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(14), 0, dp(14));
        Fx.pressable(t);
        t.setOnClickListener(l);
        return t;
    }
}
