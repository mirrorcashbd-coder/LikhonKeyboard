package com.likhon.keyboard;

import android.content.Context;

/** The big round glass button from the reference: one icon, lots of light. */
class LiquidGlassCircleButton extends LiquidGlassIconButton {

    LiquidGlassCircleButton(Context context) {
        this(context, 108f, 44f);
    }

    LiquidGlassCircleButton(Context context, float sizeDp, float iconDp) {
        super(context);
        setButtonSizeDp(sizeDp);
        setIconSizeDp(iconDp);
        setShapeRadius(999f);
        setGlassElevation(14f);
        config.tintAlpha = 0.18f;
        config.haloAlpha = 0.32f;
        config.haloWidthDp = 10f;
        config.glossAlpha = 0.32f;
        config.reflectionAlpha = 0.7f;
        config.refraction = 1.10f;
    }
}
