package com.myself.liveplayerpullflowdemo.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.myself.liveplayerpullflowdemo.MutiPlayerActivity;
import com.myself.liveplayerpullflowdemo.PlayerTwoActivity;
import com.myself.liveplayerpullflowdemo.R;

/*主界面*/
public class MainActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void openDemoOne(View view){
        startActivity(new Intent(this, VideoListActivity.class));
    }

    public void openDemoTwo(View view) {
        startActivity(new Intent(this,PlayerTwoActivity.class));
    }

    public void openDemoThree(View view) {
        startActivity(new Intent(this,MutiPlayerActivity.class));
    }
}