package com.sebtransfer.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/** Palette, formes et effets d'animation partagés par tous les écrans. */
final class Fx {
    private Fx() {}

    static final int PRIMARY = Color.parseColor("#0F766E");
    static final int PRIMARY_DARK = Color.parseColor("#053F3B");
    static final int ACCENT = Color.parseColor("#2DD4BF");
    static final int ACCENT_SOFT = Color.parseColor("#B6F2EA");
    static final int TINT = Color.parseColor("#E6F6F4");
    static final int BG = Color.parseColor("#F1F5F5");
    static final int TEXT = Color.parseColor("#0F172A");
    static final int MUTED = Color.parseColor("#64748B");
    static final int BORDER = Color.parseColor("#D5DDE0");
    static final int OK = Color.parseColor("#16A34A");
    static final int WARN = Color.parseColor("#D97706");
    static final int ERR = Color.parseColor("#DC2626");

    static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    // ------------------------------------------------------------------ Formes

    static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    static GradientDrawable circle(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    static GradientDrawable stroke(int fill, float radiusPx, int strokeColor, int strokeWidthPx) {
        GradientDrawable g = round(fill, radiusPx);
        g.setStroke(strokeWidthPx, strokeColor);
        return g;
    }

    // ------------------------------------------------------------------ Maths d'animation

    static float clamp(float x) {
        return x < 0f ? 0f : (x > 1f ? 1f : x);
    }

    /** Progression 0..1 de t dans l'intervalle [a, b]. */
    static float seg(float t, float a, float b) {
        return clamp((t - a) / (b - a));
    }

    static float easeOut(float x) {
        float k = 1f - clamp(x);
        return 1f - k * k * k;
    }

    /** Dépasse légèrement 1 avant de se stabiliser (effet « rebond »). */
    static float overshoot(float x) {
        if (x <= 0f) return 0f;
        if (x >= 1f) return 1f;
        float k = x - 1f;
        return 1f + 2.70158f * k * k * k + 1.70158f * k * k;
    }

    // ------------------------------------------------------------------ Effets sur les vues

    /** Entrée en cascade : chaque enfant monte en fondu, l'un après l'autre. */
    static void cascade(ViewGroup g, int startDelayMs) {
        float dy = dp(g.getContext(), 22);
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationY(dy);
            v.animate()
                    .setStartDelay(startDelayMs + Math.min(i, 9) * 60L)
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(420)
                    .setInterpolator(new DecelerateInterpolator(1.7f))
                    .start();
        }
    }

    /** Effet d'appui : la vue se rétrécit sous le doigt puis rebondit au relâchement. */
    static void pressable(View v) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().setStartDelay(0).scaleX(0.95f).scaleY(0.95f).setDuration(90)
                            .setInterpolator(new DecelerateInterpolator()).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().setStartDelay(0).scaleX(1f).scaleY(1f).setDuration(220)
                            .setInterpolator(new OvershootInterpolator(2.5f)).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    /** Fait apparaître une vue masquée (fondu + petite montée). Sans effet si déjà visible. */
    static void reveal(View v) {
        if (v.getVisibility() == View.VISIBLE) return;
        v.setAlpha(0f);
        v.setTranslationY(dp(v.getContext(), 10));
        v.setVisibility(View.VISIBLE);
        v.animate().setStartDelay(0).alpha(1f).translationY(0f).setDuration(280)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    /** Secousse horizontale pour signaler une saisie incorrecte. */
    static void shake(View v) {
        float a = dp(v.getContext(), 9);
        android.animation.ObjectAnimator o = android.animation.ObjectAnimator.ofFloat(
                v, View.TRANSLATION_X, 0f, -a, a, -a * 0.7f, a * 0.7f, -a * 0.35f, a * 0.35f, 0f);
        o.setDuration(420);
        o.start();
    }

    // ------------------------------------------------------------------ Dessin

    /** Deux flèches opposées (logo). radius = rayon du cercle qui contient le logo. */
    static void drawSwap(Canvas c, float cx, float cy, float radius, Paint p) {
        c.save();
        c.translate(cx, cy);
        float u = radius / 54f;
        c.scale(u, u);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(8f);
        Path a = new Path();
        a.moveTo(-24, -12);
        a.lineTo(22, -12);
        a.moveTo(10, -24);
        a.lineTo(24, -12);
        a.lineTo(10, 0);
        a.moveTo(24, 14);
        a.lineTo(-22, 14);
        a.moveTo(-10, 2);
        a.lineTo(-24, 14);
        a.lineTo(-10, 26);
        c.drawPath(a, p);
        c.restore();
    }
}
