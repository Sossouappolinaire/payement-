package com.sebtransfer.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Animation d'OUVERTURE : ondes qui se propagent, logo qui tourne en grossissant, points en orbite.
 * (L'envoi d'un transfert a son propre effet : voir TransferOverlay.)
 */
final class SplashView extends View {
    interface Done {
        void run();
    }

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tagPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float d;
    private final ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
    private float t;
    private Done done;

    SplashView(Context ctx) {
        super(ctx);
        d = ctx.getResources().getDisplayMetrics().density;
        setClickable(true);
        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setTypeface(Typeface.DEFAULT_BOLD);
        tagPaint.setTextAlign(Paint.Align.CENTER);
        anim.setDuration(2400);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                Done x = done;
                done = null;
                if (x != null) x.run();
            }
        });
    }

    void start(Done cb) {
        done = cb;
        anim.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        bg.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{Fx.PRIMARY_DARK, Fx.PRIMARY}, null, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDetachedFromWindow() {
        done = null;
        anim.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        c.drawRect(0, 0, w, h, bg);
        float cx = w / 2f;
        float cy = h * 0.40f;
        float base = 54f * d;

        // 1) ondes qui s'éloignent du logo
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f * d);
        for (int i = 0; i < 3; i++) {
            float k = Fx.seg(t, 0.08f + i * 0.13f, 0.70f + i * 0.10f);
            if (k <= 0f || k >= 1f) continue;
            float r = base + Fx.easeOut(k) * w * 0.55f;
            p.setColor(Color.argb((int) ((1f - k) * 90f), 255, 255, 255));
            c.drawCircle(cx, cy, r, p);
        }

        // 2) trois points qui tournent autour du logo
        float orb = Fx.seg(t, 0.12f, 0.35f) * (1f - Fx.seg(t, 0.78f, 0.95f));
        if (orb > 0f) {
            p.setStyle(Paint.Style.FILL);
            float rr = base + 28f * d;
            for (int i = 0; i < 3; i++) {
                double ang = t * Math.PI * 3.2 + i * 2.0944;
                p.setColor(Color.argb((int) (orb * 255f), 45, 212, 191));
                c.drawCircle(cx + (float) Math.cos(ang) * rr, cy + (float) Math.sin(ang) * rr, 4.5f * d, p);
            }
        }

        // 3) logo : grossit avec rebond, les flèches tournent jusqu'à leur place
        float s = Fx.overshoot(Fx.seg(t, 0.05f, 0.40f));
        if (s > 0f) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(60, 45, 212, 191));
            c.drawCircle(cx, cy, base * s * 1.22f, p);
            p.setColor(Color.WHITE);
            c.drawCircle(cx, cy, base * s, p);
            p.setColor(Fx.PRIMARY);
            c.save();
            c.rotate((1f - Fx.easeOut(Fx.seg(t, 0.10f, 0.55f))) * -200f, cx, cy);
            Fx.drawSwap(c, cx, cy, base * s, p);
            c.restore();
        }

        // 4) nom de l'appli puis slogan
        float a1 = Fx.seg(t, 0.40f, 0.65f);
        if (a1 > 0f) {
            titlePaint.setTextSize(30f * d);
            titlePaint.setColor(Color.argb((int) (a1 * 255f), 255, 255, 255));
            c.drawText("SEB Transfert", cx, cy + base + 62f * d + (1f - Fx.easeOut(a1)) * 22f * d, titlePaint);
        }
        float a2 = Fx.seg(t, 0.55f, 0.80f);
        if (a2 > 0f) {
            tagPaint.setTextSize(15f * d);
            tagPaint.setColor(Color.argb((int) (a2 * 230f), 182, 242, 234));
            c.drawText("Envoyez de l'argent, simplement.", cx, cy + base + 94f * d + (1f - Fx.easeOut(a2)) * 16f * d, tagPaint);
        }

        // 5) barre de chargement
        float fade = 1f - Fx.seg(t, 0.92f, 1f);
        float bw = 130f * d;
        float bh = 5f * d;
        float by = h - 96f * d;
        p.setStyle(Paint.Style.FILL);
        rect.set(cx - bw / 2f, by, cx + bw / 2f, by + bh);
        p.setColor(Color.argb((int) (fade * 55f), 255, 255, 255));
        c.drawRoundRect(rect, bh / 2f, bh / 2f, p);
        rect.set(cx - bw / 2f, by, cx - bw / 2f + bw * Fx.seg(t, 0.05f, 0.95f), by + bh);
        p.setColor(Color.argb((int) (fade * 255f), 45, 212, 191));
        c.drawRoundRect(rect, bh / 2f, bh / 2f, p);
    }
}
