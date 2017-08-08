package com.myself.liveplayerpullflowdemo.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import com.myself.liveplayerpullflowdemo.R;
import com.myself.liveplayerpullflowdemo.bean.Video;
import com.myself.liveplayerpullflowdemo.ui.VideoListActivity;

import java.util.ArrayList;

public class VideoListAdapter extends BaseAdapter {

    private Context mContext;
    private ArrayList<Video> mDataFilelist;

    public VideoListAdapter(ArrayList<Video> videoList, Context context) {
        mDataFilelist = videoList;
        mContext = context;
    }

    public int getCount() {
        return mDataFilelist.size() + 1;
    }

    // getCount()和getItem(int position)方法的返回值代表了listView顶部有一个独立的item
    public Object getItem(int position) {
        return mDataFilelist.get(position - 1);
    }

    public long getItemId(int position) {
        return position;
    }

    @SuppressLint("SetTextI18n")
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder;
        if (convertView == null) {
            convertView = LayoutInflater.from(mContext).inflate(R.layout.videoitem, null);
            viewHolder = new ViewHolder();
            viewHolder.checkBox = convertView.findViewById(R.id.checkBox1);
            viewHolder.titleTV = convertView.findViewById(R.id.video_title);
            viewHolder.isLocationTV = convertView.findViewById(R.id.video_source);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        TextView mTitle = viewHolder.titleTV;
        TextView mIsLocation = viewHolder.isLocationTV;
        CheckBox checkBox = viewHolder.checkBox;

        if (position > 0) {
            Video video = mDataFilelist.get(position - 1);

            checkBox.setChecked(video.isUseHwDecoder());// 是否是硬件解码

            boolean inLoop = video.inLoopPlay();
            String sInloop = inLoop ? "looplist" : "";

            if (video.isLocation()) {
                mTitle.setText(video.getName());
                mIsLocation.setText("local" + sInloop);
            } else {
                mTitle.setText(video.getName() + "_" + video.getVideoId() + "_" + video.getDefinition());
                mIsLocation.setText("network" + sInloop);
//                checkBox.setEnabled(false);
            }
        } else {
            mTitle.setText("自定义网络视频");
            mIsLocation.setText("");
        }
        return convertView;
    }

    private static class ViewHolder {
        CheckBox checkBox;
        TextView titleTV;
        TextView isLocationTV;
    }
}
