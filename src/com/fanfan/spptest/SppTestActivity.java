package com.fanfan.spptest;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.util.Log;
import java.util.*;

public class SppTestActivity extends Activity {
    public static final int MSG_SHOW_NOTHING    = 1;
    public static final int MSG_SHOW_CONNECTING = 2;
    public static final int MSG_SHOW_CONNECTED0 = 3;
    public static final int MSG_SHOW_CONNECTED1 = 4;
    public static final int MSG_SHOW_FAILED     = 5;
    public static final int MSG_SHOW_RETRY      = 6;

    private static final String TAG = "SppTestActivity";

    private Button   mBtnPair;
    private Button   mBtnUnPair;
    private Button   mBtnConnect;
    private Button   mBtnDisConn;
    private Button   mBtnStartAuto;
    private Button   mBtnStopAuto;
    private EditText mEditorMac;
    private TextView mTxtBtMsg;
    private BluetoothAdapter mBtAdapter;
    private BluetoothSocket  mBtSocket;
    private BtConnectManager mBtConnMan;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mBtnPair      = (Button  )findViewById(R.id.btn_pair              );
        mBtnUnPair    = (Button  )findViewById(R.id.btn_unpair            );
        mBtnConnect   = (Button  )findViewById(R.id.btn_connect           );
        mBtnDisConn   = (Button  )findViewById(R.id.btn_disconnect        );
        mBtnStartAuto = (Button  )findViewById(R.id.btn_start_auto_connect);
        mBtnStopAuto  = (Button  )findViewById(R.id.btn_stop_auto_connect );
        mEditorMac    = (EditText)findViewById(R.id.edt_bt_mac            );
        mTxtBtMsg     = (TextView)findViewById(R.id.txt_bt_msg            );
        mBtnPair     .setOnClickListener(mOnClickListener);
        mBtnUnPair   .setOnClickListener(mOnClickListener);
        mBtnConnect  .setOnClickListener(mOnClickListener);
        mBtnDisConn  .setOnClickListener(mOnClickListener);
        mBtnStartAuto.setOnClickListener(mOnClickListener);
        mBtnStopAuto .setOnClickListener(mOnClickListener);
        mEditorMac   .setText("00:13:43:20:1E:C5");

        // create bt connect manager
        mBtConnMan = new BtConnectManager(this);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        mBtConnMan.destroy();
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    private View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
            case R.id.btn_pair:
                mBtConnMan.pair(mEditorMac.getText().toString());
                break;
            case R.id.btn_unpair:
                mBtConnMan.unpair(mEditorMac.getText().toString());
                break;
            case R.id.btn_connect:
                mBtConnMan.connect(mEditorMac.getText().toString());
                break;
            case R.id.btn_disconnect:
                mBtConnMan.disconnect();
                break;
            case R.id.btn_start_auto_connect:
                mBtConnMan.startAutoReconnect(mEditorMac.getText().toString());
                break;
            case R.id.btn_stop_auto_connect:
                mBtConnMan.stopAutoReconnect();
                break;
            }
        }
    };

    public Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SHOW_NOTHING: {
                    mTxtBtMsg.setText("");
                    break;
                }
                case MSG_SHOW_CONNECTING: {
                    Log.d(TAG, "connect...");
                    mTxtBtMsg.setText("connecting...");
                    break;
                }
                case MSG_SHOW_CONNECTED0: {
                    Log.d(TAG, "connected spp...");
                    mTxtBtMsg.setText("spp connected, socket not connected !");
                    break;
                }
                case MSG_SHOW_CONNECTED1: {
                    Log.d(TAG, "connected socket...");
                    mTxtBtMsg.setText("spp and socket both connected !");
                    break;
                }
                case MSG_SHOW_FAILED: {
                    Log.d(TAG, "connect failed...");
                    mTxtBtMsg.setText("connect failed !");
                    break;
                }
                case MSG_SHOW_RETRY: {
                    Log.d(TAG, "wait 5s and retry connect...");
                    mTxtBtMsg.setText("wait 5s and retry connect...");
                    break;
                }
            }
        }
    };
}

class BtConnectManager extends Thread
{
    private static final String TAG = "BtConnectManager";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int TRY_RECONNECT_PERIOD = 5 * 1000;

    private SppTestActivity  mActivity    = null;
    private BluetoothAdapter mBtAdapter   = null;
    private BluetoothDevice  mBtDev       = null;
    private BluetoothSocket  mBtSocket    = null;
    private Object           mStartEvent  = new Object();
    private boolean          mStartFlag   = false;
    private boolean          mExitFlag    = false;
    private boolean          mConnected   = false;
    private String           mAutoMac     = null;

    public BtConnectManager(SppTestActivity activity) {
        this.start(); // start auto reconnect thread
        mActivity  = activity;
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairlist = mBtAdapter.getBondedDevices();
        for (BluetoothDevice device : pairlist) {
            Log.d(TAG, "name: " + device.getName() + ", mac: " + device.getAddress());
        }
    }

    public void destroy() {
        mExitFlag = true;
        synchronized (mStartEvent) {
            mStartFlag = false;
            mStartEvent.notify();
        }
        // wait thread exited
        try { join(); } catch (Exception e) { e.printStackTrace(); }
        disconnect(); // disconnect
    }

    public void pair(String mac) {
        BluetoothDevice btdev = mBtAdapter.getRemoteDevice(mac);
        btdev.createBond();
    }

    public void unpair(String mac) {
        BluetoothDevice btdev = mBtAdapter.getRemoteDevice(mac);
        btdev.removeBond();
    }

    public void connect(String mac) {
        int result = SppTestActivity.MSG_SHOW_CONNECTING;

        Log.d(TAG, "++ connect " + mac);
        mActivity.mHandler.sendEmptyMessage(result);

        BluetoothDevice btdev = mBtAdapter.getRemoteDevice(mac);
        try {
            if (mBtSocket != null) mBtSocket.close();
            Log.d(TAG, "BT connection setup spp.");
            mBtSocket = btdev.createRfcommSocketToServiceRecord(SPP_UUID);
        } catch (Exception e) {
            result = SppTestActivity.MSG_SHOW_FAILED;
            Log.e(TAG, "Socket creation failed.", e);
        }

        // cancel discovery
//      mBtAdapter.cancelDiscovery();

        try {
            Log.d(TAG, "BT connection established, data transfer link open.");
            mBtSocket.connect();
        } catch (Exception e1) {
            Log.d(TAG, "execute mBtSocket.connect() failed !", e1);
            result = SppTestActivity.MSG_SHOW_FAILED;
            try {
                mBtSocket.close();
            } catch (Exception e2) {
                Log.e(TAG, "Unable to close socket during connection failure", e2);
            }
        }

        if (result != SppTestActivity.MSG_SHOW_FAILED) {
            Log.d(TAG, "do spp connect0 successed !");
            result = SppTestActivity.MSG_SHOW_CONNECTED0;
        }
        mActivity.mHandler.sendEmptyMessage(result);
        try { sleep(500, 0); } catch (Exception e) {}

        // need do bluetooth spp socket communications to make connection
        // todo..
        if (result == SppTestActivity.MSG_SHOW_CONNECTED0) {
            // do socket communications
            // todo..

            // update mConnected flag if connect success or not
            // todo..

            // update result
            // todo..
        }
        mActivity.mHandler.sendEmptyMessage(result);
        try { sleep(500, 0); } catch (Exception e) {}

        Log.d(TAG, "-- connect " + mac);
    }

    public void disconnect() {
        try {
            mBtSocket.close();
        } catch (Exception e) {
            Log.e(TAG, "Unable to close socket.", e);
        }
    }

    public void startAutoReconnect(String mac) {
        synchronized (mStartEvent) {
            mAutoMac   = mac;
            mStartFlag = true;
            mStartEvent.notify();
        }
    }

    public void stopAutoReconnect() {
        mAutoMac   = null;
        mStartFlag = false;
    }

    @Override
    public void run() {
        Log.d(TAG, "auto reconnect thread enter");
        while (!mExitFlag) {
            Log.d(TAG, "auto reconnect thread is running...");

            synchronized (mStartEvent) { // wait for start event
                try { mStartEvent.wait(TRY_RECONNECT_PERIOD); } catch (Exception e) { e.printStackTrace(); }
            }

            if (mStartFlag && !mConnected) {
                connect(mAutoMac); // try to connect 
            }

            if (mStartFlag) {
                mActivity.mHandler.sendEmptyMessage(SppTestActivity.MSG_SHOW_RETRY);
            }
            else {
                mActivity.mHandler.sendEmptyMessage(SppTestActivity.MSG_SHOW_NOTHING);
            }
        }

        Log.d(TAG, "BtConnectManager thread exited !");
    }
}

