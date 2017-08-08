
package com.myself.liveplayerpullflowdemo.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;

import com.myself.liveplayerpullflowdemo.FileChooserActivity;
import com.myself.liveplayerpullflowdemo.PlayerActivity;
import com.myself.liveplayerpullflowdemo.R;
import com.myself.liveplayerpullflowdemo.adapter.VideoListAdapter;
import com.myself.liveplayerpullflowdemo.bean.Video;

import java.io.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoListActivity extends Activity {

    // 前缀标题
    private String mPrefixTitle = "";

    // sd卡中的文件夹路径
    private final String mRootDir = "/mnt/sdcard/aliyun";

    //  listview的数据源
    private ArrayList<Video> videoListData = new ArrayList<>();

    // 上下文
    private Context mContext = VideoListActivity.this;

    private int mSelectedPosition = -1;
    // private final int MSG_SHOW_SELECTION = 1;

    // 静态类不持有外部类的对象，所以你的Activity可以随意被回收
    private static class MyHandler extends Handler {

        WeakReference<VideoListActivity> weakReference;

        MyHandler(VideoListActivity videoListActivity) {
            weakReference = new WeakReference<>(videoListActivity);
        }

        @Override
        public void handleMessage(Message msg) {
            VideoListActivity videoListActivity = weakReference.get();
            // 将在FilelistRefreshThread线程中解析出来的数据放入videoListData集合中，并更新listview
            if (msg.what == 0) {
                videoListActivity.videoListData.addAll(videoListActivity.mRemoteList);
                videoListActivity.mVideoListAdapter.notifyDataSetChanged();
            }
            // 将列表跳转至顶部item
            if (msg.what == 1) {
                videoListActivity.mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
                if (videoListActivity.mSelectedPosition < 0)
                    videoListActivity.mSelectedPosition = 0;
                videoListActivity.mListView.setSelection(videoListActivity.mSelectedPosition);
                videoListActivity.mListView.setItemChecked(videoListActivity.mSelectedPosition, true);
            }
            super.handleMessage(msg);
        }
    }


    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 给mPrefixTitle赋值本应用的版本名称
        acquireVersion();

        // 无标题栏
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_videolist);

        initFindViews();

        new FilelistRefreshThread().start();

        fileChooserIntent = new Intent(this, FileChooserActivity.class);
    }

    // *************************************************给mPrefixTitle赋值本应用的版本名称**************************************************************
    public void acquireVersion() {
        try {
            PackageManager packageManager = getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(getPackageName(), 0);
            mPrefixTitle = packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
        }
    }
    // ------------------------------------------------------------------------------------------------------------------------------------------------------

    // *******************************************初始化控件并设置控件属性*****************************************************************

    private ListView mListView;
    private VideoListAdapter mVideoListAdapter;

    private void initFindViews() {
        mVideoListAdapter = new VideoListAdapter(videoListData, mContext);

        mListView = findViewById(R.id.player_one_fileListView);
        mListView.setAdapter(mVideoListAdapter);

        mListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                // TODO
                if (position > 0) {
                    Video v = videoListData.get(position - 1);
                    CheckBox checkBox = view.findViewById(R.id.checkBox1);
                    // isChecked代表是否是硬件解码，而在适配器中，根据数据Video的useHwDecoder变量已经设置了isChecked
                    boolean isChecked = checkBox.isChecked();
                    startPlayer(v, isChecked);
                } else {
                    dialog();// 顶部item弹出对话框输入播放地址url
                }
            }
        });

        mListView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            // 对选择的item进行ui更新修改
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
                mSelectedPosition = position;
                decorateItem(view, position);
                updateTitle(position + 1, videoListData.size());
            }

            public void onNothingSelected(AdapterView<?> adapterView) {
                if (videoListData.size() > 0) {
                    updateTitle(0, videoListData.size());
                } else {
                    updateTitle(0, 0);
                }
                mSelectedPosition = -1;
            }
        });
    }

    // ···············对选择的item进行ui更新修改··············································
    private View mLastSeletedItemView = null;

    private void decorateItem(View view, int position) {
        if (mLastSeletedItemView != null) {
            TextView pre = mLastSeletedItemView.findViewById(R.id.video_title);
            pre.setTextColor(Color.BLACK);
            mLastSeletedItemView.setBackgroundColor(Color.WHITE);
        }
        TextView t = view.findViewById(R.id.video_title);
        t.setTextColor(Color.WHITE);
        view.setBackgroundColor(Color.BLUE);
        mLastSeletedItemView = view;
    }

    private void updateTitle(int selected, int total_item_count) {
        TextView titleView = findViewById(R.id.listViewTitle);
        titleView.setText("videolist [ " + selected + "/" + total_item_count + " ] - (v" + mPrefixTitle + ")");
    }
    // ····································································

    // ---------------------------------------------------------------------------------------------------------------------------

    // ***************************************************弹出对话框，并播放设置url，确认后跳转至播放界面*****************************************************
    protected void dialog() {
        final EditText inputServer = new EditText(this);
        AlertDialog.Builder builder = new AlertDialog.Builder(VideoListActivity.this);
        builder.setIcon(android.R.drawable.ic_dialog_info).setView(inputServer);
        builder.setMessage("确认继续播放吗？");
        builder.setTitle("提示");
        builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                Intent intent = new Intent();
                Bundle bundle = new Bundle();

                // TODO 本界面的核心代码
                // 传递给播放界面的三个数据
                bundle.putString("TITLE", "自定义视频");
                bundle.putString("URI", inputServer.getText().toString());
                bundle.putInt("decode_type", 1);

                intent.putExtras(bundle);
                intent.setClass(mContext, PlayerActivity.class);
                startActivity(intent);
            }
        });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.create().show();
    }
    // -----------------------------------------------------------------------------------------------------------------------------------------------------

    private Intent fileChooserIntent;
    //  和startActivityForResult(fileChooserIntent, REQUEST_CODE);配合使用的
    private static final int REQUEST_CODE = 1;   //request code
    public static final String EXTRA_FILE_CHOOSER = "file_chooser";

    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (resultCode == RESULT_CANCELED) {
            //toast(getText(R.string.open_file_none));
            return;
        }
        if (resultCode == RESULT_OK && requestCode == REQUEST_CODE) {
            //aquire path
            String videoPath = data.getStringExtra(EXTRA_FILE_CHOOSER);
            //Log.v(TAG, "onActivityResult # pptPath : "+ pptPath );
            if (videoPath != null) {
                Video video = new Video();
                int pos = videoPath.lastIndexOf("/");
                String name = videoPath.substring(pos + 1);
                video.setName(name);
                video.setUri(videoPath);
                startPlayer(video, false);
            }
        }
    }

    // 跳转直播放界面进行播发
    private void startPlayer(Video video, boolean hardwareCodec) {

        // 开启本地视频文件播放选择界面
        if ("aquire path".equals(video.getName())) {
            startActivityForResult(fileChooserIntent, REQUEST_CODE);
            return;
        }

        Intent intent = new Intent();
        Bundle bundle = new Bundle();

        // TODO 本界面的核心代码
        bundle.putString("TITLE", video.getName());
        bundle.putString("URI", video.getUri());
        bundle.putInt("decode_type", video.isUseHwDecoder() ? 0 : 1);

        // 判断是否要多部视频循环播放---------------------这里没什么用
        if (video.inLoopPlay()) {
            int selectedIndex = 0;
            Bundle loopBundle = new Bundle();
            bundle.putBundle("loopList", loopBundle);
            int k = 0;

            for (Video v : videoListData) {
                if (v.inLoopPlay()) {
                    loopBundle.putString("TITLE" + k, v.getName());
                    loopBundle.putString("URI" + k, v.getUri());

                    if (v.getUri().equals(video.getUri())) {
                        selectedIndex = k;
                    }
                    k++;
                }
            }
            loopBundle.putInt("ItemCount", k);
            loopBundle.putInt("SelectedIndex", selectedIndex);
        }

        intent.putExtras(bundle);
        intent.setClass(mContext, PlayerActivity.class);
        startActivity(intent);

        mListView.setVisibility(View.INVISIBLE);
    }

    protected void onResume() {
        super.onResume();
        mListView.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    // ********************************************************************解析sd卡中的文件获取视频来源数据集合***************************************************************
    //    AtomicBoolean对象中的Boolean值的变化的时候不允许在之间插入，保持操作的原子性
    private AtomicBoolean isFirst = new AtomicBoolean(false);
    //   最终包含了链接网络的视频和本地的视频
    private List<Video> mRemoteList = new ArrayList<>();

    private MyHandler myHandler = new MyHandler(this);

    private class FilelistRefreshThread extends Thread {

        public void run() {
            // 判断是否已经首次开启线程
            if (isFirst.get()) {
                return;
            }
            init();
            mRemoteList = getRemoteVideoList();// 链接网络的视频
            List<Video> localList = getLocationVideoList(mRootDir);// 本地的视频
            mRemoteList.addAll(localList);

            myHandler.sendMessage(myHandler.obtainMessage(0));
            myHandler.sendMessageDelayed(myHandler.obtainMessage(1), 100);// 100后发送消息

            isFirst.set(Boolean.TRUE);
        }

        // TODO 为方便测试不同的手机平台的下对h264 ＋ aac的硬解的支持率添加,之后需要删除
        private void init() {
            File rootPath = new File(mRootDir);
            if (!rootPath.exists()) {
                rootPath.mkdir();
            }

            File videoListFile = new File(mRootDir, "videolist.txt");
            // 如果在application中没有写入videolist.txt文件，就在这里写入一个
            if (!videoListFile.exists()) {
                try {
                    FileWriter fileWriter = new FileWriter(videoListFile);
                    fileWriter.write("rtmp[标清] 3 hd rtmp://tan.cdnpe.com/app-test/video-test_sd");
                    fileWriter.flush();
                    fileWriter.close();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }

    // 将videoList.txt文件中的数据进行解析转换为ArrayList<Video>数据集合
    private ArrayList<Video> getRemoteVideoList() {

        ArrayList<Video> videoList = new ArrayList<>();

        // File.separator 在 UNIX 系统上，此字段的值为 '/'；在 Microsoft Windows 系统上，它为 '\'。
        String listFile = mRootDir + File.separator + "videoList.txt";
        String SPACE_CHAR = "\\s+";//  正则表达式:  \\s 表示空格  + 号表示一个或多个的意思,

        File file = new File(listFile);
        if (!file.exists()) {// 如果videoList.txt不存在就返回一个空数据
            return videoList;
        }

        InputStreamReader reader;
        Closeable resource = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
            BufferedReader bufferedReader = new BufferedReader(reader);
            resource = bufferedReader;
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                String[] content = line.split(SPACE_CHAR);
                Log.d("VideoList", "Line = " + line);
                Log.d("VideoList", "content length = " + content.length);
                if (content != null && content.length >= 4) {
                    String title = content[0];
                    String id = content[1];
                    String definition = content[2];
                    String url = content[3];

                    Video video = new Video();
                    if (content.length > 4) {
                        String hw = content[4];
                        int t = Integer.parseInt(hw);
                        if (t == 1) {
                            video.setUseHwDecoder(false);
                        }
                    }
                    video.setName(title);
                    video.setVideoId(Long.valueOf(id));
                    video.setDefinition(definition);
                    video.setUri(url);
                    video.setLocation(Boolean.FALSE);
                    videoList.add(video);
                }
            }
            bufferedReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                resource.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return videoList;
    }

    // 获取sd卡中的视频文件的数据集合
    private List<Video> getLocationVideoList(String rootPath) {
        List<Video> videoList = new ArrayList<>();

        // 文件夹不存在，返回空数据
        File dir = new File(mRootDir);
        if (!dir.isDirectory()) {
            return videoList;
        }

        // 文件夹下没有文件存在，返回空数据
        File[] files = dir.listFiles();
        if (null == files) {
            return videoList;
        }

        // 遍历文件夹下的文件，查看是否有视频文件可以播放
        for (File file : files) {
            if (file.isDirectory()) {
                continue;
            } else {
                String fullPath = file.getAbsolutePath().toLowerCase();
                String prefix = getExtension(fullPath);
                if (getVideoFilter().contains(prefix)) {
                    File f = new File(fullPath);
                    String fileName = f.getName();
                    long size = f.length();
                    Video video = new Video(fileName, fullPath, "",
                            (int) size, Boolean.TRUE);
                    videoList.add(video);
                }
            }
        }
        return videoList;
    }

    // 截取文件的格式字符串
    private static String getExtension(String filename) {
        if ((filename != null) && (filename.length() > 0)) {
            int dot = filename.lastIndexOf('.');
            if ((dot > -1) && (dot < (filename.length() - 1))) {
                return filename.substring(dot + 1);
            }
        }
        return filename;
    }

    //  返回包含了各种播放格式的HashSet集合
    private static HashSet<String> getVideoFilter() {
        HashSet<String> fileFilter = new HashSet<>();
        fileFilter.add("mp4");
        fileFilter.add("mkv");
        fileFilter.add("flv");
        fileFilter.add("wmv");
        fileFilter.add("ts");
        fileFilter.add("rm");
        fileFilter.add("rmvb");
        fileFilter.add("webm");
        fileFilter.add("mov");
        fileFilter.add("vstream");
        fileFilter.add("mpeg");
        fileFilter.add("f4v");
        fileFilter.add("avi");
        fileFilter.add("mkv");
        fileFilter.add("ogv");
        fileFilter.add("dv");
        fileFilter.add("divx");
        fileFilter.add("vob");
        fileFilter.add("asf");
        fileFilter.add("3gp");
        fileFilter.add("h264");
        fileFilter.add("hevc");
        fileFilter.add("h261");
        fileFilter.add("h263");
        fileFilter.add("m3u8");
        fileFilter.add("avs");
        fileFilter.add("swf");
        fileFilter.add("m4v");
        fileFilter.add("mpg");
        return fileFilter;
    }
    // ------------------------------------------------------------------------------------------------------------------------
}
