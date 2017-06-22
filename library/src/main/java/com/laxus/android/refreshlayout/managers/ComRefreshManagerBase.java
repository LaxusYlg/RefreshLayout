package com.laxus.android.refreshlayout.managers;


import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;

import com.laxus.android.refreshlayout.RefreshLayout;

public abstract class ComRefreshManagerBase extends RefreshLayout.RefreshManager {

    private static final float SCROLL_RATE = .6f;

    private static final int ANIMATE_TO_TRIGGER_DURATION = 200;

    private static final int ANIMATE_TO_START_DURATION = 400;

    private View mRefreshView;

    private int mConsumedDistance;
    private int mCurrentViewOffset;

    @Override
    protected View onCreateView(ViewGroup container) {
        mRefreshView = createRefreshView(container);
        return mRefreshView;
    }

    /**
     * create a view
     *
     * @param container container of refresh view
     * @return refresh view
     */
    protected abstract View createRefreshView(ViewGroup container);

    @Override
    protected boolean canMotionTriggerRefresh() {
        return Math.abs(mCurrentViewOffset) > getRefreshTriggerDistance();
    }

    /**
     * define the refresh trigger offset
     *
     * @return distance in pixel that could trigger a refresh event
     */
    protected int getRefreshTriggerDistance() {
        return mRefreshView.getMeasuredHeight();
    }

    @Override
    protected void prepare(boolean isScrolling, boolean changed) {
        final boolean stateChanged = changed;
        final int triggerDistance = getRefreshTriggerDistance();
        final boolean scrolling = isScrolling;
        final int totalOffset = mCurrentViewOffset;
        Animation animateToRefresh = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                int offset;
                if (scrolling) {
                    offset = (int) ((totalOffset + triggerDistance) * (1 - interpolatedTime) - triggerDistance);
                } else {
                    offset = -(int) (triggerDistance * interpolatedTime);
                }
                mCurrentViewOffset = offset;
                mRefreshLayout.scrollTo(0, offset);
                if (stateChanged) {
                    onScroll(offset);
                }
            }
        };
        Animation.AnimationListener listener = new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mCurrentViewOffset = -triggerDistance;
                mConsumedDistance = (int) (mCurrentViewOffset / SCROLL_RATE);
                if (stateChanged) {
                    //notify OnRefreshListener
                    fireRefresh();

                    onRefreshing();
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        };
        animateToRefresh.setDuration(ANIMATE_TO_TRIGGER_DURATION);
        animateToRefresh.setAnimationListener(listener);

        mRefreshLayout.clearAnimation();
        mRefreshLayout.startAnimation(animateToRefresh);
    }

    @Override
    protected void finish(boolean isScrolling, boolean isRefreshing) {
        final boolean duringRefreshing = isRefreshing;
        final int totalOffset = mCurrentViewOffset;
        Animation animateToStart = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                int offset = (int) (totalOffset * (1 - interpolatedTime));
                mRefreshLayout.scrollTo(0, offset);
            }
        };
        Animation.AnimationListener listener = new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                reset(duringRefreshing);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        };
        animateToStart.setAnimationListener(listener);
        animateToStart.setDuration(ANIMATE_TO_START_DURATION);

        mRefreshLayout.clearAnimation();
        mRefreshLayout.startAnimation(animateToStart);

    }

    @Override
    public void measureTargetAndRefresh(View target, View refresh, int widthMeasureSpec, int heightMeasureSpec) {
        super.measureTargetAndRefresh(target, refresh, widthMeasureSpec, heightMeasureSpec);
        if (refresh != null) {
            int width = mRefreshLayout.getMeasuredWidth();
            int height = mRefreshView.getMeasuredHeight();

            ViewGroup.LayoutParams lp = refresh.getLayoutParams();
            int measureSpec;
            if (lp.height == ViewGroup.LayoutParams.WRAP_CONTENT) {
                measureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            } else if (lp.height == ViewGroup.LayoutParams.MATCH_PARENT) {
                measureSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY);
            } else {
                measureSpec = View.MeasureSpec.makeMeasureSpec(lp.height, View.MeasureSpec.EXACTLY);
            }
            refresh.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    measureSpec);
        }
    }

    @Override
    public void layoutTargetAndRefresh(View target, View refresh, boolean changed, int l, int t, int r, int b) {
        if (target != null) {
            target.layout(0, 0, target.getMeasuredWidth(), target.getMeasuredHeight());
        }
        if (refresh != null) {
            refresh.layout(0, -refresh.getMeasuredHeight(), refresh.getMeasuredWidth(), 0);
        }
    }

    @Override
    protected void startConsume() {
        super.startConsume();
        onScrollStart();
    }

    @Override
    protected int onConsume(int dy) {
        if (dy < 0 || (dy > 0 && mConsumedDistance < 0)) {
            int consumed;
            if (mConsumedDistance + dy > 0) {
                mConsumedDistance = 0;
                consumed = Math.abs(mConsumedDistance);
            } else {
                mConsumedDistance += dy;
                consumed = dy;
            }
            scroll(mConsumedDistance, !mIsRefreshing);
            return consumed;
        }
        return 0;
    }


    private void scroll(int distance, boolean notify) {
        mCurrentViewOffset = (int) (distance * SCROLL_RATE);
        mRefreshLayout.scrollTo(0, mCurrentViewOffset);
        if (notify) {
            onScroll(mCurrentViewOffset);
        }
    }

    private void reset(boolean notify) {
        mCurrentViewOffset = 0;
        mConsumedDistance = 0;
        if (notify) {
            onReset();
        }
    }

    protected void onScrollStart() {
    }

    protected void onScroll(int offset) {
    }

    protected void onRefreshing() {
    }

    protected void onReset() {
    }
}
