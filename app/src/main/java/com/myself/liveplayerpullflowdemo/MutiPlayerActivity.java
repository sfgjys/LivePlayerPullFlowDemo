package com.myself.liveplayerpullflowdemo;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.SurfaceView;
import com.alivc.player.AliVcMediaPlayer;

/*多个播放器界面*/
public class MutiPlayerActivity extends Activity {
    public static final String URL = "http://livecdn.video.taobao.com/temp/test1466295255657-65e172e6-1b96-4660-9f2f-1aba576d84e8.m3u8";

    private AliVcMediaPlayer mPlayerOne, mPlayerTwo, mPlayerThree;
    private SurfaceView mSurfaceViewOne, mSurfaceViewTwo, mSurfaceViewThree;

    private Handler mHandler;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi_player);
        mSurfaceViewOne = (SurfaceView) findViewById(R.id.PlayerOne);
        mSurfaceViewTwo = (SurfaceView) findViewById(R.id.PlayerTwo);
        mSurfaceViewThree = (SurfaceView) findViewById(R.id.PlayerThree);
        mSurfaceViewOne.setZOrderOnTop(true);
        mSurfaceViewTwo.setZOrderOnTop(true);
        mSurfaceViewThree.setZOrderOnTop(true);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mHandler = new Handler();

        mHandler.postDelayed(new Runnable() {
            public void run() {
                startPlayerOne();
            }
        }, 1000);

        mHandler.postDelayed(new Runnable() {
            public void run() {
                startPlayerTwo();
            }
        }, 5000);

        mHandler.postDelayed(new Runnable() {
            public void run() {
                startPlayerThree();
            }
        }, 10000);
    }

    private void startPlayerOne() {
        mPlayerOne = new AliVcMediaPlayer(getApplicationContext(), mSurfaceViewOne);
        mPlayerOne.prepareAndPlay(URL);
    }

    private void startPlayerTwo() {
        mPlayerTwo = new AliVcMediaPlayer(getApplicationContext(), mSurfaceViewTwo);
        mPlayerTwo.prepareAndPlay(URL);
    }

    private void startPlayerThree() {
        mPlayerThree = new AliVcMediaPlayer(getApplicationContext(), mSurfaceViewThree);
        mPlayerThree.prepareAndPlay(URL);
    }

}