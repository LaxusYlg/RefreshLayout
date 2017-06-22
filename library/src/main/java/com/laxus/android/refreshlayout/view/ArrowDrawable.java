package com.laxus.android.refreshlayout.view;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.Transformation;

import com.laxus.android.refreshlayout.R;


public class ArrowDrawable extends Drawable implements Animatable {

    private static final int ANIMATE_DURATION = 300;
    private static final int STROKE_WIDTH = 2;
    private static final int DEGREE = 180;

    private View mParent;

    private Paint mPaint = new Paint();
    private Bitmap mArrowBitmap;
    private int mDrawableSize;

    private Animation mAnimation;

    private float mCurrentDegree;
    private boolean mDirectionUp = false;

    public ArrowDrawable(View parent) {
        mParent = parent;

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(parent.getResources().getDisplayMetrics().density * STROKE_WIDTH);

        mArrowBitmap = BitmapFactory.decodeResource(parent.getResources(), R.drawable.arrow);
        mDrawableSize = Math.max(mArrowBitmap.getWidth(), mArrowBitmap.getHeight());
        setUpAnimation();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        int width = getBounds().width();
        int height = getBounds().height();
        int left = (width - mArrowBitmap.getWidth()) / 2;
        int top = (height - mArrowBitmap.getHeight()) / 2;
        canvas.save();
        canvas.rotate(mCurrentDegree, width / 2, height / 2);
        canvas.drawBitmap(mArrowBitmap, left, top, mPaint);
        canvas.restore();

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

    private void setUpAnimation() {
        mAnimation = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                mCurrentDegree = mDirectionUp ?
                        DEGREE * interpolatedTime : DEGREE * (1 - interpolatedTime);
                mParent.invalidate();
            }
        };
        Animation.AnimationListener listener = new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mCurrentDegree = mDirectionUp ? DEGREE : 0;
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        };
        mAnimation.setDuration(ANIMATE_DURATION);
        mAnimation.setAnimationListener(listener);
        mAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
    }

    @Override
    public void start() {
        if (!mAnimation.hasEnded()) {
            mAnimation.cancel();
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

    /**
     * toggle arrow direction
     */
    public void toggle() {
        mDirectionUp = !mDirectionUp;
        start();
    }

    @Override
    public int getIntrinsicWidth() {
        return mDrawableSize;
    }

    @Override
    public int getIntrinsicHeight() {
        return mDrawableSize;
    }
}
