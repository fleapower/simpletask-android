package com.buildware.widget.indeterm;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.StateSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.AttrRes;
import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import java.util.Objects;

import nl.mpcjanssen.simpletask.R;

class Utils {
    public static int applyAlpha(int color, float alpha) {
        return Color.argb(Math.round(Color.alpha(color) * alpha), Color.red(color), Color.green(color), Color.blue(color));
    }

    public static Drawable tintDrawable(View view, @DrawableRes int drawable) {
        if (!(view instanceof IndeterminateCheckable)) {
            throw new IllegalArgumentException("view must implement IndeterminateCheckable");
        }

        final ColorStateList colorStateList = createIndetermColorStateList(view.getContext());

        final Drawable d = DrawableCompat.wrap(Objects.requireNonNull(ContextCompat.getDrawable(view.getContext(), drawable)));
        DrawableCompat.setTintList(d, colorStateList);
        return d;
    }

    private static ColorStateList createIndetermColorStateList(Context context) {
        final int[][] states = new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{R.attr.state_indeterminate}, new int[]{android.R.attr.state_checked}, StateSet.WILD_CARD};

        final int normal = resolveColor(context, com.google.android.material.R.attr.colorControlNormal, Color.DKGRAY);
        final int activated = resolveColor(context, com.google.android.material.R.attr.colorControlActivated, Color.CYAN);
        final float disabledAlpha = resolveFloat(context, android.R.attr.disabledAlpha, 0.25f);
        final int[] colors = new int[]{Utils.applyAlpha(normal, disabledAlpha), normal, activated, normal};

        return new ColorStateList(states, colors);
    }

    private static int resolveColor(Context context, @AttrRes int attr, int defaultValue) {
        int result;

        try (TypedArray a = context.getTheme().obtainStyledAttributes(new int[]{attr})) {
            result = a.getColor(0, defaultValue);
            a.recycle();
        }

        return result;
    }

    private static float resolveFloat(Context context, @AttrRes int attr, float defaultValue) {
        TypedValue val = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, val, true)) {
            return val.getFloat();
        } else {
            return defaultValue;
        }
    }
}