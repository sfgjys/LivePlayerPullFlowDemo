
package com.myself.liveplayerpullflowdemo;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;
import android.view.*;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.widget.*;

import com.alivc.player.AliVcMediaPlayer;
import com.alivc.player.MediaPlayer;
import com.alivc.player.NDKCallback;
import com.myself.liveplayerpullflowdemo.port.StatusListener;

import java.util.List;

/*播放界面，提供播放展示和操作*/
public class PlayerActivity extends Activity {

    public static final String TAG = "PlayerActivity";

    public static final int STATUS_START = 1;
    public static final int STATUS_STOP = 2;
    public static final int STATUS_PAUSE = 3;
    public static final int STATUS_RESUME = 4;

    public static final int CMD_START = 1;
    public static final int CMD_STOP = 2;
    public static final int CMD_PAUSE = 3;
    public static final int CMD_RESUME = 4;
    public static final int CMD_VOLUME = 5;
    public static final int CMD_SEEK = 6;

    public static final int TEST = 0;


    private AliVcMediaPlayer mPlayer = null;
    private SurfaceHolder mSurfaceHolder = null;
    private SurfaceView mSurfaceView = null;

    private SeekBar mSeekBar = null;
    private TextView mTipView = null;
    private TextView mCurDurationView = null;
    private TextView mErrInfoView = null;
    private TextView mDecoderTypeView = null;
    private LinearLayout mTipLayout = null;

    private boolean mEnableUpdateProgress = true;
    private int mLastPercent = -1;
    private int mPlayingIndex = -1;
    private StringBuilder msURI = new StringBuilder("");
    private StringBuilder msTitle = new StringBuilder("");
    private GestureDetector mGestureDetector;
    private int mPosition = 0;
    private int mVolumn = 50;
    private MediaPlayer.VideoScalingMode mScalingMode = MediaPlayer.VideoScalingMode.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING;
    private boolean mMute = false;

    private PlayerControl mPlayerControl = null;

    private PowerManager.WakeLock mWakeLock = null;

    private StatusListener mStatusListener = null;


    // 标记播放器是否已经停止
    private boolean isStopPlayer = false;
    // 标记播放器是否已经暂停
    private boolean isPausePlayer = false;
    private boolean isPausedByUser = false;
    //用来控制应用前后台切换的逻辑
    private boolean isCurrentRunningForeground = true;


    // ***********************************************************当wifi切换到4g时,提示用户是否需要继续播放***************************************************************************
    private boolean isLastWifiConnected = false;
    // 重点:发生从wifi切换到4g时,提示用户是否需要继续播放,此处有两种做法:
    // 1.从历史位置从新播放
    // 2.暂停播放,因为存在网络切换,续播有时会不成功
    private BroadcastReceiver connectionReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            // 主要管理和网络连接相关的操作
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            // getNetworkInfo API23废弃
            NetworkInfo mobNetInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);// 手机数据流量网络状态
            NetworkInfo wifiNetInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);// wifi网络状态

            // isConnected表示网络连接是否存在，是否有可能建立连接和传递数据。一句话是不是可以使用网络
            Log.d(TAG, "mobile " + mobNetInfo.isConnected() + " wifi " + wifiNetInfo.isConnected());


            if (!isLastWifiConnected && wifiNetInfo.isConnected()) {
                // 链接wifi网络

                isLastWifiConnected = true;
            }
            if (isLastWifiConnected && mobNetInfo.isConnected() && !wifiNetInfo.isConnected()) {
                // 链接手机流量

                isLastWifiConnected = false;

                // TODO 在由wifi变为数据流量时走
                if (mPlayer != null) {
                    mPosition = mPlayer.getCurrentPosition();
                    // 重点:新增接口,此处必须要将之前的surface释放掉
                    mPlayer.releaseVideoSurface();
                    mPlayer.stop();
                    mPlayer.destroy();
                    mPlayer = null;
                }
                dialog();

            }
        }
    };

    protected void dialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(PlayerActivity.this);
        builder.setMessage("正在使用数据流量，确认继续播放吗？");

        builder.setTitle("提示");

        builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                // TODO
                initSurface();
            }
        });
        builder.setNegativeButton("退出", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                // 退出本界面
                dialog.dismiss();
                PlayerActivity.this.finish();
            }
        });
        builder.create().show();
    }
    // -------------------------------------------------------------------------------------------------------------------------------------------

    // 设置播放状态监听
    void setStatusListener(StatusListener listener) {
        mStatusListener = listener;
    }

    private PlayerControl.ControllerListener mController = new PlayerControl.ControllerListener() {

        @Override
        public void notifyController(int cmd, int extra) {
            Message msg = Message.obtain();
            switch (cmd) {
                case PlayerControl.CMD_PAUSE:
                    msg.what = CMD_PAUSE;
                    break;
                case PlayerControl.CMD_RESUME:
                    msg.what = CMD_RESUME;
                    break;
                case PlayerControl.CMD_SEEK:
                    msg.what = CMD_SEEK;
                    msg.arg1 = extra;
                    break;
                case PlayerControl.CMD_START:
                    msg.what = CMD_START;
                    break;
                case PlayerControl.CMD_STOP:
                    msg.what = CMD_STOP;
                    break;
                case PlayerControl.CMD_VOLUME:
                    msg.what = CMD_VOLUME;
                    msg.arg1 = extra;

                    break;

                default:
                    break;

            }

            if (TEST != 0) {
                mTimerHandler.sendMessage(msg);
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 注册一个网络状态变化监听的广播,用于wifi切换倒4g时的提示
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(connectionReceiver, intentFilter);

        setContentView(R.layout.activity_play);

        // TODO mPlayingIndex有什么用
        mPlayingIndex = -1;

        // TODO TEST初始化值为0
        if (TEST == 1) {
            mPlayerControl = new PlayerControl(this);
            mPlayerControl.setControllerListener(mController);
        }

        acquireWakeLock();

        initView();
    }

    // ***************************************************************************************************************************
    // 获取锁来让屏幕常量
    private void acquireWakeLock() {
        if (mWakeLock == null) {
            // PowerManager用来控制电源状态的.
            PowerManager powerManager = (PowerManager) this.getSystemService(POWER_SERVICE);
            // PowerManager.SCREEN_BRIGHT_WAKE_LOCK在Android3.2(API 13)后被废弃，使用FLAG_KEEP_SCREEN_ON代替。持锁将保持屏幕背光为最大亮度，而键盘背光可以熄灭。按下Power键后，此锁将会被系统自动释放，释放后屏幕与CPU均关闭。
            mWakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "SmsSyncService.sync() wakelock.");
        }
        mWakeLock.acquire();
    }

    // 释放锁
    private void releaseWakeLock() {
        mWakeLock.release();
        mWakeLock = null;
    }
    // -----------------------------------------------------------------------------------------------------------------------------

    // *************************************************************************************************************************
    // 初始化控件
    private void initView() {
        mTipLayout = findViewById(R.id.LayoutTip);
        mSeekBar = findViewById(R.id.progress);
        mTipView = findViewById(R.id.text_tip);
        mCurDurationView = findViewById(R.id.current_duration);
        mErrInfoView = findViewById(R.id.error_info);
        mDecoderTypeView = findViewById(R.id.decoder_type);

        initSurface();
    }
    // -------------------------------------------------------------------------------------------------------------------

    // ************************************************************对SurfaceView进行初始化和属性设置*****************************************************************

    /**
     * 重点:初始化播放器使用的SurfaceView,此处的SurfaceView采用动态添加
     */
    private boolean initSurface() {
        // 获取控件
        FrameLayout frameContainer = findViewById(R.id.GLViewContainer);
        // 设置背景
        frameContainer.setBackgroundColor(Color.rgb(0, 0, 0));

        // 创建SurfaceView控件
        mSurfaceView = new SurfaceView(this);

        mGestureDetector = new GestureDetector(this, new MyGestureListener());

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);

        // 为避免重复添加,事先remove子view
        frameContainer.removeAllViews();
        frameContainer.addView(mSurfaceView);

        mSurfaceView.setZOrderOnTop(false);// 设置SurfaceView不在Activity最顶层

        mSurfaceView.setOnTouchListener(new View.OnTouchListener() {
            private long mLastDownTimestamp = 0;

            @Override
            public boolean onTouch(View v, MotionEvent event) {

                // 如果GestureDetector消费了TouchEvenr事件，则返回true
                if (mGestureDetector.onTouchEvent(event))
                    return true;

                // 按下触摸事件
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    // 按下时获取时间戳
                    mLastDownTimestamp = System.currentTimeMillis();
                    return true;
                }

                // TODO SurfaceView抬起触摸事件
                if (event.getAction() == MotionEvent.ACTION_UP) {

                    if (mPlayer != null && !mPlayer.isPlaying() && mPlayer.getDuration() > 0) {
                        start();
                        return false;
                    }
                    //just show the progress bar  判断按下在抬起的间隔时间超过200ms
                    if ((System.currentTimeMillis() - mLastDownTimestamp) > 200) {
                        show_progress_ui(true);
                        mTimerHandler.postDelayed(mUIRunnable, 3000);
                        return true;

                    } else {
                        if (mPlayer != null && mPlayer.getDuration() > 0)
                            pause();
                    }
                    return false;
                }

                return false;
            }
        });

        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(mSurfaceHolderCB);

        // mPlayingIndex初始值-1  msURI和msTitle初始值为""  方法内获取播放地址存储在了msURI内
        mPlayingIndex = getVideoSourcePath(mPlayingIndex, msURI, msTitle);

        return true;
    }

    // TODO  SurfaceView的触摸事件中有可能被调用
    private class MyGestureListener extends SimpleOnGestureListener {

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                float distanceX, float distanceY) {

            final double FLING_MIN_DISTANCE = 0.5;
            final double FLING_MIN_VELOCITY = 0.5;

            if (e1.getY() - e2.getY() > FLING_MIN_DISTANCE
                    && Math.abs(distanceY) > FLING_MIN_VELOCITY) {
                onVolumeSlide(1);
            }
            if (e1.getY() - e2.getY() < FLING_MIN_DISTANCE
                    && Math.abs(distanceY) > FLING_MIN_VELOCITY) {
                onVolumeSlide(-1);
            }

            return super.onScroll(e1, e2, distanceX, distanceY);
        }
    }

    // 音量滑动
    private void onVolumeSlide(int vol) {
        if (mPlayer != null) {
            mVolumn += vol;
            if (mVolumn > 100)
                mVolumn = 100;
            if (mVolumn < 0)
                mVolumn = 0;
            mPlayer.setVolume(mVolumn);
        }
    }

    private SurfaceHolder.Callback mSurfaceHolderCB = new SurfaceHolder.Callback() {
        @SuppressWarnings("deprecation")
        public void surfaceCreated(SurfaceHolder holder) {
            // ???????????????????????????
            holder.setType(SurfaceHolder.SURFACE_TYPE_GPU);
            // 当surface被显示的时候是否启用或禁用屏幕保持打开状态，默认是禁用，允许屏幕关闭，启用选项有效时，可以安全的调用任何线程。
            holder.setKeepScreenOn(true);

            Log.d(TAG, "AlivcPlayer onSurfaceCreated.");

            // 重点:
            if (mPlayer != null) {
                // 对于从后台切换到前台,需要重设surface;部分手机锁屏也会做前后台切换的处理
                mPlayer.setVideoSurface(mSurfaceView.getHolder().getSurface());
            } else {
                // 创建并启动播放器
                startToPlay();
            }

            if (mPlayerControl != null)
                mPlayerControl.start();
            Log.d(TAG, "AlivcPlayeron SurfaceCreated over.");
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            Log.d(TAG, "onSurfaceChanged is valid ? " + holder.getSurface().isValid());
            if (mPlayer != null)
                mPlayer.setSurfaceChanged();// TODO
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.d(TAG, "onSurfaceDestroy.");
            if (mPlayer != null) {
                mPlayer.releaseVideoSurface();// TODO
            }
        }
    };
    // --------------------------------------------------------------------------------------------------------------------------------------------

    // **********************************************获取从上一个Activity中传递过来的播放url地址等信息**********************************************
    private int getVideoSourcePath(int curIndex, StringBuilder sURI, StringBuilder sTitle) {
        //清空所有信息
        sURI.delete(0, sURI.length());
        sTitle.delete(0, sTitle.length());

        Bundle bundle = getIntent().getExtras();
        int selected = -1;
        if (curIndex == -1) { //我们所选择的播放视频
            sTitle.append(bundle.getString("TITLE"));
            sURI.append(bundle.getString("URI"));
        }

        // 多视频循环地址获取
        Bundle loopBundle = bundle.getBundle("loopList");
        if (loopBundle != null) {
            int count = loopBundle.getInt("ItemCount");
            if (curIndex == -1) {
                selected = loopBundle.getInt("SelectedIndex");
            } else {
                selected = curIndex + 1;
                selected = (selected == count ? 0 : selected);
                sURI.append(loopBundle.getString("URI" + selected));
                sTitle.append(loopBundle.getString("TITLE" + selected));
            }
        }

        return selected;
    }
    // ------------------------------------------------------------------------------------------------------------------------------------------------

    // ****************************************************************准备开始播发，初始化播放器****************************************************************
    private boolean startToPlay() {
        Log.d(TAG, "start play.");

        // 重置ui
        mSeekBar.setProgress(0);
        show_pause_ui(false, false);
        show_progress_ui(false);
        mErrInfoView.setText("");

        if (mPlayer == null) {
            //  ★★★★★★★★★★★★★★★★★★初始化播放器★★★★★★★★★★★★★★★★★★★★
            // 创建player对象
            mPlayer = new AliVcMediaPlayer(this, mSurfaceView);
            // 播放器就绪事件
            mPlayer.setPreparedListener(new VideoPreparedListener());
            // 异常错误事件
            mPlayer.setErrorListener(new VideoErrorListener());
            // 信息状态监听事件
            mPlayer.setInfoListener(new VideoInfolistener());
            // seek结束事件（备注：直播无seek操作）
            mPlayer.setSeekCompleteListener(new VideoSeekCompletelistener());
            // 播放结束事件
            mPlayer.setCompletedListener(new VideoCompletelistener());
            // 画面大小变化事件
            mPlayer.setVideoSizeChangeListener(new VideoSizeChangelistener());
            // 缓冲信息更新事件
            mPlayer.setBufferingUpdateListener(new VideoBufferUpdatelistener());
            // 停止事件
            mPlayer.setStopedListener(new VideoStoppedListener());

            // 如果同时支持软解和硬解是有用 ？？？？？？？？？？？？？？？？？？？？
            Bundle bundle = getIntent().getExtras();

            // 解码器类型。0代表硬件解码器；1代表软件解码器。
            // 备注：默认为软件解码。由于android手机硬件适配性的问题，很多android手机的硬件解码会有问题，所以，我们建议尽量使用软件解码。
            mPlayer.setDefaultDecoder(1);

            // 重点: 在调试阶段可以使用以下方法打开native log
            mPlayer.enableNativeLog();

            // mPosition初始值为0
            if (mPosition != 0) {
                // 跳转到指定位置前的第一个关键帧的位置。
                mPlayer.seekTo(mPosition);
            }
        }

        // 设置标题
        TextView vt = findViewById(R.id.video_title);
        vt.setText(msTitle.toString());
        vt.setVisibility(View.VISIBLE);

        // 准备开始播放
        mPlayer.prepareAndPlay(msURI.toString());

        //播放加密视频使用如下：？？？？？？？？？？？？？？？？？？？？？
        // VidSource vidSource = new VidSource();
        // vidSource.setVid("视频id");
        // vidSource.setAcId("你的accessKeyId");
        // vidSource.setAcKey("你的accessKeySecret");
        // vidSource.setStsToken("你的STS token");
        // vidSource.setDomainRegion("你的domain");
        // vidSource.setAuthInfo(你的authinfo");
        // mPlayer.prepareAndPlayWithVid(vidSource);

        if (mStatusListener != null)
            mStatusListener.notifyStatus(STATUS_START);// 通知状态为开始

        // 5秒后显示是硬解码还是软解码
        new Handler().postDelayed(new Runnable() {
            public void run() {
                mDecoderTypeView.setText(NDKCallback.getDecoderType() == 0 ? "HardDeCoder" : "SoftDecoder");
            }
        }, 5000);
        return true;
    }
    // --------------------------------------------------------------------------------------------------------------------------------------

    // ***************************************************界面左上角的三个按钮的点击事件***********************************************************************
    public void switchScalingMode(View view) {
        if (mPlayer != null) {
            if (mScalingMode == MediaPlayer.VideoScalingMode.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING) {
                mPlayer.setVideoScalingMode(MediaPlayer.VideoScalingMode.VIDEO_SCALING_MODE_SCALE_TO_FIT);
                mScalingMode = MediaPlayer.VideoScalingMode.VIDEO_SCALING_MODE_SCALE_TO_FIT;
            } else {
                mPlayer.setVideoScalingMode(MediaPlayer.VideoScalingMode.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                mScalingMode = MediaPlayer.VideoScalingMode.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING;
            }
        }
    }

    public void switchMute(View view) {
        if (mPlayer != null) {
            if (!mMute) {
                mMute = true;
                mPlayer.setMuteMode(true);
            } else {
                mMute = false;
                mPlayer.setMuteMode(false);
            }
        }
    }

    public void reStart(View view) {
        if (mPlayer != null) {
            mPlayer.stop();
            new Thread(new Runnable() {
                public void run() {

                    while (!isStopPlayer) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    mPlayer.prepareAndPlay(msURI.toString());
                }
            }).start();
        }
    }
    // ----------------------------------------------------------------------------------------------------------------------------------------


    private void update_progress(int ms) {
        if (mEnableUpdateProgress) {
            mSeekBar.setProgress(ms);
        }
    }

    private void update_second_progress(int ms) {
        if (mEnableUpdateProgress) {
            mSeekBar.setSecondaryProgress(ms);
        }
    }

    private void show_progress_ui(boolean bShowPause) {
        LinearLayout progress_layout = findViewById(R.id.progress_layout);
        TextView video_title = findViewById(R.id.video_title);
        if (bShowPause) {
            progress_layout.setVisibility(View.VISIBLE);
            video_title.setVisibility(View.VISIBLE);
        } else {
//            progress_layout.setVisibility(View.GONE);
//            video_title.setVisibility(View.GONE);
        }
    }

    private void show_pause_ui(boolean bShowPauseBtn, boolean bShowReplayBtn) {
        LinearLayout layout = findViewById(R.id.buttonLayout);
        if (!bShowPauseBtn && !bShowReplayBtn) {
            layout.setVisibility(View.GONE);
        } else {
            layout.setVisibility(View.VISIBLE);
        }
        ImageView pause_view = findViewById(R.id.pause_button);
        pause_view.setVisibility(bShowPauseBtn ? View.VISIBLE : View.GONE);

        Button replay_btn = findViewById(R.id.replay_button);
        replay_btn.setVisibility(bShowReplayBtn ? View.VISIBLE : View.GONE);
    }

    private int show_tip_ui(boolean bShowTip, float percent) {

        int vnum = (int) (percent);
        vnum = vnum > 100 ? 100 : vnum;

        mTipLayout.setVisibility(bShowTip ? View.VISIBLE : View.GONE);
        mTipView.setVisibility(bShowTip ? View.VISIBLE : View.GONE);

        if (mLastPercent < 0) {
            mLastPercent = vnum;
        } else if (vnum < mLastPercent) {
            vnum = mLastPercent;
        } else {
            mLastPercent = vnum;
        }

        String strValue = String.format("Buffering(%1$d%%)...", vnum);
        mTipView.setText(strValue);

        if (!bShowTip) { //hide it, then we need reset the percent value here.
            mLastPercent = -1;
        }

        return vnum;
    }

    private void show_buffering_ui(boolean bShowTip) {

        mTipLayout.setVisibility(bShowTip ? View.VISIBLE : View.GONE);
        mTipView.setVisibility(bShowTip ? View.VISIBLE : View.GONE);

        String strValue = "Buffering...";
        mTipView.setText(strValue);
    }

    private void update_total_duration(int ms) {
        int var = (int) (ms / 1000.0f + 0.5f);
        int min = var / 60;
        int sec = var % 60;
        TextView total = findViewById(R.id.total_duration);
        total.setText("" + min + ":" + sec);


        SeekBar sb = findViewById(R.id.progress);
        sb.setMax(ms);
        sb.setKeyProgressIncrement(10000); //5000ms = 5sec.
        sb.setProgress(0);
        sb.setSecondaryProgress(0); //reset progress now.

        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            public void onProgressChanged(SeekBar seekBar, int i, boolean fromuser) {
                int var = (int) (i / 1000.0f + 0.5f);
                int min = var / 60;
                int sec = var % 60;
                String strCur = String.format("%1$d:%2$d", min, sec);
                mCurDurationView.setText(strCur);
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
                mEnableUpdateProgress = false;
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
                int ms = seekBar.getProgress();
                mPlayer.seekTo(ms);
            }
        });

    }

    private void report_error(String err, boolean bshow) {
        if (mErrInfoView.getVisibility() == View.GONE && !bshow) {
            return;
        }
        mErrInfoView.setVisibility(bshow ? View.VISIBLE : View.GONE);
        mErrInfoView.setText(err);
        mErrInfoView.setTextColor(Color.RED);
    }


    public void switchSurface(View view) {
        if (mPlayer != null) {
            // release old surface;
            mPlayer.releaseVideoSurface();
            mSurfaceHolder.removeCallback(mSurfaceHolderCB);
            FrameLayout frameContainer = findViewById(R.id.GLViewContainer);
            frameContainer.removeAllViews();

            // init surface
            LinearLayout linearLayout = findViewById(R.id.surface_view_container);
            mSurfaceView = new SurfaceView(this);
//            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
//            params.gravity = Gravity.CENTER;
//            mSurfaceView.setLayoutParams(params);
            linearLayout.addView(mSurfaceView);

            mSurfaceHolder = mSurfaceView.getHolder();
            mSurfaceHolder.addCallback(mSurfaceHolderCB);
        }
    }


    //pause the video
    private void pause() {
        if (mPlayer != null) {
            mPlayer.pause();
            isPausePlayer = true;
            isPausedByUser = true;
            if (mStatusListener != null)
                mStatusListener.notifyStatus(STATUS_PAUSE);
            show_pause_ui(true, false);
            show_progress_ui(true);
        }
    }

    //start the video
    private void start() {

        if (mPlayer != null) {
            isPausePlayer = false;
            isPausedByUser = false;
            isStopPlayer = false;
            mPlayer.play();
            if (mStatusListener != null)
                mStatusListener.notifyStatus(STATUS_RESUME);
            show_pause_ui(false, false);
            show_progress_ui(false);
        }
    }

    //stop the video 
    private void stop() {
        Log.d(TAG, "AudioRender: stop play");
        if (mPlayer != null) {
            mPlayer.stop();
            if (mStatusListener != null)
                mStatusListener.notifyStatus(STATUS_STOP);
            mPlayer.destroy();
            mPlayer = null;
        }
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "AudioRender: onDestroy.");
        if (mPlayer != null) {
//            stop();
            mTimerHandler.removeCallbacks(mRunnable);
        }

        releaseWakeLock();

        // 解除注册的网络状态变化监听广播
        if (connectionReceiver != null) {
            unregisterReceiver(connectionReceiver);
        }


        // 重点:在 activity destroy的时候,要停止播放器并释放播放器
        if (mPlayer != null) {
            mPosition = mPlayer.getCurrentPosition();
            stop();
            if (mPlayerControl != null)
                mPlayerControl.stop();
        }

        super.onDestroy();
    }

    @Override
    protected void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();

        // 重点:如果播放器是从锁屏/后台切换到前台,那么调用player.stat
        if (mPlayer != null && !isStopPlayer && isPausePlayer) {
            if (!isPausedByUser) {
                isPausePlayer = false;
                mPlayer.play();
                // 更新ui
                show_pause_ui(false, false);
                show_progress_ui(false);
            }
        }
    }

    @Override
    protected void onStart() {
        Log.e(TAG, "onStart.");
        super.onStart();
        if (!isCurrentRunningForeground) {
            Log.d(TAG, ">>>>>>>>>>>>>>>>>>>切到前台 activity process");
        }
    }

    @Override
    protected void onPause() {
        Log.e(TAG, "onPause." + isStopPlayer + " " + isPausePlayer + " " + (mPlayer == null));
        super.onPause();
        // 重点:播放器没有停止,也没有暂停的时候,在activity的pause的时候也需要pause
        if (!isStopPlayer && !isPausePlayer && mPlayer != null) {
            Log.e(TAG, "onPause mpayer.");
            mPlayer.pause();
            isPausePlayer = true;
        }
    }

    @Override
    protected void onStop() {
        Log.e(TAG, "onStop.");
        super.onStop();

        isCurrentRunningForeground = isRunningForeground();
        if (!isCurrentRunningForeground) {
            Log.d(TAG, ">>>>>>>>>>>>>>>>>>>切到后台 activity process");
        }
    }

    private Handler mTimerHandler = new Handler() {
        public void handleMessage(Message msg) {
            System.out.println();
            switch (msg.what) {

                case CMD_PAUSE:
                    pause();
                    break;
                case CMD_RESUME:
                    start();
                    break;
                case CMD_SEEK:
                    mPlayer.seekTo(msg.arg1);
                    break;
                case CMD_START:
                    startToPlay();
                    break;
                case CMD_STOP:
                    stop();
                    break;
                case CMD_VOLUME:
                    mPlayer.setVolume(msg.arg1);
                    break;
                default:
                    break;
            }
        }
    };
    Runnable mRunnable = new Runnable() {
        @Override
        public void run() {

            if (mPlayer != null && mPlayer.isPlaying())
                update_progress(mPlayer.getCurrentPosition());

            mTimerHandler.postDelayed(this, 1000);
        }
    };

    Runnable mUIRunnable = new Runnable() {
        @Override
        public void run() {
            show_progress_ui(false);
        }
    };


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            isStopPlayer = true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // 重点:判定是否在前台工作
    public boolean isRunningForeground() {
        ActivityManager activityManager = (ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appProcessInfos = activityManager.getRunningAppProcesses();
        // 枚举进程
        for (ActivityManager.RunningAppProcessInfo appProcessInfo : appProcessInfos) {
            if (appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                if (appProcessInfo.processName.equals(this.getApplicationInfo().processName)) {
                    Log.d(TAG, "EntryActivity isRunningForeGround");
                    return true;
                }
            }
        }
        Log.d(TAG, "EntryActivity isRunningBackGround");
        return false;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

//        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
//        params.gravity = Gravity.CENTER;
//        mSurfaceView.setLayoutParams(params);
    }


    // TODO *********************************************************各种监听回调对象*********************************************************

    /**
     * 准备完成监听器:调度更新进度
     */
    private class VideoPreparedListener implements MediaPlayer.MediaPlayerPreparedListener {

        @Override
        public void onPrepared() {
            Log.d(TAG, "onPrepared");
            if (mPlayer != null) {
                mPlayer.setVideoScalingMode(MediaPlayer.VideoScalingMode.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                update_total_duration(mPlayer.getDuration());
                mTimerHandler.postDelayed(mRunnable, 1000);
                show_progress_ui(true);
                mTimerHandler.postDelayed(mUIRunnable, 3000);
            }
        }
    }


    /**
     * 错误处理监听器
     */
    private class VideoErrorListener implements MediaPlayer.MediaPlayerErrorListener {

        public void onError(int what, int extra) {
            int errCode;

            if (mPlayer == null) {
                return;
            }

            errCode = mPlayer.getErrorCode();
            switch (errCode) {
                case MediaPlayer.ALIVC_ERR_LOADING_TIMEOUT:
                    report_error("缓冲超时,请确认网络连接正常后重试", true);
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_NO_INPUTFILE:
                    report_error("no input file", true);
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_NO_VIEW:
                    report_error("no surface", true);
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_INVALID_INPUTFILE:
                    report_error("视频资源或者网络不可用", true);
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_NO_SUPPORT_CODEC:
                    report_error("no codec", true);
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_FUNCTION_DENIED:
                    report_error("no priority", true);
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_UNKNOWN:
                    report_error("unknown error", true);
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_NO_NETWORK:
                    report_error("视频资源或者网络不可用", true);
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_ILLEGALSTATUS:
                    report_error("illegal call", true);
                    break;
                case MediaPlayer.ALIVC_ERR_NOTAUTH:
                    report_error("auth failed", true);
                    break;
                case MediaPlayer.ALIVC_ERR_READD:
                    report_error("资源访问失败,请重试", true);
                    mPlayer.reset();
                    break;
                default:
                    break;

            }
        }
    }

    /**
     * 信息通知监听器:重点是缓存开始/结束
     */
    private class VideoInfolistener implements MediaPlayer.MediaPlayerInfoListener {

        public void onInfo(int what, int extra) {
            Log.d(TAG, "onInfo what = " + what + " extra = " + extra);
            System.out.println();
            switch (what) {
                case MediaPlayer.MEDIA_INFO_UNKNOW:
                    break;
                case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                    //pause();
                    show_buffering_ui(true);
                    break;
                case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                    //start();
                    show_buffering_ui(false);
                    break;
                case MediaPlayer.MEDIA_INFO_TRACKING_LAGGING:
                    break;
                case MediaPlayer.MEDIA_INFO_NETWORK_ERROR:
                    report_error("�������!", true);
                    break;
                case MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                    if (mPlayer != null)
                        Log.d(TAG, "on Info first render start : " + ((long) mPlayer.getPropertyDouble(AliVcMediaPlayer.FFP_PROP_DOUBLE_1st_VFRAME_SHOW_TIME, -1) - (long) mPlayer.getPropertyDouble(AliVcMediaPlayer.FFP_PROP_DOUBLE_OPEN_STREAM_TIME, -1)));

                    break;
            }
        }
    }

    /**
     * 快进完成监听器
     */
    private class VideoSeekCompletelistener implements MediaPlayer.MediaPlayerSeekCompleteListener {

        public void onSeekCompleted() {
            mEnableUpdateProgress = true;
        }
    }

    /**
     * 视频播完监听器
     */
    private class VideoCompletelistener implements MediaPlayer.MediaPlayerCompletedListener {

        public void onCompleted() {
            Log.d(TAG, "onCompleted.");

            AlertDialog.Builder builder = new AlertDialog.Builder(PlayerActivity.this);
            builder.setMessage("播放结束");

            builder.setTitle("提示");


            builder.setNegativeButton("退出", new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    PlayerActivity.this.finish();
                }
            });

            builder.create().show();
        }
    }

    /**
     * 视频大小变化监听器
     */
    private class VideoSizeChangelistener implements MediaPlayer.MediaPlayerVideoSizeChangeListener {

        public void onVideoSizeChange(int width, int height) {
            Log.d(TAG, "onVideoSizeChange width = " + width + " height = " + height);
        }
    }

    /**
     * 视频缓存变化监听器: percent 为 0~100之间的数字】
     */
    private class VideoBufferUpdatelistener implements MediaPlayer.MediaPlayerBufferingUpdateListener {

        public void onBufferingUpdateListener(int percent) {

        }
    }

    /**
     * 视频停止监听器
     */
    private class VideoStoppedListener implements MediaPlayer.MediaPlayerStopedListener {
        @Override
        public void onStopped() {
            Log.d(TAG, "onVideoStopped.");
            isStopPlayer = true;
        }
    }
    // --------------------------------------------------------------------------------------------------------------

}
