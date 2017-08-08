package com.myself.liveplayerpullflowdemo;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;

import java.io.File;
import java.io.FileWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;


public class PlayerThreeActivity extends Activity {
    private TextView mTextView = null;
    private String result;

    private final int NTP_TIME_OUT_MILLISECOND = 1000;

    private boolean isStopCalibrate = false;
    /**
     * ntp服务器地址集
     */
    private String[] ntpServerHost = new String[]{
            "dns1.synet.edu.cn",
            "news.neu.edu.cn",
            "dns.sjtu.edu.cn",
            "dns2.synet.edu.cn",
            "ntp.glnet.edu.cn",
            "ntp-sz.chl.la",
            "ntp.gwadar.cn",
            "cn.pool.ntp.org"
    };

    private Handler mHandle = new Handler(new Handler.Callback() {
        public boolean handleMessage(Message message) {
            mTextView.setText(result);
            return false;
        }
    });

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.player_three_activity);

        mTextView = (TextView) findViewById(R.id.text);


        new Thread(new Runnable() {
            public void run() {
                File file = new File("/sdcard/time.txt");
                try {
                    FileWriter fileWriter = new FileWriter(file);

                    long start, end, time,duration,sumDuration = 0,delta,sumDelta = 0;
                    int count = 0;
                    for (int i = 0; i < 100; i++) {
                        start = System.currentTimeMillis();
                        time = getTimeFromNtpServer("time.pool.aliyun.com");
                        if(time<=0) {
                            continue;
                        } else {
                            count++;
                        }
                        end = System.currentTimeMillis();
                        duration = end - start;
                        sumDuration += duration;
                        delta = (start + end)/2 - time;
                        sumDelta += delta;
//                        result =  "start = " + start + ", end = " + end + ", time = " + time;
                        result = "index = " + i + ", duration = " + (end - start) + ", delta = " + delta  + ", time = " + time + ", start = " + start + ", end = " + end +  "\r\n";
                        fileWriter.write(result);
                        fileWriter.flush();
                        mHandle.sendEmptyMessage(0);
                        Thread.sleep(1000);
                    }
                    result = "over!!!" + " average duration = "+ sumDuration/count + ", average delta = "+ sumDelta/count;
                    mHandle.sendEmptyMessage(0);
                    fileWriter.write(result);
                    fileWriter.flush();
                    fileWriter.close();

                } catch (Throwable t) {
                    t.printStackTrace();
                }

            }
        }).start();

    }

    /**
     * 开始校准时间
     */
    public void startCalibrateTime() {
        new Thread() {
            @Override
            public void run() {
                while (!isStopCalibrate) {
                    for (int i = 0; i < ntpServerHost.length; i++) {
                        long time = getTimeFromNtpServer(ntpServerHost[i]);
                        if (time != -1) {
                            int tryCount = 3;
                            while (tryCount > 0) {
                                tryCount--;
                                boolean isSetTimeSuccessful = setCurrentTimeMillis(time);
                                if (isSetTimeSuccessful) {
                                    tryCount = 0;
                                    isStopCalibrate = true;
                                    Log.i("", "set time successful");
                                } else {
                                    Log.i("", "set time failure");
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }.start();
    }

    /**
     * 停止校准时间
     */
    public void stopCalibrateTime() {
        isStopCalibrate = true;
    }

    /**
     * 从ntp服务器中获取时间 * * @param ntpHost ntp服务器域名地址 * @return 如果失败返回-1，否则返回当前的毫秒数
     */
    private long getTimeFromNtpServer(String ntpHost) {
        Log.i("", "get time from " + ntpHost);
        SntpClient client = new SntpClient();
        boolean isSuccessful = client.requestTime(ntpHost, NTP_TIME_OUT_MILLISECOND);
        if (isSuccessful) {
            return client.getNtpTime();
        }
        return -1;
    }


    /**
     * 设置当前的系统时间 * * @param time * @return true表示设置成功, false表示设置失败
     */
    public boolean setCurrentTimeMillis(long time) {
        return false;
    }

    public static class SntpClient {
        private static final String TAG = "SntpClient";

        private static final int REFERENCE_TIME_OFFSET = 16;
        private static final int ORIGINATE_TIME_OFFSET = 24;
        private static final int RECEIVE_TIME_OFFSET = 32;
        private static final int TRANSMIT_TIME_OFFSET = 40;
        private static final int NTP_PACKET_SIZE = 48;

        private static final int NTP_PORT = 123;
        private static final int NTP_MODE_CLIENT = 3;
        private static final int NTP_VERSION = 3;

        // Number of seconds between Jan 1, 1900 and Jan 1, 1970
        // 70 years plus 17 leap days
        private static final long OFFSET_1900_TO_1970 = ((365L * 70L) + 17L) * 24L * 60L * 60L;

        // system time computed from NTP server response
        private long mNtpTime;

        // value of SystemClock.elapsedRealtime() corresponding to mNtpTime
        private long mNtpTimeReference;

        // round trip time in milliseconds
        private long mRoundTripTime;

        /**
         * Sends an SNTP request to the given host and processes the response. * * @param host host name of the server. * @param timeout network timeout in milliseconds. * @return true if the transaction was successful.
         */
        public boolean requestTime(String host, int timeout) {
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket();
                socket.setSoTimeout(timeout);
                InetAddress address = InetAddress.getByName(host);
                byte[] buffer = new byte[NTP_PACKET_SIZE];
                DatagramPacket request = new DatagramPacket(buffer, buffer.length, address, NTP_PORT);

                // set mode = 3 (client) and version = 3
                // mode is in low 3 bits of first byte
                // version is in bits 3-5 of first byte
                buffer[0] = NTP_MODE_CLIENT | (NTP_VERSION << 3);

                // get current time and write it to the request packet
                long requestTime = System.currentTimeMillis();
                long requestTicks = SystemClock.elapsedRealtime();
                writeTimeStamp(buffer, TRANSMIT_TIME_OFFSET, requestTime);

                socket.send(request);

                // read the response
                DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                socket.receive(response);
                long responseTicks = SystemClock.elapsedRealtime();
                long responseTime = requestTime + (responseTicks - requestTicks);

                // extract the results
                long originateTime = readTimeStamp(buffer, ORIGINATE_TIME_OFFSET);
                long receiveTime = readTimeStamp(buffer, RECEIVE_TIME_OFFSET);
                long transmitTime = readTimeStamp(buffer, TRANSMIT_TIME_OFFSET);
                long roundTripTime = responseTicks - requestTicks - (transmitTime - receiveTime);
                // receiveTime = originateTime + transit + skew
                // responseTime = transmitTime + transit - skew
                // clockOffset = ((receiveTime - originateTime) + (transmitTime - responseTime))/2
                // = ((originateTime + transit + skew - originateTime) +
                // (transmitTime - (transmitTime + transit - skew)))/2
                // = ((transit + skew) + (transmitTime - transmitTime - transit + skew))/2
                // = (transit + skew - transit + skew)/2
                // = (2 * skew)/2 = skew
                long clockOffset = ((receiveTime - originateTime) + (transmitTime - responseTime)) / 2;
                // if (false) Log.d(TAG, "round trip: " + roundTripTime + " ms");
                // if (false) Log.d(TAG, "clock offset: " + clockOffset + " ms");

                // save our results - use the times on this side of the network latency
                // (response rather than request time)
                mNtpTime = responseTime + clockOffset;
                mNtpTimeReference = responseTicks;
                mRoundTripTime = roundTripTime;
            } catch (Exception e) {
                if (false) Log.d(TAG, "request time failed: " + e);
                return false;
            } finally {
                if (socket != null) {
                    socket.close();
                }
            }

            return true;
        }

        /**
         * Returns the time computed from the NTP transaction. * * @return time value computed from NTP server response.
         */
        public long getNtpTime() {
            return mNtpTime;
        }

        /**
         * Returns the reference clock value (value of SystemClock.elapsedRealtime()) * corresponding to the NTP time. * * @return reference clock corresponding to the NTP time.
         */
        public long getNtpTimeReference() {
            return mNtpTimeReference;
        }

        /**
         * Returns the round trip time of the NTP transaction * * @return round trip time in milliseconds.
         */
        public long getRoundTripTime() {
            return mRoundTripTime;
        }

        /**
         * Reads an unsigned 32 bit big endian number from the given offset in the buffer.
         */
        private long read32(byte[] buffer, int offset) {
            byte b0 = buffer[offset];
            byte b1 = buffer[offset + 1];
            byte b2 = buffer[offset + 2];
            byte b3 = buffer[offset + 3];

            // convert signed bytes to unsigned values
            int i0 = ((b0 & 0x80) == 0x80 ? (b0 & 0x7F) + 0x80 : b0);
            int i1 = ((b1 & 0x80) == 0x80 ? (b1 & 0x7F) + 0x80 : b1);
            int i2 = ((b2 & 0x80) == 0x80 ? (b2 & 0x7F) + 0x80 : b2);
            int i3 = ((b3 & 0x80) == 0x80 ? (b3 & 0x7F) + 0x80 : b3);

            return ((long) i0 << 24) + ((long) i1 << 16) + ((long) i2 << 8) + (long) i3;
        }

        /**
         * Reads the NTP time stamp at the given offset in the buffer and returns * it as a system time (milliseconds since January 1, 1970).
         */
        private long readTimeStamp(byte[] buffer, int offset) {
            long seconds = read32(buffer, offset);
            long fraction = read32(buffer, offset + 4);
            return ((seconds - OFFSET_1900_TO_1970) * 1000) + ((fraction * 1000L) / 0x100000000L);
        }

        /**
         * Writes system time (milliseconds since January 1, 1970) as an NTP time stamp * at the given offset in the buffer.
         */
        private void writeTimeStamp(byte[] buffer, int offset, long time) {
            long seconds = time / 1000L;
            long milliseconds = time - seconds * 1000L;
            seconds += OFFSET_1900_TO_1970;

            // write seconds in big endian format
            buffer[offset++] = (byte) (seconds >> 24);
            buffer[offset++] = (byte) (seconds >> 16);
            buffer[offset++] = (byte) (seconds >> 8);
            buffer[offset++] = (byte) (seconds >> 0);

            long fraction = milliseconds * 0x100000000L / 1000L;
            // write fraction in big endian format
            buffer[offset++] = (byte) (fraction >> 24);
            buffer[offset++] = (byte) (fraction >> 16);
            buffer[offset++] = (byte) (fraction >> 8);
            // low order bits should be random data
            buffer[offset++] = (byte) (Math.random() * 255.0);
        }
    }

}