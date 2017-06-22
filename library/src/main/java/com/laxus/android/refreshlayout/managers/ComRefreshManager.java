package com.laxus.android.refreshlayout.managers;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.laxus.android.refreshlayout.R;
import com.laxus.android.refreshlayout.view.ArrowDrawable;
import com.laxus.android.refreshlayout.view.LineSpinLoadingDrawable;


public class ComRefreshManager extends ComRefreshManagerBase {
    private ImageView mHintImageView;
    private TextView mHintTextView;

    private LineSpinLoadingDrawable mSpinDrawable;
    private ArrowDrawable mArrowDrawable;

    private boolean mWaitingRelease;

    private int mTriggerOffset = -1;

    @Override
    protected View createRefreshView(ViewGroup container) {
        View view = LayoutInflater.from(container.getContext())
                .inflate(R.layout.common_refresh_view, container, false);
        mHintImageView = (ImageView) view.findViewById(R.id.image_view);
        mHintTextView = (TextView) view.findViewById(R.id.text_view);

        mHintTextView.setText("下拉刷新");
        mArrowDrawable = new ArrowDrawable(mHintImageView);
        mHintImageView.setImageDrawable(mArrowDrawable);
        return view;
    }

    @Override
    protected void onScroll(int offset) {
        if (mTriggerOffset == -1) {
            mTriggerOffset = getRefreshTriggerDistance();
        }
        if (Math.abs(offset) > mTriggerOffset) {
            if (!mWaitingRelease) {
                mHintTextView.setText("释放刷新");
                mArrowDrawable.toggle();
                mWaitingRelease = true;
            }
        } else {
            if (mWaitingRelease) {
                mHintTextView.setText("下拉刷新");
                mArrowDrawable.toggle();
                mWaitingRelease = false;
            }
        }
    }

    @Override
    protected void onRefreshing() {
        if (mSpinDrawable == null) {
            mSpinDrawable = new LineSpinLoadingDrawable(mHintImageView);
        }
        mHintImageView.setImageDrawable(mSpinDrawable);
        mSpinDrawable.start();
        mHintTextView.setText("刷新中...");
    }

    @Override
    protected void onReset() {
        mWaitingRelease = false;
        mHintTextView.setText("下拉刷新");
        if (mSpinDrawable != null && mSpinDrawable.isRunning()) {
            mSpinDrawable.stop();
        }
        if (mArrowDrawable.isRunning()) {
            mArrowDrawable.stop();
        }
        mHintImageView.setImageDrawable(mArrowDrawable);
    }
}
