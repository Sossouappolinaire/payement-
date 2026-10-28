package com.sebtransfer.app;

import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Random;

/**
 * Écran plein format affiché PENDANT l'envoi d'un transfert.
 * Effets propres à l'envoi : l'argent circule de l'expéditeur vers SebPay puis vers le bénéficiaire,
 * 4 étapes, pourcentage animé, puis résultat (coche + confettis, croix qui tremble, ou point d'attention).
 */
final class TransferOverlay extends FrameLayout {
    static final int OK = 0;
    static final int ERR = 1;
    static final int WAIT = 2;

    private static final String[] STEPS = {"Demande", "Validation", "Versement", "Terminé"};
    private static final float[] START = {0f, 22f, 58f};
    private static final float[] CEIL = {20f, 56f, 95f};
    private static final float[] RATE = {0.03f, 0.007f, 0.02f};

    private final Handler h = new Handler(Looper.getMainLooper());
    private final float d;

    private TextView title;
    private TextView subtitle;
    private TextView status;
    private TextView hint;
    private TextView pct;
    private FlowView flow;
    private ResultView result;
    private StepperView stepper;
    private BarView bar;
    private LinearLayout barRow;
    private LinearLayout card;
    private Button primary;
    private TextView secondary;

    private int step;
    private float shown;
    private boolean running;
    private boolean finished;
    private boolean resultShown;
    private boolean dismissed;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running || dismissed) return;
            int s = Math.min(step, 2);
            shown += (CEIL[s] - shown) * RATE[s];
            bar.setValue(shown / 100f);
            pct.setText(Math.round(shown) + " %");
            h.postDelayed(this, 80);
        }
    };

    TransferOverlay(Context ctx) {
        super(ctx);
        d = ctx.getResources().getDisplayMetrics().density;
        setClickable(true);
        setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Fx.PRIMARY_DARK, Fx.PRIMARY}));

        ScrollView sv = new ScrollView(ctx);
        sv.setFillViewport(true);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setPadding(dp(20), dp(30), dp(20), dp(26));
        sv.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        title = tv(ctx, 22, Color.WHITE, true, Gravity.CENTER);
        subtitle = tv(ctx, 15, Color.argb(215, 255, 255, 255), false, Gravity.CENTER);
        subtitle.setPadding(0, dp(4), 0, 0);
        col.addView(title, new LinearLayout.LayoutParams(-1, -2));
        col.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout stage = new FrameLayout(ctx);
        flow = new FlowView(ctx);
        stage.addView(flow, new FrameLayout.LayoutParams(-1, -1));
        result = new ResultView(ctx);
        result.setVisibility(GONE);
        stage.addView(result, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(210));
        sp.topMargin = dp(10);
        col.addView(stage, sp);

        stepper = new StepperView(ctx);
        col.addView(stepper, new LinearLayout.LayoutParams(-1, dp(84)));

        barRow = new LinearLayout(ctx);
        barRow.setOrientation(LinearLayout.HORIZONTAL);
        barRow.setGravity(Gravity.CENTER_VERTICAL);
        bar = new BarView(ctx);
        barRow.addView(bar, new LinearLayout.LayoutParams(0, dp(10), 1f));
        pct = tv(ctx, 14, Color.WHITE, true, Gravity.END | Gravity.CENTER_VERTICAL);
        pct.setText("0 %");
        barRow.addView(pct, new LinearLayout.LayoutParams(dp(56), -2));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = dp(6);
        col.addView(barRow, bp);

        status = tv(ctx, 17, Color.WHITE, true, Gravity.CENTER);
        status.setPadding(dp(8), dp(22), dp(8), 0);
        col.addView(status, new LinearLayout.LayoutParams(-1, -2));
        hint = tv(ctx, 13, Color.argb(190, 255, 255, 255), false, Gravity.CENTER);
        hint.setPadding(dp(8), dp(6), dp(8), 0);
        col.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(8), dp(16), dp(8));
        card.setBackground(Fx.round(Color.WHITE, dp(18)));
        card.setElevation(dp(4));
        card.setVisibility(GONE);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = dp(18);
        col.addView(card, cp);

        primary = new Button(ctx);
        primary.setAllCaps(false);
        primary.setTextSize(16);
        primary.setTypeface(Typeface.DEFAULT_BOLD);
        primary.setTextColor(Fx.PRIMARY);
        primary.setBackground(Fx.round(Color.WHITE, dp(14)));
        primary.setStateListAnimator(null);
        primary.setElevation(dp(4));
        primary.setVisibility(GONE);
        Fx.pressable(primary);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(52));
        pp.topMargin = dp(18);
        col.addView(primary, pp);

        secondary = tv(ctx, 15, Color.WHITE, true, Gravity.CENTER);
        secondary.setPadding(0, dp(14), 0, dp(10));
        secondary.setVisibility(GONE);
        col.addView(secondary, new LinearLayout.LayoutParams(-1, -2));
    }

    private int dp(float v) {
        return (int) (v * d + 0.5f);
    }

    private static TextView tv(Context c, float sp, int color, boolean bold, int gravity) {
        TextView t = new TextView(c);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(gravity);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    // ================================================================== API

    /** À appeler une fois l'écran ajouté à la fenêtre. */
    void begin(String head, String sub, String fromLabel, String toLabel) {
        title.setText(head);
        subtitle.setText(sub);
        flow.setLabels(fromLabel, toLabel);
        step = 0;
        shown = 0f;
        running = true;
        stepper.setStep(0);
        flow.setPhase(0);
        h.post(tick);
        setAlpha(0f);
        setTranslationY(dp(40));
        animate().setStartDelay(0).alpha(1f).translationY(0f).setDuration(340)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    /** Passe à l'étape s (0 = demande, 1 = validation chez l'expéditeur, 2 = versement). */
    void step(int s, String msg) {
        if (finished || dismissed) return;
        step = Math.max(0, Math.min(2, s));
        if (shown < START[step]) shown = START[step];
        stepper.setStep(step);
        flow.setPhase(step);
        hint.setText(step == 0 ? "Connexion sécurisée à SebPay"
                : step == 1 ? "Confirmez sur votre téléphone (notification ou code USSD)"
                : "Merci de patienter, ne fermez pas l'application");
        status.setText(msg);
        status.setAlpha(0f);
        status.setTranslationY(dp(8));
        status.animate().setStartDelay(0).alpha(1f).translationY(0f).setDuration(260).start();
    }

    /**
     * Termine : mode OK, ERR ou WAIT. rows = lignes {libellé, valeur} du reçu ;
     * les lignes dont le libellé commence par « Réf » ou « ID » sont copiables.
     */
    void finish(final int mode, final String head, final String msg, final String[][] rows,
                final String primaryLabel, final Runnable onPrimary,
                final String secondaryLabel, final Runnable onSecondary) {
        if (finished || dismissed) return;
        finished = true;
        running = false;
        h.removeCallbacks(tick);
        long delay;
        if (mode == OK) {
            stepper.setStep(3);
            flow.setPhase(3);
            ValueAnimator a = ValueAnimator.ofFloat(shown, 100f);
            a.setDuration(600);
            a.addUpdateListener(v -> {
                shown = (float) v.getAnimatedValue();
                bar.setValue(shown / 100f);
                pct.setText(Math.round(shown) + " %");
            });
            a.start();
            delay = 950;
        } else {
            flow.setPhase(-1);
            delay = 450;
        }
        h.postDelayed(() -> {
            if (!dismissed) showResult(mode, head, msg, rows, primaryLabel, onPrimary, secondaryLabel, onSecondary);
        }, delay);
    }

    /** Vrai quand le résultat est affiché (l'utilisateur peut alors fermer l'écran). */
    boolean isFinished() {
        return resultShown;
    }

    void dismiss() {
        dismissed = true;
        running = false;
        h.removeCallbacksAndMessages(null);
        flow.stop();
        result.stop();
    }

    // ================================================================== Résultat

    private void showResult(int mode, String head, String msg, String[][] rows,
                            String primaryLabel, final Runnable onPrimary,
                            String secondaryLabel, final Runnable onSecondary) {
        resultShown = true;
        flow.stop();
        flow.setVisibility(GONE);
        stepper.setVisibility(GONE);
        barRow.setVisibility(GONE);
        hint.setVisibility(GONE);
        status.setVisibility(GONE);
        result.setVisibility(VISIBLE);
        result.start(mode);

        title.setTextSize(24);
        title.setText(head);
        subtitle.setText(msg);

        card.removeAllViews();
        for (String[] r : rows) addRow(r[0], r[1]);
        if (rows.length > 0) {
            card.setVisibility(VISIBLE);
            card.setAlpha(0f);
            card.setTranslationY(dp(26));
            card.animate().setStartDelay(550).alpha(1f).translationY(0f).setDuration(420)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }

        primary.setText(primaryLabel);
        primary.setOnClickListener(v -> onPrimary.run());
        primary.setVisibility(VISIBLE);
        primary.setAlpha(0f);
        primary.animate().setStartDelay(700).alpha(1f).setDuration(400).start();

        if (secondaryLabel != null && onSecondary != null) {
            secondary.setText(secondaryLabel);
            secondary.setOnClickListener(v -> onSecondary.run());
            secondary.setVisibility(VISIBLE);
        }
    }

    private void addRow(final String label, final String value) {
        Context ctx = getContext();
        if (card.getChildCount() > 0) {
            View line = new View(ctx);
            line.setBackgroundColor(Color.parseColor("#E8EEF0"));
            card.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        }
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(tv(ctx, 12, Fx.MUTED, false, Gravity.START));
        ((TextView) texts.getChildAt(0)).setText(label);
        TextView v = tv(ctx, 15, Fx.TEXT, true, Gravity.START);
        v.setText(value);
        texts.addView(v);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        if (label.startsWith("Réf") || label.startsWith("ID")) {
            row.addView(new IconView(ctx, IconView.COPY, Fx.PRIMARY), new LinearLayout.LayoutParams(dp(22), dp(22)));
            row.setOnClickListener(x -> copy(value));
        }
        card.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void copy(String value) {
        ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Référence", value));
            Toast.makeText(getContext(), "Référence copiée", Toast.LENGTH_SHORT).show();
        }
    }

    // ================================================================== Flux animé : expéditeur → SebPay → bénéficiaire

    static final class FlowView extends View {
        private static final int ERR_SOFT = Color.parseColor("#FCA5A5");

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final ValueAnimator loop = ValueAnimator.ofFloat(0f, 1f);
        private final float d;
        private float f;
        private int phase;
        private String left = "Vous";
        private String right = "Bénéficiaire";

        FlowView(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTextSize(12f * d);
            loop.setDuration(1500);
            loop.setRepeatCount(ValueAnimator.INFINITE);
            loop.setInterpolator(new LinearInterpolator());
            loop.addUpdateListener(a -> {
                f = (float) a.getAnimatedValue();
                invalidate();
            });
        }

        void setLabels(String l, String r) {
            left = l;
            right = r;
            invalidate();
        }

        void setPhase(int ph) {
            phase = ph;
            invalidate();
        }

        void stop() {
            loop.cancel();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!loop.isStarted()) loop.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            loop.cancel();
            super.onDetachedFromWindow();
        }

        /** 0 = en attente, 1 = actif, 2 = terminé. */
        private int segState(int i) {
            if (phase < 0) return 0;
            if (i == 0) return phase <= 1 ? 1 : 2;
            return phase < 2 ? 0 : (phase == 2 ? 1 : 2);
        }

        private int nodeState(int i) {
            if (phase < 0) return 0;
            switch (i) {
                case 0:
                    return phase <= 1 ? 1 : 2;
                case 1:
                    return phase < 2 ? 0 : (phase == 2 ? 1 : 2);
                default:
                    return phase >= 3 ? 2 : 0;
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float h = getHeight();
            float y = h * 0.44f;
            float r = 28f * d;
            float rh = 33f * d;
            float x0 = w * 0.15f;
            float x1 = w * 0.5f;
            float x2 = w * 0.85f;
            int tint = phase < 0 ? ERR_SOFT : Fx.ACCENT;

            line(c, x0 + r, x1 - rh, y, segState(0), tint);
            line(c, x1 + rh, x2 - r, y, segState(1), tint);
            node(c, x0, y, r, nodeState(0), tint, 0);
            node(c, x1, y, rh, nodeState(1), tint, 1);
            node(c, x2, y, r, nodeState(2), tint, 2);

            float lw = w * 0.30f;
            label(c, left, x0, y + r + 24f * d, lw);
            label(c, "SebPay", x1, y + rh + 24f * d, lw);
            label(c, right, x2, y + r + 24f * d, lw);
        }

        private void line(Canvas c, float xa, float xb, float y, int st, int tint) {
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(3f * d);
            p.setColor(st == 2 ? tint : Color.argb(70, 255, 255, 255));
            c.drawLine(xa, y, xb, y, p);
            if (st == 1) {
                p.setStyle(Paint.Style.FILL);
                for (int i = 0; i < 4; i++) {
                    float u = (f + i / 4f) % 1f;
                    float a = (float) Math.sin(Math.PI * u);
                    p.setColor((tint & 0x00FFFFFF) | (((int) (a * 255f)) << 24));
                    c.drawCircle(xa + (xb - xa) * u, y, (2.5f + 3.5f * a) * d, p);
                }
            }
        }

        private void node(Canvas c, float cx, float cy, float r, int st, int tint, int kind) {
            if (st == 1) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2f * d);
                p.setColor((tint & 0x00FFFFFF) | (((int) ((1f - f) * 160f)) << 24));
                c.drawCircle(cx, cy, r + f * 18f * d, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(st == 2 ? tint : Color.argb(st == 1 ? 55 : 26, 255, 255, 255));
            c.drawCircle(cx, cy, r, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f * d);
            p.setColor(st == 0 ? Color.argb(90, 255, 255, 255) : tint);
            c.drawCircle(cx, cy, r, p);
            int g = st == 2 ? Fx.PRIMARY_DARK : Color.WHITE;
            p.setColor(g);
            if (kind == 1) {
                Fx.drawSwap(c, cx, cy, r, p);
            } else {
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy - r * 0.2f, r * 0.26f, p);
                rect.set(cx - r * 0.5f, cy + r * 0.12f, cx + r * 0.5f, cy + r * 1.0f);
                c.drawArc(rect, 180f, 180f, false, p);
            }
        }

        private void label(Canvas c, String s, float cx, float y, float maxW) {
            tp.setColor(Color.argb(215, 255, 255, 255));
            CharSequence t = TextUtils.ellipsize(s, tp, maxW, TextUtils.TruncateAt.END);
            c.drawText(t, 0, t.length(), cx, y, tp);
        }
    }

    // ================================================================== Étapes : Demande · Validation · Versement · Terminé

    static final class StepperView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Path chk = new Path();
        private final ValueAnimator pulse = ValueAnimator.ofFloat(0f, 1f);
        private ValueAnimator move;
        private final float d;
        private float prog;
        private int target;
        private float f;

        StepperView(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTextSize(11.5f * d);
            pulse.setDuration(1300);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.setInterpolator(new LinearInterpolator());
            pulse.addUpdateListener(a -> {
                f = (float) a.getAnimatedValue();
                invalidate();
            });
        }

        void setStep(int s) {
            target = s;
            if (move != null) move.cancel();
            move = ValueAnimator.ofFloat(prog, s);
            move.setDuration(650);
            move.setInterpolator(new DecelerateInterpolator());
            move.addUpdateListener(a -> {
                prog = (float) a.getAnimatedValue();
                invalidate();
            });
            move.start();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!pulse.isStarted()) pulse.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            pulse.cancel();
            if (move != null) move.cancel();
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float padX = w * 0.13f;
            float y = 20f * d;
            float r = 13f * d;
            float gap = (w - 2f * padX) / 3f;

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(3f * d);
            p.setColor(Color.argb(60, 255, 255, 255));
            c.drawLine(padX, y, padX + 3f * gap, y, p);
            p.setColor(Fx.ACCENT);
            c.drawLine(padX, y, padX + prog * gap, y, p);

            for (int i = 0; i < 4; i++) {
                float x = padX + i * gap;
                boolean done = i < target || (target == 3 && i == 3);
                boolean act = i == target && !done;
                if (done) {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(Fx.ACCENT);
                    c.drawCircle(x, y, r, p);
                    chk.reset();
                    chk.moveTo(x - r * 0.42f, y + r * 0.02f);
                    chk.lineTo(x - r * 0.10f, y + r * 0.34f);
                    chk.lineTo(x + r * 0.45f, y - r * 0.30f);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeJoin(Paint.Join.ROUND);
                    p.setStrokeWidth(2.4f * d);
                    p.setColor(Fx.PRIMARY_DARK);
                    c.drawPath(chk, p);
                } else if (act) {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(2f * d);
                    p.setColor((Fx.ACCENT & 0x00FFFFFF) | (((int) ((1f - f) * 170f)) << 24));
                    c.drawCircle(x, y, r + f * 8f * d, p);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(Color.argb(45, 255, 255, 255));
                    c.drawCircle(x, y, r, p);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(2f * d);
                    p.setColor(Fx.ACCENT);
                    c.drawCircle(x, y, r, p);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(Color.WHITE);
                    c.drawCircle(x, y, (3.5f + 1.5f * (float) Math.sin(2.0 * Math.PI * f)) * d, p);
                } else {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(2f * d);
                    p.setColor(Color.argb(90, 255, 255, 255));
                    c.drawCircle(x, y, r, p);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(Color.argb(90, 255, 255, 255));
                    c.drawCircle(x, y, 3f * d, p);
                }
                tp.setFakeBoldText(act);
                tp.setColor(done || act ? Color.argb(240, 255, 255, 255) : Color.argb(125, 255, 255, 255));
                c.drawText(STEPS[i], x, y + r + 22f * d, tp);
            }
        }
    }

    // ================================================================== Barre de pourcentage

    static final class BarView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final ValueAnimator shimmerAnim = ValueAnimator.ofFloat(0f, 1f);
        private float v;
        private float shimmer;
        private float lastW = -1f;
        private LinearGradient grad;

        BarView(Context c) {
            super(c);
            shimmerAnim.setDuration(1400);
            shimmerAnim.setRepeatCount(ValueAnimator.INFINITE);
            shimmerAnim.setInterpolator(new LinearInterpolator());
            shimmerAnim.addUpdateListener(a -> {
                shimmer = (float) a.getAnimatedValue();
                invalidate();
            });
        }

        void setValue(float x) {
            v = Fx.clamp(x);
            invalidate();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!shimmerAnim.isStarted()) shimmerAnim.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            shimmerAnim.cancel();
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float hh = getHeight();
            float rad = hh / 2f;
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            rect.set(0f, 0f, w, hh);
            p.setColor(Color.argb(60, 255, 255, 255));
            c.drawRoundRect(rect, rad, rad, p);

            float fw = Math.max(hh, w * v);
            if (grad == null || Math.abs(fw - lastW) > 0.5f) {
                grad = new LinearGradient(0f, 0f, fw, 0f, Fx.ACCENT, Fx.ACCENT_SOFT, Shader.TileMode.CLAMP);
                lastW = fw;
            }
            p.setShader(grad);
            rect.set(0f, 0f, fw, hh);
            c.drawRoundRect(rect, rad, rad, p);
            p.setShader(null);

            c.save();
            c.clipRect(0f, 0f, fw, hh);
            float bw = hh * 4f;
            float bx = (fw + bw) * shimmer - bw;
            p.setColor(Color.argb(95, 255, 255, 255));
            c.drawRect(bx, 0f, bx + bw * 0.5f, hh, p);
            c.restore();
        }
    }

    // ================================================================== Résultat animé

    static final class ResultView extends View {
        private static final int N = 46;
        private static final int[] PALETTE = {
                Color.parseColor("#2DD4BF"), Color.parseColor("#FACC15"), Color.parseColor("#F472B6"),
                Color.parseColor("#60A5FA"), Color.parseColor("#FFFFFF"), Color.parseColor("#FB923C")};

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        private final float[] vx = new float[N];
        private final float[] vy = new float[N];
        private final float[] sz = new float[N];
        private final float[] rot = new float[N];
        private final int[] col = new int[N];
        private final float d;
        private int mode;
        private float t;

        ResultView(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            anim.setDuration(1700);
            anim.setInterpolator(new LinearInterpolator());
            anim.addUpdateListener(a -> {
                t = (float) a.getAnimatedValue();
                invalidate();
            });
        }

        void start(int m) {
            mode = m;
            Random rnd = new Random(7);
            for (int i = 0; i < N; i++) {
                vx[i] = (rnd.nextFloat() - 0.5f) * 2f * 150f;
                vy[i] = -(110f + rnd.nextFloat() * 230f);
                sz[i] = 3f + rnd.nextFloat() * 3f;
                rot[i] = rnd.nextFloat() * 360f;
                col[i] = PALETTE[rnd.nextInt(PALETTE.length)];
            }
            t = 0f;
            anim.cancel();
            anim.start();
        }

        void stop() {
            anim.cancel();
        }

        @Override
        protected void onDetachedFromWindow() {
            anim.cancel();
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;
            float R = 50f * d;
            int color = mode == OK ? Fx.OK : (mode == ERR ? Fx.ERR : Fx.WARN);

            c.save();
            if (mode == ERR && t > 0.3f) {
                c.translate((float) Math.sin((t - 0.3f) * 70f) * 10f * d * (1f - Fx.seg(t, 0.3f, 0.9f)), 0f);
            }

            // confettis (réussite uniquement)
            if (mode == OK && t > 0.2f) {
                float tt = Fx.seg(t, 0.2f, 1f);
                int alpha = (int) ((1f - Fx.seg(t, 0.75f, 1f)) * 255f);
                p.setStyle(Paint.Style.FILL);
                for (int i = 0; i < N; i++) {
                    float x = cx + vx[i] * d * tt;
                    float y = cy + (vy[i] * tt + 380f * tt * tt) * d;
                    p.setColor((col[i] & 0x00FFFFFF) | (alpha << 24));
                    c.save();
                    c.rotate(rot[i] + tt * 420f, x, y);
                    c.drawRect(x - sz[i] * d, y - sz[i] * d * 0.5f, x + sz[i] * d, y + sz[i] * d * 0.5f, p);
                    c.restore();
                }
            }

            // halo + onde
            float s = Fx.overshoot(Fx.seg(t, 0f, 0.30f));
            p.setStyle(Paint.Style.FILL);
            p.setColor((color & 0x00FFFFFF) | 0x40000000);
            c.drawCircle(cx, cy, R * s * 1.28f, p);
            float k = Fx.seg(t, 0.15f, 0.75f);
            if (k > 0f && k < 1f) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3f * d);
                p.setColor(Color.argb((int) ((1f - k) * 160f), 255, 255, 255));
                c.drawCircle(cx, cy, R + k * 46f * d, p);
            }

            // disque
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            c.drawCircle(cx, cy, R * s, p);

            // symbole tracé progressivement
            float kk = Fx.seg(t, 0.28f, 0.62f);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(7f * d);
            p.setColor(Color.WHITE);
            if (kk > 0f) {
                if (mode == OK) {
                    float ax = cx - 0.42f * R, ay = cy + 0.02f * R;
                    float bx = cx - 0.12f * R, by = cy + 0.34f * R;
                    float qx = cx + 0.46f * R, qy = cy - 0.30f * R;
                    float a = Fx.clamp(kk / 0.4f);
                    float b = Fx.clamp((kk - 0.4f) / 0.6f);
                    c.drawLine(ax, ay, ax + (bx - ax) * a, ay + (by - ay) * a, p);
                    if (b > 0f) c.drawLine(bx, by, bx + (qx - bx) * b, by + (qy - by) * b, p);
                } else if (mode == ERR) {
                    float a = Fx.clamp(kk / 0.5f);
                    float b = Fx.clamp((kk - 0.5f) / 0.5f);
                    float o = 0.30f * R;
                    c.drawLine(cx - o, cy - o, cx - o + 2f * o * a, cy - o + 2f * o * a, p);
                    if (b > 0f) c.drawLine(cx + o, cy - o, cx + o - 2f * o * b, cy - o + 2f * o * b, p);
                } else {
                    float a = Fx.clamp(kk / 0.7f);
                    c.drawLine(cx, cy - 0.40f * R, cx, cy - 0.40f * R + 0.48f * R * a, p);
                    if (kk > 0.7f) {
                        p.setStyle(Paint.Style.FILL);
                        c.drawCircle(cx, cy + 0.34f * R, 4.5f * d * Fx.clamp((kk - 0.7f) / 0.3f), p);
                    }
                }
            }
            c.restore();
        }
    }
}
