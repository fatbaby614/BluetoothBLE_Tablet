/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothlegatt;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    private Path mPath;				//路径
    public Paint mPaint = null;		//画笔
    private Bitmap mCacheBitmap = null;		//定义一个内存中的图片，该图片将作为缓冲区
    private Paint mBitmapPaint;
    private  Canvas mCacheCanvas = null;		//定义cacheBitmap上的Canvas对象

    private long btTime;
    private MyView dv ;
    private Context ct;

    private long lastX,lastY,lastPenPressure;
    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    public final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                byte [] data = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data) {
                    stringBuilder.append(String.format("%02X ", byteChar));
                }
                displayData(stringBuilder.toString());
                byte [] data1 = new byte[10];
                long nowTime = System.currentTimeMillis();
               // Log.i(TAG,String.format("bt time:%d ms",nowTime - btTime));
                btTime = nowTime;
                if (data.length >=10)
                {
                    data1= Arrays.copyOfRange(data,0,10);
                    int  sumcheck = (byte) ( data[2]+data1[3]+data1[4]+data1[5]+data1[6]+data1[7]+data1[8] ) & 0xff;
                    long posX=0,posY=0,penPressure=0;
                    posX = ((data1[3] &0xff) | ((data1[4] << 8) & 0xff00)) ;
                    posY = ((data1[5] &0xff) | ((data1[6] << 8) & 0xff00)) ;
                    penPressure = ((data1[7] & 0xff) | ((data1[8] << 8) & 0xff00))  ;
                    final int penStatus = data1[2] & 0x0f;
//                    Log.i(TAG,String.format("%02x %02x %02x %02x %02x %02x %02x %02x %02x %02x",
//                            data1[0],data1[1],data1[2],data1[3],data1[4],data1[5],data1[6],data1[7],data1[8],data1[9]));
                  //  Log.i(TAG,String.format("%04x %04x %04x %02x %02x", posX,posY,penPressure,penStatus,sumcheck));
                    if (penPressure>0) {
                        dv.DrawLine(lastX , lastY , lastPenPressure, posX , posY , penPressure);
                    }
                    lastX = posX;
                    lastY = posY;
                    lastPenPressure = penPressure;
  //                  Log.i(TAG,String.format("B %2x ", penStatus) + " " + posX + " " + posY + " " + penPressure );
                   // mCacheCanvas.drawPoint(posX,posY,mPaint);
//                    mPath.addRect(posX,posY,posX+10,posY+10, Path.Direction.CW);
                }
            }
        }
    };

//    public void initCanvas(){
//        //创建一个与该view相同大小的缓存区,Config.ARGB_8888 --> 一种32位的位图,意味着有四个参数,即A,R,G,B,每一个参数由8bit来表示.
//        mCacheBitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888);
//        mCacheCanvas = new Canvas();		//创建一个新画布
//        mCacheCanvas.setBitmap(mCacheBitmap);		//在cacheCanvas上绘制cacheBitmap
////        cacheCanvas.drawColor(Color.WHITE);
//        mPaint = new Paint(Paint.DITHER_FLAG);	//Paint.DITHER_FLAG 防抖动
//        mPaint.setColor(Color.BLUE);				//设置默认的画笔颜色为红色
//        //设置画笔风格
//        mPaint.setStyle(Paint.Style.STROKE);		//设置填充方式为描边
//        mPaint.setStrokeJoin(Paint.Join.ROUND);	//设置笔刷的图形样式
//        mPaint.setStrokeCap(Paint.Cap.ROUND);	//设置画笔转弯处的连接风格
//        mPaint.setStrokeWidth(6);				//设置默认笔触的宽度为1像素
//        mPaint.setAntiAlias(true);				//设置抗锯齿功能
//        mPaint.setDither(true);					//设置抖动效果
//        mBitmapPaint = new Paint(Paint.DITHER_FLAG);
//        mPath = new Path();
//
//    }


    // If a given GATT characteristic is selected, check for supported features.  This sample
    // demonstrates 'Read' and 'Notify' features.  See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
    // list of supported characteristic features.
    private final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    Log.i(TAG, String.format("groupPosition:%2d childPosition:%2d id:%2d", groupPosition,childPosition,id) );
                    if (mGattCharacteristics != null) {
                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(groupPosition).get(childPosition);
                        final int charaProp = characteristic.getProperties();
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                            // If there is an active notification on a characteristic, clear
                            // it first so it doesn't update the data field on the user interface.
                            if (mNotifyCharacteristic != null) {
                                mBluetoothLeService.setCharacteristicNotification(
                                        mNotifyCharacteristic, false);
                                mNotifyCharacteristic = null;
                            }
                            mBluetoothLeService.readCharacteristic(characteristic);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = characteristic;
                            mBluetoothLeService.setCharacteristicNotification(
                                    characteristic, true);

                            dv =new MyView(ct);
                            setContentView(dv);
//                            int w = MyView.MeasureSpec.makeMeasureSpec(0,MyView.MeasureSpec.UNSPECIFIED);
//                            int h = MyView.MeasureSpec.makeMeasureSpec(0,MyView.MeasureSpec.UNSPECIFIED);
//                            dv.measure(w,h);

//                            Log.i(TAG,"canvas size:" + this.getWidth() + " " +dv.getHeight() + " "  + dv.getMeasuredWidth() + " " +dv.getMeasuredHeight());
                        }
                        return true;
                    }
                    return false;
                }
    };

    private void clearUI() {
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        mDataField.setText(R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
        mGattServicesList.setOnChildClickListener(servicesListClickListner);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        ct = this;
//        initCanvas();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String data) {
        if (data != null) {
            mDataField.setText(data);
        }
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this,
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 },
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 }
        );
        mGattServicesList.setAdapter(gattServiceAdapter);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    public class MyView extends View {

        private final Path mPath = new Path();
        private final Paint mPaint = new Paint();
        private long canvasHeight,canvasWidth;
        private long mstartX, mstartY,  mlastPenPressure , mendX, mendY, mpenPressure;
        public MyView(Context context) {
            super(context);
            mPaint.setAntiAlias(true);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(5);
            mPaint.setColor(Color.RED);

        }

        public void DrawLine(long startX,long startY, long lastPenPressure ,long endX,long endY,long penPressure)
        {
            mstartX = startX;
            mstartY = startY;
            mlastPenPressure =lastPenPressure;
            mendX =endX;
            mendY = endY;
            mpenPressure = penPressure;
//            Log.i(TAG,String.format("%5d %5d -- %5d %5d -- %5d", startX ,startY,endX,endY,penPressure));
            if (penPressure >0 && lastPenPressure ==0)
            {
               // mPath.reset();
            }
            else if (penPressure >0 && lastPenPressure >0)
            {
               // mPaint.setStrokeWidth(penPressure/128);
                mPath.moveTo(startX * canvasWidth / 65536, startY * canvasHeight / 65536);
              //  mPath.lineTo(endX * canvasWidth / 65536, endY * canvasHeight / 65536);
                mPath.quadTo((startX ) * canvasWidth / 65536, (startY )  * canvasHeight / 65536,endX * canvasWidth / 65536, endY * canvasHeight / 65536);
               // mPath.close();
            }

            invalidate();
            return;
        }
        @Override
        protected void onDraw(Canvas canvas) {
           // Log.i(TAG,"onDraw()");
            canvasWidth = canvas.getWidth();
            canvasHeight = canvas.getHeight();
            //Log.i(TAG,"canvas size:" + canvas.getWidth() + " " +canvas.getHeight() + " "  + getMeasuredWidth() + " " +getMeasuredHeight());
            super.onDraw(canvas);

//            Path mPath = new Path();
//            Paint mPaint = new Paint();
//
//            mPaint.setAntiAlias(true);
//            mPaint.setStyle(Paint.Style.STROKE);
//            mPaint.setStrokeWidth(5);
//            mPaint.setColor(Color.RED);
//            if (mpenPressure >0 && mlastPenPressure ==0)
//            {
//               // mPath.reset();
//            }
//            else
//            {
//               // mPaint.setStrokeWidth(penPressure/128);
//                mPath.moveTo(mstartX * canvasWidth / 65536, mstartY * canvasHeight / 65536);
//                mPath.lineTo(mendX * canvasWidth / 65536, mendY * canvasHeight / 65536);
//                mPath.close();
//            }

            canvas.drawPath(mPath, mPaint);
           // canvas.save(Canvas.ALL_SAVE_FLAG);

        }
    }
}
