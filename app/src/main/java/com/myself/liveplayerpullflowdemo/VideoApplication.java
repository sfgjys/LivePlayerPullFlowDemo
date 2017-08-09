package com.myself.liveplayerpullflowdemo;

import android.app.Application;
import android.widget.Toast;

import com.alivc.player.AccessKey;
import com.alivc.player.AccessKeyCallback;
import com.alivc.player.AliVcMediaPlayer;

import java.io.*;

/*  本类主要是将assets中两个文件拷贝倒sd卡中，并且从其中一个文件中获取播放器初始化所需要的id和密钥，进行初始化播放器  */
public class VideoApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // 检查/mnt/sdcard/TAOBAOPLAYER 是否存在,不存在创建，判断SD卡中是否存在aliyun文件夹
        File rootPath = new File("/mnt/sdcard/aliyun");// TODO SD卡的路径有可能变化
        if (!rootPath.exists()) {
            System.out.println("创建aliyun文件夹" + (rootPath.mkdir() ? "成功" : "夹失败"));

        }

        // 判断aliyun文件夹下是否存在videolist.txt文件,不存在复制
        File videolistFile = new File(rootPath, "videolist.txt");
        if (!videolistFile.exists()) {
            try {
                // 拷贝文件
                copyAssetsToSD("videolist.txt", videolistFile.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // 判断aliyun文件夹下是否存在accesstoken.txt文件 是否存在,不存在复制
        File assessKeyFile = new File(rootPath, "accesstoken.txt");
        if (!assessKeyFile.exists()) {
            try {
                // 拷贝文件
                copyAssetsToSD("accesstoken.txt", assessKeyFile.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        File file = new File("/mnt/sdcard/aliyun/accesstoken.txt");
        if (file.exists()) {
            // accesstoken.txt文件存在，读取文件中的文本
            try {
                BufferedReader fileReader = new BufferedReader(new FileReader(file));
                // 访问Key的ID
                final String accessKeyId = fileReader.readLine();
                // 访问Key的密钥
                final String accessKeySecret = fileReader.readLine();
                // 业务ID，用户自行设置，用于标识使用播放器sdk的APP。如“淘宝直播”就设置“TaobaoLive”。
                final String businessId = "kuku_live";



            } catch (Throwable t) {
                t.printStackTrace();
            }
        } else {
            Toast.makeText(getApplicationContext(), "accesstoken.txt不存在，拷贝失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyAssetsToSD(String assetsFile, String sdFile) throws IOException {
        InputStream myInput;
        OutputStream myOutput = new FileOutputStream(sdFile);
        // 从assets文件夹下获取assetsFile文件的输入流
        myInput = this.getAssets().open(assetsFile);
        byte[] buffer = new byte[1024];
        int length = myInput.read(buffer);
        while (length > 0) {
            myOutput.write(buffer, 0, length);
            length = myInput.read(buffer);
        }
        // 将输入流写入sdFile路径下
        myOutput.flush();
        myInput.close();
        myOutput.close();
    }
}
