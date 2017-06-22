package com.laxus.android.refreshlayoutdemo;

import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.laxus.android.refreshlayout.RefreshLayout;
import com.laxus.android.refreshlayout.managers.ComRefreshManager;
import com.laxus.android.refreshlayout.managers.SwipeRefreshManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class RVActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rv);

        final RefreshLayout refreshLayout = (RefreshLayout) findViewById(R.id.refresh_layout);
        final RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recycler_view);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        final RefreshAdapter adapter = new RefreshAdapter();
        recyclerView.setAdapter(adapter);

        refreshLayout.setRefreshManager(getRefreshManager(getIntent().getStringExtra("managerName")));
        refreshLayout.setOnRefreshListener(new RefreshLayout.OnRefreshListener() {
            @Override
            public void onRefreshing() {
                refreshLayout.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        refreshLayout.setRefreshing(false);
                        adapter.addDataSet();
                    }
                }, 10000);
            }
        });
        recyclerView.postDelayed(new Runnable() {
            @Override
            public void run() {
                refreshLayout.setRefreshing(true);
            }
        }, 300);
    }

    private RefreshLayout.RefreshManager getRefreshManager(String name) {
        switch (name) {
            case "SwipeRefreshManager":
                return new SwipeRefreshManager(this);
            case "ComRefreshManager":
                return new ComRefreshManager();
        }
        return null;
    }

    static class RefreshAdapter extends RecyclerView.Adapter<RefreshHolder> {

        private List<String> mDataSet = new ArrayList<>();

        public RefreshAdapter() {
            for (int i = 0; i < 16; ++i) {
                mDataSet.add("index " + (15 - i));
            }
        }

        public void addDataSet() {
            for (int i = 0; i < 3; ++i) {
                mDataSet.add(0, "index " + (mDataSet.size() + 1));
            }
            notifyDataSetChanged();
        }

        private Random mRandom = new Random();

        @Override
        public RefreshHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.rv_item, parent, false);
            return new RefreshHolder(view);
        }

        @Override
        public void onBindViewHolder(RefreshHolder holder, int position) {
            holder.itemView.setBackgroundColor(Color.rgb(mRandom.nextInt(255), mRandom.nextInt(255), mRandom.nextInt(255)));
            holder.textView.setText(mDataSet.get(position));
        }

        @Override
        public int getItemCount() {
            return mDataSet.size();
        }
    }

    static class RefreshHolder extends RecyclerView.ViewHolder {

        public TextView textView;

        public RefreshHolder(View itemView) {
            super(itemView);
            textView = (TextView) itemView.findViewById(R.id.text_view);
        }
    }

}
