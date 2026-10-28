package com.sebtransfer.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Petites icônes vectorielles dessinées au Canvas (grille de 24 unités). */
final class IconView extends View {
    static final int SLIDERS = 0;
    static final int BACK = 1;
    static final int COPY = 2;
    static final int TRASH = 3;
    static final int CHECK = 4;
    static final int CROSS = 5;
    static final int CLOCK = 6;
    static final int ARROW_DOWN = 7;
    static final int CHEVRON = 8;

    private final int type;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    IconView(Context ctx, int type, int color) {
        super(ctx);
        this.type = type;
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        float s = Math.min(w, h);
        c.save();
        c.translate((w - s) / 2f, (h - s) / 2f);
        c.scale(s / 24f, s / 24f);
        p.setStrokeWidth(2.2f);
        p.setStyle(Paint.Style.STROKE);
        path.reset();
        switch (type) {
            case SLIDERS:
                slider(c, 6f, 9f);
                slider(c, 12f, 15.5f);
                slider(c, 18f, 8f);
                break;
            case BACK:
                path.moveTo(19, 12);
                path.lineTo(5, 12);
                path.moveTo(11, 6);
                path.lineTo(5, 12);
                path.lineTo(11, 18);
                c.drawPath(path, p);
                break;
            case COPY:
                c.drawRoundRect(9f, 9f, 20f, 20f, 2.5f, 2.5f, p);
                path.moveTo(8, 15);
                path.lineTo(6, 15);
                path.quadTo(4, 15, 4, 13);
                path.lineTo(4, 6);
                path.quadTo(4, 4, 6, 4);
                path.lineTo(13, 4);
                path.quadTo(15, 4, 15, 6);
                path.lineTo(15, 8);
                c.drawPath(path, p);
                break;
            case TRASH:
                path.moveTo(4, 7);
                path.lineTo(20, 7);
                path.moveTo(9, 7);
                path.lineTo(9, 4);
                path.lineTo(15, 4);
                path.lineTo(15, 7);
                path.moveTo(6, 7);
                path.lineTo(7, 20);
                path.lineTo(17, 20);
                path.lineTo(18, 7);
                path.moveTo(10, 11);
                path.lineTo(10, 16);
                path.moveTo(14, 11);
                path.lineTo(14, 16);
                c.drawPath(path, p);
                break;
            case CHECK:
                p.setStrokeWidth(2.8f);
                path.moveTo(5, 12.5f);
                path.lineTo(10, 17.5f);
                path.lineTo(19, 7);
                c.drawPath(path, p);
                break;
            case CROSS:
                p.setStrokeWidth(2.8f);
                path.moveTo(6, 6);
                path.lineTo(18, 18);
                path.moveTo(18, 6);
                path.lineTo(6, 18);
                c.drawPath(path, p);
                break;
            case CLOCK:
                p.setStrokeWidth(2.4f);
                c.drawCircle(12f, 12f, 8.5f, p);
                path.moveTo(12, 7.5f);
                path.lineTo(12, 12);
                path.lineTo(15.5f, 14);
                c.drawPath(path, p);
                break;
            case ARROW_DOWN:
                path.moveTo(12, 5);
                path.lineTo(12, 19);
                path.moveTo(6, 13);
                path.lineTo(12, 19);
                path.lineTo(18, 13);
                c.drawPath(path, p);
                break;
            case CHEVRON:
                p.setStrokeWidth(2.6f);
                path.moveTo(6, 9);
                path.lineTo(12, 15);
                path.lineTo(18, 9);
                c.drawPath(path, p);
                break;
            default:
                break;
        }
        c.restore();
    }

    /** Une ligne horizontale avec un rond (réglage) à la position knobX. */
    private void slider(Canvas c, float y, float knobX) {
        c.drawLine(4f, y, knobX - 3f, y, p);
        c.drawLine(knobX + 3f, y, 20f, y, p);
        c.drawCircle(knobX, y, 2.3f, p);
    }
}
