package com.laxus.android.refreshlayout.view;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.Transformation;


public class LineSpinLoadingDrawable extends Drawable implements Animatable {

    private static final int ANIMATION_DURATION = 1200;
    private static final int[] DELAY = new int[]{0, 150, 300, 450, 600, 750, 900, 1050};
    private static final float MAX_SCALE = 1.0f;
    private static final int MAX_ALPHA = 255;
    private static final float MIN_SCALE = .3f;
    private static final int MIN_ALPHA = 120;
    private static final int DEFAULT_SIZE = 24;//DP

    private int[] mAlpha = new int[8];
    private float[] mScale = new float[8];
    private int mRepeatCount;

    private int mSize;

    private View mParent;

    private Paint mPaint;

    private RectF mDrawRectF = new RectF();

    private Animation mAnimation;


    public LineSpinLoadingDrawable(View parent) {
        mParent = parent;

        mPaint = new Paint();
//        mPaint.setColor(Color.parseColor("#D8D8D8"));
        mSize = (int) (parent.getResources().getDisplayMetrics().density * DEFAULT_SIZE * 1.2);

        for (int i = 0; i < DELAY.length; ++i) {
            mScale[i] = MAX_SCALE;
            mAlpha[i] = MAX_ALPHA;
        }

        setUpAnimation();

    }


    @Override
    public void draw(@NonNull Canvas canvas) {
        int width = getBounds().width();
        int height = getBounds().height();
        float radius = width / 10;
        for (int i = 0; i < DELAY.length; i++) {
            canvas.save();
            Point point = circleAt(width, height, width / 2.5f - radius, i * (Math.PI / 4));
            canvas.translate(point.x, point.y);
            canvas.scale(mScale[i], mScale[i]);
            canvas.rotate(i * 45);
            mPaint.setAlpha(mAlpha[i]);
            mDrawRectF.set(-radius, -radius / 1.5f, 1.5f * radius, radius / 1.5f);
            canvas.drawRoundRect(mDrawRectF, 5, 5, mPaint);
            canvas.restore();
        }
    }

    @Override
    public void setAlpha(@IntRange(from = 0, to = 255) int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }

    @Override
    public int getIntrinsicWidth() {
        return mSize;
    }

    @Override
    public int getIntrinsicHeight() {
        return mSize;
    }

    @Override
    public void start() {
        if (!mAnimation.hasEnded()) {
            mAnimation.cancel();
        }
        for (int i = 0; i < DELAY.length; ++i) {
            mScale[i] = MAX_SCALE;
            mAlpha[i] = MAX_ALPHA;
        }
        mAnimation.reset();
        mParent.clearAnimation();
        mParent.startAnimation(mAnimation);
    }

    @Override
    public void stop() {
        if (!mAnimation.hasEnded()) {
            mAnimation.cancel();
        }
    }

    @Override
    public boolean isRunning() {
        return !mAnimation.hasEnded();
    }

    private void setUpAnimation() {
        mAnimation = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                int animateTime = (int) (mRepeatCount * ANIMATION_DURATION + ANIMATION_DURATION * interpolatedTime);
                for (int i = 0; i < DELAY.length; ++i) {
                    int duration = Math.max(0, animateTime - DELAY[i]) % ANIMATION_DURATION;
                    float percent = duration / (float) ANIMATION_DURATION;
                    mAlpha[i] = Math.max(MIN_ALPHA, (int) ((1 - percent) * MAX_ALPHA));
                    mScale[i] = Math.max(MIN_SCALE, (1 - percent) * MAX_SCALE);
                    mParent.invalidate();
                }
            }
        };
        mAnimation.setDuration(ANIMATION_DURATION);
        mAnimation.setRepeatCount(Animation.INFINITE);
        mAnimation.setInterpolator(new AccelerateDecelerateInterpolator());

        Animation.AnimationListener listener = new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {

            }

            @Override
            public void onAnimationRepeat(Animation animation) {
                mRepeatCount++;
            }
        };
        mAnimation.setAnimationListener(listener);
    }

    Point circleAt(int width, int height, float radius, double angle) {
        float x = (float) (width / 2 + radius * (Math.cos(angle)));
        float y = (float) (height / 2 + radius * (Math.sin(angle)));
        return new Point(x, y);
    }

    final class Point {
        float x;
        float y;

        Point(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

}
