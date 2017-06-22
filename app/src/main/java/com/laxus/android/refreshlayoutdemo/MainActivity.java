package com.laxus.android.refreshlayoutdemo;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    public static final String[] MANAGER_NAMES = new String[]
            {
                    "SwipeRefreshManager",
                    "ComRefreshManager"
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ListView listView = (ListView) findViewById(R.id.list_view);
        listView.setAdapter(new ListAdapter());
    }

    class ListAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return MANAGER_NAMES.length;
        }

        @Override
        public Object getItem(int position) {
            return MANAGER_NAMES[position];
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ListHolder holder;
            if (convertView == null) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.list_manager_name_item, parent, false);
                convertView = view;
                holder = new ListHolder(view);
                convertView.setTag(holder);
            } else {
                holder = (ListHolder) convertView.getTag();
            }
            holder.nameView.setText(MANAGER_NAMES[position]);
            holder.position = position;
            return convertView;
        }
    }

    class ListHolder {
        View itemView;
        TextView nameView;

        int position;

        public ListHolder(View itemView) {
            this.itemView = itemView;
            nameView = (TextView) itemView.findViewById(R.id.name);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ListHolder holder = (ListHolder) v.getTag();
                    String name = MANAGER_NAMES[holder.position];
                    Intent intent = new Intent(v.getContext(), RVActivity.class);
                    intent.putExtra("managerName", name);
                    v.getContext().startActivity(intent);
                }
            });
        }
    }
}
