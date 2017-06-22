package com.laxus.android.refreshlayout;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.support.annotation.IntDef;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.NestedScrollingChild;
import android.support.v4.view.NestedScrollingChildHelper;
import android.support.v4.view.NestedScrollingParent;
import android.support.v4.view.NestedScrollingParentHelper;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AbsListView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A refresh widget that its refresh style is delegated to RefreshManager.
 * It is designed to reduce code modify while changing refresh style.
 */
public class RefreshLayout extends ViewGroup implements NestedScrollingChild, NestedScrollingParent {

    private static final String LOG_TAG = "RefreshLayout";

    private static final int INVALID_POINTER = -1;

    /**
     * above target
     */
    public static final int ABOVE = 1;
    /**
     * below target
     */
    public static final int BELOW = 2;

    @IntDef({ABOVE, BELOW})
    @Retention(RetentionPolicy.SOURCE)
    public @interface DrawingOrder {
    }

    public interface OnRefreshListener {
        void onRefreshing();
    }

    private NestedScrollingChildHelper mNestedScrollingChildHelper;
    private NestedScrollingParentHelper mNestedScrollingParentHelper;

    /**
     * attached RefreshManager
     */
    private RefreshManager mRefreshManager;

    private OnRefreshListener mRefreshListener;

    private TargetScrollUpListener mTargetScrollUpChecker;

    private View mTargetView;
    private View mRefreshView;

    private int[] mParentOffsetInWindow = new int[2];
    private int[] mParentConsumed = new int[2];

    private int mActivePointerId = INVALID_POINTER;
    private float mInitialDownY;
    private float mLastMotionY;
    private int mTouchSlop;

    /**
     * current drawing order of the refresh view
     */
    private int mRefreshViewIndex;

    @SuppressWarnings("FieldCanBeLocal")
    @DrawingOrder
    private int mRefreshViewDrawingOrder;

    private boolean mIsBeingDragged;

    /**
     * whether is processing nested scrolling
     */
    private boolean mInNestedScrolling;

    /**
     * whether gesture has been locked.
     * if refresh is triggered during nestedScrolling or dragging,
     * then any gesture will be locked.
     */
    private boolean mGestureLocked;

    /**
     * whether gesture end should notify RM
     */
    private boolean mNotifyMotionEnd;

    /**
     * whether one of
     * {@link #onNestedPreScroll(View, int, int, int[])},
     * {@link #onNestedScroll(View, int, int, int, int)},
     * {@link #onNestedPreFling(View, float, float)},
     * {@link #onNestedFling(View, float, float, boolean)}
     * has been called
     */
    boolean hasNestedMotion;

    public RefreshLayout(Context context) {
        this(context, null);
    }

    public RefreshLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RefreshLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        ViewCompat.setChildrenDrawingOrderEnabled(this, true);

        mNestedScrollingChildHelper = new NestedScrollingChildHelper(this);
        mNestedScrollingParentHelper = new NestedScrollingParentHelper(this);

        setNestedScrollingEnabled(true);

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

    }

    //region NestedScrollingParent
    @Override
    public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
        //some RefreshManager may want to accept nestedScroll in any condition
        return isEnabled() && acceptScroll() && (nestedScrollAxes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
    }

    @Override
    public int getNestedScrollAxes() {
        return mNestedScrollingParentHelper.getNestedScrollAxes();
    }

    @Override
    public void onNestedScrollAccepted(View child, View target, int nestedScrollAxes) {
        mNestedScrollingParentHelper.onNestedScrollAccepted(child, target, nestedScrollAxes);
        mInNestedScrolling = true;
        mNotifyMotionEnd = true;
        mRefreshManager.startConsume();
    }

    @Override
    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        if (dy > 0) {
            int scrolled = mRefreshManager.onConsume(dy);
            if (Math.abs(scrolled) > 0) {
                hasNestedMotion = true;
            }
            if (scrolled > dy) {
                throw new IllegalStateException("RM seemed to eat too much{dy=" + dy + ",onScrolling() return=" + scrolled);
            }
            consumed[1] += scrolled;
        }

        final int[] parentConsumed = mParentConsumed;
        if (dispatchNestedPreScroll(dx, dy, parentConsumed, null)) {
            consumed[0] += parentConsumed[0];
            consumed[1] += parentConsumed[1];
        }
    }

    @Override
    public void onNestedScroll(View target, int dxConsumed, int dyConsumed,
                               int dxUnconsumed, int dyUnconsumed) {
        dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, mParentOffsetInWindow);

        final int dy = dyUnconsumed + mParentOffsetInWindow[1];
        if (dy < 0 && !canTargetScrollUp()) {
            int scrolled = mRefreshManager.onConsume(dyUnconsumed);
            if (Math.abs(scrolled) > 0) {
                hasNestedMotion = true;
            }
        }
    }

    @Override
    public void onStopNestedScroll(View target) {
        mNestedScrollingParentHelper.onStopNestedScroll(target);
        mInNestedScrolling = false;
        if (mRefreshManager != null && mNotifyMotionEnd) {
            mRefreshManager.stopConsume();
        }
        stopNestedScroll();
        hasNestedMotion = false;
    }

    @Override
    public boolean onNestedFling(View target, float velocityX, float velocityY, boolean consumed) {
        return dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public boolean onNestedPreFling(View target, float velocityX, float velocityY) {
        return dispatchNestedPreFling(velocityX, velocityY);
    }
    //endregion

    //region nested scrolling child
    @Override
    public void setNestedScrollingEnabled(boolean enabled) {
        mNestedScrollingChildHelper.setNestedScrollingEnabled(enabled);
    }

    @Override
    public boolean isNestedScrollingEnabled() {
        return mNestedScrollingChildHelper.isNestedScrollingEnabled();
    }

    @Override
    public boolean hasNestedScrollingParent() {
        return mNestedScrollingChildHelper.hasNestedScrollingParent();
    }

    @Override
    public boolean startNestedScroll(int axes) {
        return mNestedScrollingChildHelper.startNestedScroll(axes);
    }

    @Override
    public void stopNestedScroll() {
        mNestedScrollingChildHelper.stopNestedScroll();
    }


    @Override
    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed,
                                        int dyUnconsumed, int[] offsetInWindow) {
        return mNestedScrollingChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed,
                dxUnconsumed, dyUnconsumed, offsetInWindow);
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow) {
        return mNestedScrollingChildHelper.dispatchNestedPreScroll(
                dx, dy, consumed, offsetInWindow);
    }

    @Override
    public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
        return mNestedScrollingChildHelper.dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
        return mNestedScrollingChildHelper.dispatchNestedPreFling(velocityX, velocityY);
    }
    //endregion

    private void ensureTargetView() {
        if (mTargetView == null) {
            for (int i = 0; i < getChildCount(); ++i) {
                View child = getChildAt(i);
                if (child != mRefreshView) {
                    mTargetView = child;
                    break;
                }
            }
        }
    }

    private boolean canTargetScrollUp() {
        if (mTargetScrollUpChecker != null) {
            return mTargetScrollUpChecker.canTargetScrollUp();
        }
        if (Build.VERSION.SDK_INT < 14) {
            if (mTargetView instanceof AbsListView) {
                final AbsListView absListView = (AbsListView) mTargetView;
                return absListView.getChildCount() > 0
                        && (absListView.getFirstVisiblePosition() > 0 || absListView.getChildAt(0)
                        .getTop() < absListView.getPaddingTop());
            } else {
                return ViewCompat.canScrollVertically(mTargetView, -1) || mTargetView.getScrollY() > 0;
            }
        } else {
            return ViewCompat.canScrollVertically(mTargetView, -1);
        }
    }

    void abortScrolling() {
        if (mInNestedScrolling) {
            mNotifyMotionEnd = false;

            if (Build.VERSION.SDK_INT < 21) {
                ((NestedScrollingChild) mTargetView).stopNestedScroll();
            } else {
                mTargetView.stopNestedScroll();
            }
            //if fresh triggered during nestedScrolling, then any gesture should be locked,
            //otherwise RML will continue consuming the scrolling produced by gesture
            mGestureLocked = true;
        }

        if (mIsBeingDragged) {
            mNotifyMotionEnd = false;
            //finish gesture
            final long now = SystemClock.uptimeMillis();
            MotionEvent cancelEvent = MotionEvent.obtain(now, now,
                    MotionEvent.ACTION_CANCEL, 0.0f, 0.0f, 0);
            onTouchEvent(cancelEvent);

            mGestureLocked = true;

        }
    }

    void fireRefreshEvent() {
        if (mRefreshListener != null) {
            mRefreshListener.onRefreshing();
        }
    }

    boolean acceptScroll() {
        return mRefreshManager != null && mRefreshManager.acceptScroll();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mRefreshManager != null) {
            mRefreshManager.onAttachedToWindow();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mRefreshManager != null) {
            mRefreshManager.onDetachedFromWindow();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        ensureTargetView();
        if (mTargetView != null) {
            mTargetView.measure(
                    MeasureSpec.makeMeasureSpec(getMeasuredWidth() - getPaddingLeft() - getPaddingRight(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(getMeasuredHeight() - getPaddingTop() - getPaddingTop(), MeasureSpec.EXACTLY));
        }
        if (mRefreshManager != null) {
            mRefreshManager.measureTargetAndRefresh(mTargetView, mRefreshView, widthMeasureSpec, heightMeasureSpec);
        }

        mRefreshViewIndex = -1;
        for (int i = 0; i < getChildCount(); ++i) {
            final View child = getChildAt(i);
            if (child == mRefreshView) {
                mRefreshViewIndex = i;
                break;
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (mRefreshManager == null) {
            Log.d(LOG_TAG, "no RefreshManager attached, skip layout");
            return;
        }
        mRefreshManager.layoutTargetAndRefresh(mTargetView, mRefreshView, changed, l, t, r, b);
    }

    @Override
    protected int getChildDrawingOrder(int childCount, int i) {
        if (mRefreshViewIndex < 0 || mRefreshManager == null) {
            return i;
        }
        mRefreshViewDrawingOrder = mRefreshManager.getViewDrawingOrder();
        if (mRefreshViewDrawingOrder == ABOVE) {
            if (i == getChildCount() - 1) {
                return mRefreshViewIndex;
            } else if (i >= mRefreshViewIndex) {
                return i + 1;
            } else {
                return i;
            }
        } else {
            if (i == 0) {
                return mRefreshViewIndex;
            } else if (i <= mRefreshViewIndex) {
                return i - 1;
            } else {
                return i;
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {

        if (mRefreshManager == null || !isEnabled() || canTargetScrollUp() || !acceptScroll() || mInNestedScrolling
                || mGestureLocked) {
            // Fail fast if we're not in a state where a swipe is possible
            return false;
        }

        final int action = MotionEventCompat.getActionMasked(ev);
        int pointerIndex;

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mActivePointerId = ev.getPointerId(0);
                mIsBeingDragged = false;

                mInitialDownY = ev.getY(0);
                mLastMotionY = mInitialDownY;

                mGestureLocked = false;
                break;

            case MotionEvent.ACTION_MOVE:
                if (mActivePointerId == INVALID_POINTER) {
                    Log.e(LOG_TAG, "Got ACTION_MOVE event but don't have an active pointer id.");
                    return false;
                }

                pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    return false;
                }
                final float y = ev.getY(pointerIndex);
                startDragging(y);
                mLastMotionY = y;
                break;

            case MotionEventCompat.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsBeingDragged = false;
                mActivePointerId = INVALID_POINTER;
                break;
        }

        return mIsBeingDragged;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {

        if (mRefreshManager == null || !isEnabled() || canTargetScrollUp() || !acceptScroll() || mInNestedScrolling
                || mGestureLocked) {
            // Fail fast if we're not in a state where a refresh is possible
            return false;
        }

        final int action = MotionEventCompat.getActionMasked(ev);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mActivePointerId = ev.getPointerId(0);
                mIsBeingDragged = false;

                mInitialDownY = ev.getY(0);
                mLastMotionY = mInitialDownY;

                mGestureLocked = false;
                break;

            case MotionEvent.ACTION_MOVE: {
                final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    Log.e(LOG_TAG, "Got ACTION_MOVE event but have an invalid active pointer id.");
                    return false;
                }

                final float y = ev.getY(pointerIndex);
                startDragging(y);

                if (mIsBeingDragged) {
                    final int dy = (int) (mLastMotionY - y);
                    mRefreshManager.onConsume(dy);
                }
                mLastMotionY = y;
                break;

            }

            case MotionEventCompat.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;

            case MotionEvent.ACTION_UP: {
                final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    Log.e(LOG_TAG, "Got ACTION_UP event but don't have an active pointer id.");
                    return false;
                }
                mIsBeingDragged = false;
                mActivePointerId = INVALID_POINTER;

                if (mRefreshManager != null && mNotifyMotionEnd) {
                    mRefreshManager.stopConsume();
                }
                break;
            }

            case MotionEvent.ACTION_CANCEL:
                mIsBeingDragged = false;
                mActivePointerId = INVALID_POINTER;
                return false;
        }

        return true;
    }

    private void startDragging(float y) {
        final float yDiff = y - mInitialDownY;
        if (yDiff > mTouchSlop && !mIsBeingDragged) {
            mLastMotionY = mInitialDownY + mTouchSlop;
            mIsBeingDragged = true;
            mNotifyMotionEnd = true;
            if (mRefreshManager != null) {
                mRefreshManager.startConsume();
            }
        }
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = MotionEventCompat.getActionIndex(ev);
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mActivePointerId = ev.getPointerId(newPointerIndex);
        }
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if ((android.os.Build.VERSION.SDK_INT < 21 && mTargetView instanceof AbsListView)
                || (mTargetView != null && !ViewCompat.isNestedScrollingEnabled(mTargetView))) {
            // Nope, just to keep motion event passing.
        } else {
            super.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }

    /**
     * change refresh state.
     * it may not begin refreshing immediately as RM may need some time to prepare
     *
     * @param refreshing whether a refresh process should be began or end
     */
    public void setRefreshing(boolean refreshing) {
        if (mRefreshManager != null) {
            mRefreshManager.setRefresh(refreshing);
        }
    }

    public boolean isRefreshing() {
        return mRefreshManager != null && mRefreshManager.mIsRefreshing;
    }

    /**
     * attach a RefreshManager to this widget
     *
     * @param refreshManager refresh style controller
     */
    public void setRefreshManager(RefreshManager refreshManager) {
        if (isRefreshing() || mIsBeingDragged || mInNestedScrolling) {
            throw new IllegalStateException("cannot change RefreshManager during refreshing or scrolling");
        }
        if (mRefreshManager != refreshManager && refreshManager != null) {
            mRefreshManager = refreshManager;
            mRefreshManager.setRefreshLayout(this);
            if (mRefreshView != null) {
                //need remove old refresh view if there's one
                removeView(mRefreshView);
            }
            mRefreshView = mRefreshManager.onCreateView(this);
            addView(mRefreshView);

        }
    }

    /**
     * set listener to be notified when a refresh event is triggered
     */
    public void setOnRefreshListener(OnRefreshListener listener) {
        mRefreshListener = listener;
    }

    /**
     * set TargetScrollUpChecker.
     * if RefreshLayout direct child is not scrollable view,
     * you may use {@link TargetScrollUpListener} to tell RefreshLayout whether target can scroll up
     */
    public void setTargetScrollUpChecker(TargetScrollUpListener checker) {
        mTargetScrollUpChecker = checker;
    }


    public interface TargetScrollUpListener {
        boolean canTargetScrollUp();
    }


    @SuppressWarnings("WeakerAccess")
    public static abstract class RefreshManager {

        protected RefreshLayout mRefreshLayout;

        /**
         * is RM current in refreshing state,prepare or setting state included
         */
        protected boolean mIsRefreshing = false;

        @DrawingOrder
        public int getViewDrawingOrder() {
            return ABOVE;
        }

        void setRefreshLayout(RefreshLayout refreshLayout) {
            mRefreshLayout = refreshLayout;
        }

        /***
         * called to get RefreshView
         * @param container ViewGroup this RefreshView will be added
         * @return RefreshView
         */
        protected abstract View onCreateView(ViewGroup container);

        /**
         * is current RM state could trigger a refresh event,
         * if this return true then {@link #prepare(boolean, boolean)} will be called,
         * otherwise {@link  #finish(boolean, boolean)} will be called
         *
         * @return true if could
         */
        protected abstract boolean canMotionTriggerRefresh();

        /**
         * make preparation for refresh, eg. animate view to correct position.
         * when start to refresh,it should fire refreshing event by calling {@link #fireRefresh()},
         * then {@link OnRefreshListener} will be called if there is one.
         *
         * @param isScrolling whether this method being called during scrolling.
         * @param changed
         */
        protected abstract void prepare(boolean isScrolling, boolean changed);

        /**
         * finish refresh state, reset to its original state.
         *
         * @param isScrolling whether this method being called during scrolling.
         * @param changed
         */
        protected abstract void finish(boolean isScrolling, boolean changed);

        /**
         * RefreshLayout attached to Window
         */
        protected void onAttachedToWindow() {
        }

        /**
         * RefreshLayout detached from window
         */
        protected void onDetachedFromWindow() {
        }

        /**
         * measure target and refresh view.
         * note, target has been measured with match_parent flag before this be called,
         * normally target don't needed to be measured again.
         *
         * @param widthMeasureSpec  parent measureSpec
         * @param heightMeasureSpec parent measureSpec
         */
        public void measureTargetAndRefresh(View target, View refresh, int widthMeasureSpec, int heightMeasureSpec) {
            refresh.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        }

        public abstract void layoutTargetAndRefresh(View target, View refresh, boolean changed, int l, int t, int r, int b);

        /**
         * whether RefreshLayout should handle motion event or accept nested scroll
         *
         * @return true if should
         */
        protected boolean acceptScroll() {
            return true;
        }

        /**
         * start consume produced motion y
         */
        protected void startConsume() {
        }

        /***
         * @param dy distance in pixel of produced motion y
         * @return distance in pixel this RML consumed
         */
        protected abstract int onConsume(int dy);

        /**
         * stop consume produced motion y
         */
        protected void stopConsume() {
            //if refresh layout didn't receive any nested scroll event,
            //then no need to response
            if (mRefreshLayout.hasNestedMotion) {
                boolean canTrigger = canMotionTriggerRefresh();
                if (mIsRefreshing) {
                    if (canTrigger) {
                        prepare(true, false);
                    } else {
                        finish(true, false);
                    }
                } else {
                    setRefreshInternal(canTrigger, true, false);
                }
            }
        }

        void setRefresh(boolean refresh) {
            if (mIsRefreshing != refresh) {
                setRefreshInternal(refresh, false, false);
            }
        }

        /**
         * change refresh state
         *
         * @param refresh        new refresh state
         * @param isScrolling    is RefreshLayout currently scrolling while this method called
         * @param abortScrolling whether should abort RefreshLayout's scroll.
         *                       sometime client trigger refresh event in the middle of scrolling,
         *                       at that case RefreshLayout are supposed to filter any motion event.
         */
        protected void setRefreshInternal(boolean refresh, boolean isScrolling, boolean abortScrolling) {
            if (abortScrolling) {
                mRefreshLayout.abortScrolling();
            }
            boolean changed = mIsRefreshing ^ refresh;
            System.out.println("refresh " + refresh + " is " + mIsRefreshing + " state " + (mIsRefreshing ^ refresh)
                    + " changed " + changed);
            mIsRefreshing = refresh;
            if (mIsRefreshing) {
                prepare(isScrolling, changed);
            } else {
                finish(isScrolling, changed);
            }
        }

        protected final void fireRefresh() {
            mRefreshLayout.fireRefreshEvent();
        }

    }
}

