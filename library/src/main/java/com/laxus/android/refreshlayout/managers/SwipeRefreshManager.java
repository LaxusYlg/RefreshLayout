package com.laxus.android.refreshlayout.managers;


import android.annotation.SuppressLint;
import android.content.Context;
import android.support.v4.view.ViewCompat;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Transformation;

import com.laxus.android.refreshlayout.RefreshLayout;
import com.laxus.android.refreshlayout.view.CircleImageView;
import com.laxus.android.refreshlayout.view.CopiedProgressDrawable;

import static com.laxus.android.refreshlayout.view.CopiedProgressDrawable.DEFAULT;
import static com.laxus.android.refreshlayout.view.CopiedProgressDrawable.LARGE;

public class SwipeRefreshManager extends RefreshLayout.RefreshManager {

    private static final int CIRCLE_DIAMETER = 40;
    private static final int CIRCLE_DIAMETER_LARGE = 56;

    private static final int MAX_ALPHA = 255;
    private static final int STARTING_PROGRESS_ALPHA = (int) (.3f * MAX_ALPHA);

    // Max amount of circle that can be filled by progress during swipe gesture,
    // where 1.0 is a full circle
    private static final float MAX_PROGRESS_ANGLE = .8f;

    private static final int SCALE_DOWN_DURATION = 150;

    private static final int ALPHA_ANIMATION_DURATION = 300;

    private static final int ANIMATE_TO_TRIGGER_DURATION = 200;

    private static final int ANIMATE_TO_START_DURATION = 200;

    // Default background for the progress spinner
    private static final int CIRCLE_BG_LIGHT = 0xFFFAFAFA;
    // Default offset in dips from the top of the view to where the progress spinner should stop
    private static final int DEFAULT_CIRCLE_TARGET = 64;

    private static final float DECELERATE_INTERPOLATION_FACTOR = 2f;

    private Context mContext;
    private CopiedProgressDrawable mProgress;
    private CircleImageView mCircleView;
    private int mTotalConsumedDistance;

    private int mTotalTriggerDistance;
    private int mLastTargetY;

    private final DecelerateInterpolator mDecelerateInterpolator;

    private Animation mScaleAnimation;

    private Animation mScaleDownAnimation;

    private Animation mAlphaStartAnimation;

    private Animation mAlphaMaxAnimation;

    private int mSpinnerOffsetEnd;

    private int mCircleDiameter;

    private int mCurrentTargetOffsetTop;

    private int mOriginalOffsetTop;

    protected int mFrom;

    private int mMediumAnimationDuration;

    private Animation.AnimationListener mRefreshListener = new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }

        @SuppressLint("NewApi")
        @Override
        public void onAnimationEnd(Animation animation) {
            // Make sure the progress view is fully visible
            mProgress.setAlpha(MAX_ALPHA);
            mProgress.start();
            mCurrentTargetOffsetTop = mCircleView.getTop();
            mLastTargetY = mCurrentTargetOffsetTop;
            //notify OnRefreshListener
            fireRefresh();
        }
    };

    private Animation.AnimationListener mFinishListener = new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {

        }

        @Override
        public void onAnimationEnd(Animation animation) {
            reset();
        }

        @Override
        public void onAnimationRepeat(Animation animation) {

        }
    };

    public SwipeRefreshManager(Context context) {
        mContext = context;
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        mCircleDiameter = (int) (CIRCLE_DIAMETER * metrics.density);
        mTotalTriggerDistance = (int) (DEFAULT_CIRCLE_TARGET * metrics.density);
        mSpinnerOffsetEnd = mTotalTriggerDistance;
        mOriginalOffsetTop = mCurrentTargetOffsetTop = -mCircleDiameter;

        mDecelerateInterpolator = new DecelerateInterpolator(DECELERATE_INTERPOLATION_FACTOR);
        mMediumAnimationDuration = context.getResources().getInteger(android.R.integer.config_mediumAnimTime);

    }

    @Override
    protected View onCreateView(ViewGroup container) {
        mCircleView = new CircleImageView(container.getContext(), CIRCLE_BG_LIGHT);
        mProgress = new CopiedProgressDrawable(container.getContext(), mCircleView);
        mProgress.setBackgroundColor(CIRCLE_BG_LIGHT);
        mCircleView.setImageDrawable(mProgress);
        mCircleView.setVisibility(View.GONE);

        return mCircleView;
    }

    @Override
    protected boolean canMotionTriggerRefresh() {
        return mLastTargetY > mTotalTriggerDistance;
    }

    @Override
    protected boolean acceptScroll() {
        return false;
    }

    @Override
    protected void prepare(boolean isScrolling, boolean isRefreshing) {
        if (isScrolling) {
            animateOffsetToCorrectPosition(mCurrentTargetOffsetTop, mRefreshListener);
        } else {
            int endTarget = mSpinnerOffsetEnd;
            setTargetOffsetTopAndBottom(endTarget - mCurrentTargetOffsetTop,
                    true /* requires update */);
            startScaleUpAnimation(mRefreshListener);
        }
    }

    @Override
    protected void finish(boolean isScrolling, boolean isRefreshing) {
        mTotalConsumedDistance = 0;
        mProgress.setStartEndTrim(0f, 0f);
        mProgress.showArrow(false);
        if (isScrolling) {
            animateOffsetToStartPosition(mCurrentTargetOffsetTop, mFinishListener);
        } else {
            startScaleDownAnimation(mFinishListener);
        }
    }

    @Override
    public void measureTargetAndRefresh(View target, View refresh, int widthMeasureSpec, int heightMeasureSpec) {
        super.measureTargetAndRefresh(target, refresh, widthMeasureSpec, heightMeasureSpec);
        mCircleView.measure(View.MeasureSpec.makeMeasureSpec(mCircleDiameter, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(mCircleDiameter, View.MeasureSpec.EXACTLY));
    }

    @Override
    public void layoutTargetAndRefresh(View target, View refresh, boolean changed, int l, int t, int r, int b) {
        if (target == null)
            return;
        int targetWidth = target.getMeasuredWidth();
        target.layout(0, 0, targetWidth, target.getMeasuredHeight());
        if (refresh != null) {
            int left = (targetWidth - refresh.getMeasuredWidth()) / 2;
            refresh.layout(left, mCurrentTargetOffsetTop,
                    left + refresh.getMeasuredWidth(), mCurrentTargetOffsetTop + refresh.getMeasuredHeight());
        }
    }

    @Override
    protected int onConsume(int dy) {
        if (dy < 0 || (dy > 0 && mTotalConsumedDistance < 0)) {
            int consumed;
            if (mTotalConsumedDistance + dy > 0) {
                mTotalConsumedDistance = 0;
                consumed = Math.abs(mTotalConsumedDistance);
            } else {
                mTotalConsumedDistance += dy;
                consumed = dy;
            }
            moveSpinner(Math.abs(mTotalConsumedDistance));
            return consumed;
        }
        return 0;
    }

    public void setSize(int size) {
        if (size != LARGE && size != DEFAULT) {
            return;
        }
        final DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
        if (size == LARGE) {
            mCircleDiameter = (int) (CIRCLE_DIAMETER_LARGE * metrics.density);
        } else {
            mCircleDiameter = (int) (CIRCLE_DIAMETER * metrics.density);
        }
        // force the bounds of the progress circle inside the circle view to
        // update by setting it to null before updating its size and then
        // re-setting it
        mCircleView.setImageDrawable(null);
        mProgress.updateSizes(size);
        mCircleView.setImageDrawable(mProgress);
    }

    void reset() {
        mCircleView.clearAnimation();
        mProgress.stop();
        mCircleView.setVisibility(View.GONE);
        setColorViewAlpha(MAX_ALPHA);
        setTargetOffsetTopAndBottom(mOriginalOffsetTop - mCurrentTargetOffsetTop, true /* requires update */);
        mCurrentTargetOffsetTop = mCircleView.getTop();
        mLastTargetY = 0;
    }

    private void moveSpinner(float overscrollTop) {
        mProgress.showArrow(true);
        float originalDragPercent = overscrollTop / mTotalTriggerDistance;

        float dragPercent = Math.min(1f, Math.abs(originalDragPercent));
        float adjustedPercent = (float) Math.max(dragPercent - .4, 0) * 5 / 3;
        float extraOS = Math.abs(overscrollTop) - mTotalTriggerDistance;
        float slingshotDist = mSpinnerOffsetEnd;
        float tensionSlingshotPercent = Math.max(0, Math.min(extraOS, slingshotDist * 2)
                / slingshotDist);
        float tensionPercent = (float) ((tensionSlingshotPercent / 4) - Math.pow(
                (tensionSlingshotPercent / 4), 2)) * 2f;
        float extraMove = (slingshotDist) * tensionPercent * 2;

        int targetY = mOriginalOffsetTop + (int) ((slingshotDist * dragPercent) + extraMove);
        mLastTargetY = targetY;
        // where 1.0f is a full circle
        if (mCircleView.getVisibility() != View.VISIBLE) {
            mCircleView.setVisibility(View.VISIBLE);
        }
        ViewCompat.setScaleX(mCircleView, 1f);
        ViewCompat.setScaleY(mCircleView, 1f);


        if (overscrollTop < mTotalTriggerDistance) {
            if (mProgress.getAlpha() > STARTING_PROGRESS_ALPHA
                    && !isAnimationRunning(mAlphaStartAnimation)) {
                // Animate the alpha
                startProgressAlphaStartAnimation();
            }
        } else {
            if (mProgress.getAlpha() < MAX_ALPHA && !isAnimationRunning(mAlphaMaxAnimation)) {
                // Animate the alpha
                startProgressAlphaMaxAnimation();
            }
        }
        float strokeStart = adjustedPercent * .8f;
        mProgress.setStartEndTrim(0f, Math.min(MAX_PROGRESS_ANGLE, strokeStart));
        mProgress.setArrowScale(Math.min(1f, adjustedPercent));

        float rotation = (-0.25f + .4f * adjustedPercent + tensionPercent * 2) * .5f;
        mProgress.setProgressRotation(rotation);
        setTargetOffsetTopAndBottom(targetY - mCurrentTargetOffsetTop, true /* requires update */);
    }

    private void animateOffsetToCorrectPosition(int from, Animation.AnimationListener listener) {
        mFrom = from;
        mAnimateToCorrectPosition.reset();
        mAnimateToCorrectPosition.setDuration(ANIMATE_TO_TRIGGER_DURATION);
        mAnimateToCorrectPosition.setInterpolator(mDecelerateInterpolator);
        if (listener != null) {
            mCircleView.setAnimationListener(listener);
        }
        mCircleView.clearAnimation();
        mCircleView.startAnimation(mAnimateToCorrectPosition);
    }

    private void animateOffsetToStartPosition(int from, Animation.AnimationListener listener) {
        mFrom = from;
        mAnimateToStartPosition.reset();
        mAnimateToStartPosition.setDuration(ANIMATE_TO_START_DURATION);
        mAnimateToStartPosition.setInterpolator(mDecelerateInterpolator);
        if (listener != null) {
            mCircleView.setAnimationListener(listener);
        }
        mCircleView.clearAnimation();
        mCircleView.startAnimation(mAnimateToStartPosition);

    }

    private final Animation mAnimateToCorrectPosition = new Animation() {
        @Override
        public void applyTransformation(float interpolatedTime, Transformation t) {
            int targetTop = 0;
            int endTarget = mSpinnerOffsetEnd;
            targetTop = (mFrom + (int) ((endTarget - mFrom) * interpolatedTime));
            int offset = targetTop - mCircleView.getTop();
            setTargetOffsetTopAndBottom(offset, false /* requires update */);
            mProgress.setArrowScale(1 - interpolatedTime);
        }
    };

    void moveToStart(float interpolatedTime) {
        int targetTop = (mFrom + (int) ((mOriginalOffsetTop - mFrom) * interpolatedTime));
        int offset = targetTop - mCircleView.getTop();
        setTargetOffsetTopAndBottom(offset, false /* requires update */);
    }

    private final Animation mAnimateToStartPosition = new Animation() {
        @Override
        public void applyTransformation(float interpolatedTime, Transformation t) {
            moveToStart(interpolatedTime);
        }
    };

    private void startScaleUpAnimation(Animation.AnimationListener listener) {
        mCircleView.setVisibility(View.VISIBLE);
        if (android.os.Build.VERSION.SDK_INT >= 11) {
            // Pre API 11, alpha is used in place of scale up to show the
            // progress circle appearing.
            // Don't adjust the alpha during appearance otherwise.
            mProgress.setAlpha(MAX_ALPHA);
        }
        mScaleAnimation = new Animation() {
            @Override
            public void applyTransformation(float interpolatedTime, Transformation t) {
                setAnimationProgress(interpolatedTime);
            }
        };
        mScaleAnimation.setDuration(mMediumAnimationDuration);
        if (listener != null) {
            mCircleView.setAnimationListener(listener);
        }
        mCircleView.clearAnimation();
        mCircleView.startAnimation(mScaleAnimation);
    }

    @SuppressLint("NewApi")
    private void startProgressAlphaStartAnimation() {
        mAlphaStartAnimation = startAlphaAnimation(mProgress.getAlpha(), STARTING_PROGRESS_ALPHA);
    }

    @SuppressLint("NewApi")
    private void startProgressAlphaMaxAnimation() {
        mAlphaMaxAnimation = startAlphaAnimation(mProgress.getAlpha(), MAX_ALPHA);
    }

    @SuppressLint("NewApi")
    private Animation startAlphaAnimation(final int startingAlpha, final int endingAlpha) {
        Animation alpha = new Animation() {
            @Override
            public void applyTransformation(float interpolatedTime, Transformation t) {
                mProgress.setAlpha(
                        (int) (startingAlpha + ((endingAlpha - startingAlpha) * interpolatedTime)));
            }
        };
        alpha.setDuration(ALPHA_ANIMATION_DURATION);
        // Clear out the previous animation listeners.
        mCircleView.setAnimationListener(null);
        mCircleView.clearAnimation();
        mCircleView.startAnimation(alpha);
        return alpha;
    }

    void startScaleDownAnimation(Animation.AnimationListener listener) {
        mScaleDownAnimation = new Animation() {
            @Override
            public void applyTransformation(float interpolatedTime, Transformation t) {
                setAnimationProgress(1 - interpolatedTime);
            }
        };
        mScaleDownAnimation.setDuration(SCALE_DOWN_DURATION);
        mCircleView.setAnimationListener(listener);
        mCircleView.clearAnimation();
        mCircleView.startAnimation(mScaleDownAnimation);
    }

    void setTargetOffsetTopAndBottom(int offset, boolean requiresUpdate) {
        mCircleView.bringToFront();
        ViewCompat.offsetTopAndBottom(mCircleView, offset);
        mCurrentTargetOffsetTop = mCircleView.getTop();
        if (requiresUpdate && android.os.Build.VERSION.SDK_INT < 11) {
            mRefreshLayout.invalidate();
        }
    }

    void setAnimationProgress(float progress) {
        if (isAlphaUsedForScale()) {
            setColorViewAlpha((int) (progress * MAX_ALPHA));
        } else {
            ViewCompat.setScaleX(mCircleView, progress);
            ViewCompat.setScaleY(mCircleView, progress);
        }
    }

    private void setColorViewAlpha(int targetAlpha) {
        mCircleView.getBackground().setAlpha(targetAlpha);
        mProgress.setAlpha(targetAlpha);
    }

    private boolean isAlphaUsedForScale() {
        return android.os.Build.VERSION.SDK_INT < 11;
    }

    private boolean isAnimationRunning(Animation animation) {
        return animation != null && animation.hasStarted() && !animation.hasEnded();
    }
}
