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
import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ExpandableListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.EditText;


import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.widget.Toast;

import com.example.android.bluetoothlegatt.data.SportsDBHelper;
import com.example.android.bluetoothlegatt.protocol.BLEProtocol;
import com.example.android.bluetoothlegatt.utils.HexUtil;
import com.example.android.bluetoothlegatt.utils.SharedPreferencesHelper;
import com.example.android.bluetoothlegatt.view.CircleBar;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    private static final int MESSAGE_WHAT_STEP = 1;
    private static final int MESSAGE_WHAT_HEART_RATE = 2;
    private static final int MESSAGE_WHAT_SAVE_DATA = 3;
    private SharedPreferencesHelper sharedPrefrencesHelper;
    private BluetoothGattService bluetoothGattService;
    public BluetoothGattCharacteristic characteristicRead;
    public BluetoothGattCharacteristic characteristicWrite;
    private TextView tv_step, tv_mileage, tv_calori, tv_heart_rate;
    private RadioButton rb_binddev, rb_cancelbinddev;
    private RadioGroup rg_devselector;
    public static final String BIND_IFG = "BindIfg";  //
//    private Handler reconnectHandler = new Handler();
//    private Reconnect reconnect = new Reconnect();
//    private final static int RECONNECT_TIME_INTERVAL = 2000;
    private CircleBar idCircleBar;
//    private Button id_save_db, id_query_db;
    /***保存每一次活的的运动数据，当断开链接时将这个数据保存到数据库**/
    private SportsDBHelper.SportsData sportsData = new SportsDBHelper.SportsData();
    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            switch (msg.what) {
                case MESSAGE_WHAT_STEP:
//                    String s_byteArray = HexUtil.bytesToHexString(byteArray);
//                    Log.d(TAG, "handleMessage: " + s_byteArray);
                    Bundle bundle = msg.getData();
                    byte[] byteArray = bundle.getByteArray("ByteArray");
                    /******************display step*********************/
                    int step = ((((int) byteArray[2] << 16) & 0x00ff0000) | (((int) byteArray[3] << 8) & 0x0000ff00) | (((int) byteArray[4]) & 0x000000ff));
                    String s_int_step = Integer.toString(step, 10);
                    String s_hex_step = Integer.toHexString(step);
                    dispStep("Step count:(0x" + s_hex_step + "," + s_int_step + ")");
                    sportsData.step = step;
                    /******************display mileage*********************/
                    int mileage = ((((int) byteArray[5] << 16) & 0x00ff0000) | (((int) byteArray[6] << 8) & 0x0000ff00) | (((int) byteArray[7]) & 0x000000ff));
                    String s_int_mileage = Integer.toString(mileage, 10);
                    String s_hex_mileage = Integer.toHexString(mileage);
                    dispMileage("Mileage:(0x" + s_hex_mileage + "," + s_int_mileage + ")");
                    sportsData.mileage = mileage;
                    /******************display calori*********************/
                    int calorie = ((((int) byteArray[8] << 16) & 0x00ff0000) | (((int) byteArray[9] << 8) & 0x0000ff00) | (((int) byteArray[10]) & 0x000000ff));
                    String s_int_calori = Integer.toString(calorie, 10);
                    String s_hex_calori = Integer.toHexString(calorie);
                    dispCalori("Calori:(0x" + s_hex_calori + "," + s_int_calori + ")");
                    sportsData.calorie = calorie;
                    updateCircleBar(step, mileage, calorie);
                    mHandler.sendEmptyMessage(MESSAGE_WHAT_SAVE_DATA);
                    break;
                case MESSAGE_WHAT_HEART_RATE:
                    Bundle bundle1 = msg.getData();
                    byte[] byteArray1 = bundle1.getByteArray("ByteArray");
                    int heartbeat = byteArray1[12] & 0xff;
                    String s_heartbeat = Integer.toString(heartbeat, 10);
                    dispHeartRate("Heart Rate:(" + s_heartbeat + ")");
                    sportsData.heartbeat = heartbeat;
                    mHandler.sendEmptyMessage(MESSAGE_WHAT_SAVE_DATA);
                    break;

                case MESSAGE_WHAT_SAVE_DATA:
                    saveData();
                    break;
                default:
                    break;
            }

        }
    };

    private RadioGroup.OnCheckedChangeListener listener = new RadioGroup.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(RadioGroup radioGroup, int i) {
//            Log.d(TAG, "onCheckedChanged: " + i);
            switch (i) {
                case R.id.rb_binddev:  //绑定设备
                    Log.d(TAG, "onCheckedChanged: bind device");
                    sharedPrefrencesHelper.put(BIND_IFG, true);
                    sharedPrefrencesHelper.put(EXTRAS_DEVICE_NAME, mDeviceName);
                    sharedPrefrencesHelper.put(EXTRAS_DEVICE_ADDRESS, mDeviceAddress);
                    startService();
                    break;
                case R.id.rb_cancelbinddev:   //取消绑定
                    Log.d(TAG, "onCheckedChanged: unbind device");
                    sharedPrefrencesHelper.put(BIND_IFG, false);
                    sharedPrefrencesHelper.put(EXTRAS_DEVICE_NAME, "");
                    sharedPrefrencesHelper.put(EXTRAS_DEVICE_ADDRESS, "");
//                    stopService();
                    break;
                default:
                    break;
            }
        }
    };
    private void startService() {
        Intent intent = new Intent();
        intent.setAction("StartBluetoothLeService");
        intent.putExtra(BluetoothLeService.EXTRAS_DEVICE_ADDRESS, mDeviceAddress);
        startService(intent);//启动服务后台循环重连
    }
    private void stopService() {
        stopService(new Intent("StartBluetoothLeService"));
    }



    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";


    private TextView mConnectionState;
//    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
//    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";
    //byte[] WriteBytes = null;
//    byte[] WriteBytes = new byte[20];
    // Code to manage Service lifecycle.

    private ProgressDialog progressDialog;
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
//            if (!mBluetoothLeService.initialize()) {
//                Log.e(TAG, "Unable to initialize Bluetooth");
//                finish();
//            }
//            // Automatically connects to the device upon successful start-up initialization.
//            mBluetoothLeService.connect(mDeviceAddress);
//            showProgressDialog("","正在连接...");
            if (BluetoothLeService.STATE_CONNECTED != mBluetoothLeService.getCurrentConnectState()) {
                showProgressDialog("", "正在连接...");

                /******启动服务*****/
                startService();
                mConnected = false;
            } else {
                mConnected = true;
                updateConnectionState(R.string.connected);

            }
            /***绑定服务成功后同步数据库数据****/
            sysnchronousData();

//            int state = mBluetoothLeService.getCurrentConnectState();
//            Log.i("tag", "onCreate BLE connect state:" + state);
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
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;

                hideProgressDialog();
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
//                reconnectHandler.removeCallbacks(reconnect);//链接成功清除重连
//                mBluetoothLeService.cancelReconnect();//取消服务中的循环重连



            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
//                clearUI();
//                reconnectHandler.postDelayed(reconnect, RECONNECT_TIME_INTERVAL);//意外断开尝试去重连

                /************当蓝牙断开链接后将数据写入数据库*******************/
                Message msg = mHandler.obtainMessage();
                msg.what = MESSAGE_WHAT_SAVE_DATA;
                mHandler.sendMessage(msg);

            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                /*displayGattServices(mBluetoothLeService.getSupportedGattServices());*/

                //获得手表的服务UUID
                bluetoothGattService = mBluetoothLeService.getSupportedGattService(UUID.fromString(SampleGattAttributes.SERVICE_UUID));
                if (bluetoothGattService != null) {
                    //设置读取消息的通知
                    characteristicRead = bluetoothGattService.getCharacteristic(UUID.fromString(SampleGattAttributes.CHARACTER_READ_UUID));
//                Log.d("tag", characteristicRead.getUuid().toString());
//                if (mBluetoothLeService.enableNatification(characteristicRead, true)) {
//                    Log.d("tag", "enabled!!!");
//                } else {
//                    Log.d("tag", "disabled!!!");
//                }
                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Calendar cal = Calendar.getInstance();
                            int i_year = cal.get(Calendar.YEAR);
                            int i_month = cal.get(Calendar.MONTH) + 1;
                            int i_day = cal.get(Calendar.DATE);
                            int i_week = cal.get(Calendar.DAY_OF_WEEK) - 1;
                            int i_hour = cal.get(Calendar.HOUR_OF_DAY);
                            int i_minute = cal.get(Calendar.MINUTE);
                            int i_second = cal.get(Calendar.SECOND);
                            BLEProtocol.setBLETimePro(i_year, i_month, i_day, i_hour, i_minute, i_second, i_week);//往协议中插入系统时间
                            //获得BLE发送的 characterictic
                            if (bluetoothGattService != null) {
                                characteristicWrite = bluetoothGattService.getCharacteristic(UUID.fromString(SampleGattAttributes.CHARACTER_WRITE_UUID));
                                if (characteristicWrite != null) {
                                    characteristicWrite.setValue(BLEProtocol.getBLEGetTimePro());  //按照协议发送时间
                                    mBluetoothLeService.writeCharacteristic(characteristicWrite);
                                }
                            }
                        }
                    }, 300);
                } else {
                    Log.d(TAG, "获取到 bluetoothGattService 为空");
                }

            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                String s_recvPro = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                byte[] b_recvPro = HexUtil.hexStringToBytes(s_recvPro);
                if (0x00 == b_recvPro[0]) { //时间设置返回成功
                    Toast.makeText(DeviceControlActivity.this, "时间设置成功", Toast.LENGTH_SHORT).show();
                    //时间返回成功立马发送主动上报步数信息命令
                    characteristicWrite.setValue(BLEProtocol.BLEGetStepInfoPro);
                    mBluetoothLeService.writeCharacteristic(characteristicWrite);
                }else if (b_recvPro[0] == 0x80) {  //时间设置返回失败
                    Toast.makeText(DeviceControlActivity.this, "时间设置失败", Toast.LENGTH_SHORT).show();
                } else if (b_recvPro[0] == 0x03) {//返回的是步数信息
                    Message msg = mHandler.obtainMessage();
                    msg.what = MESSAGE_WHAT_STEP;
                    Bundle bundle = new Bundle();
                    bundle.putByteArray("ByteArray", b_recvPro);
                    msg.setData(bundle);
                    mHandler.sendMessage(msg);
                } else if (b_recvPro[0] == 0x0a) { //返回的是心率信息
                    Message msg = mHandler.obtainMessage();
                    msg.what = MESSAGE_WHAT_HEART_RATE;
                    Bundle bundle = new Bundle();
                    bundle.putByteArray("ByteArray", b_recvPro);
                    msg.setData(bundle);
                    mHandler.sendMessage(msg);
                }
                displayData(s_recvPro);
            } else if (BluetoothLeService.ACTION_GATT_CONNECTING.equals(action)) {//正在链接中...
                Log.d(TAG, "onReceive: connecting...");
            }
        }
    };
/*

    public static String bin2hex(String bin) {
        char[] digital = "0123456789ABCDEF".toCharArray();
        StringBuffer sb = new StringBuffer("");
        byte[] bs = bin.getBytes();
        int bit;
        for (int i = 0; i < bs.length; i++) {
            bit = (bs[i] & 0x0f0) >> 4;
            sb.append(digital[bit]);
            bit = bs[i] & 0x0f;
            sb.append(digital[bit]);
        }
        return sb.toString();
    }
    public static byte[] hex2byte(byte[] b) {
        if ((b.length % 2) != 0) {
            throw new IllegalArgumentException("长度不是偶数");
        }
        byte[] b2 = new byte[b.length / 2];
        for (int n = 0; n < b.length; n += 2) {
            String item = new String(b, n, 2);
            // 两位一组，表示一个字节,把这样表示的16进制字符串，还原成一个进制字节
            b2[n / 2] = (byte) Integer.parseInt(item, 16);
        }
        b = null;
        return b2;
    }
    // If a given GATT characteristic is selected, check for supported features.  This sample
    // demonstrates 'Read' and 'Notify' features.  See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
    // list of supported characteristic features.
    private final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    final EditText et ;  //申明变量

                    et = new EditText(parent.getContext()); //创建对象
                    et.setSingleLine(true);  //设置属性

                    final EditText etHex ;  //申明变量
                    etHex = new EditText(parent.getContext()); //创建对象
                    etHex.setSingleLine(true);  //设置属性

                    if (mGattCharacteristics != null) {
                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(groupPosition).get(childPosition);
                        final int charaProp = characteristic.getProperties();

                        //如果该char可写
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {


                            LayoutInflater factory = LayoutInflater.from(parent.getContext());
                            final View textEntryView = factory.inflate(R.layout.dialog, null);
                            final EditText editTextName = (EditText) textEntryView.findViewById(R.id.editTextName);
                            final EditText editTextNumEditText = (EditText)textEntryView.findViewById(R.id.editTextNum);
                            AlertDialog.Builder ad1 = new AlertDialog.Builder(parent.getContext());
                            ad1.setTitle("WriteCharacteristic");
                            ad1.setView(textEntryView);
                            ad1.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int i) {
                                    byte[] value = new byte[20];
                                    value[0] = (byte) 0x00;
                                    editTextName.setText("0301000000000000000000000000000000000000");
                                    if(editTextName.getText().length() > 0){
                                        //write string
                                        WriteBytes= editTextName.getText().toString().getBytes();
                                    }else if(editTextNumEditText.getText().length() > 0){
                                        WriteBytes= hex2byte(editTextNumEditText.getText().toString().getBytes());
                                    }

//                                    String uuid = characteristic.getUuid().toString();
//                                    Log.d("tag", uuid);

                                    characteristic.setValue(value[0],
                                            BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                                    characteristic.setValue(WriteBytes);

                                    mBluetoothLeService.writeCharacteristic(characteristic);

                                }
                            });
                            ad1.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int i) {

                                    //0x03 EN 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
//                                    characteristic.setValue(new byte[]{
//                                            0x03, 0x01, 0x00, 0x00, 0x00,
//                                            0x00, 0x00, 0x00, 0x00, 0x00,
//                                            0x00, 0x00, 0x00, 0x00, 0x00,
//                                            0x00, 0x00, 0x00, 0x00, 0x00
//                                    });
                                    characteristic.setValue(new byte[]{
                                            (byte)0xfc, 0x00, 0x12, 0x12, 0x12,
                                            0x12, 0x12, 0x12, 0x06, 0x00,
                                            0x00, 0x00, 0x00, 0x00, 0x00,
                                            0x00, 0x00, 0x00, 0x00, 0x00
                                    });

                                    mBluetoothLeService.writeCharacteristic(characteristic);
//                                    mBluetoothLeService.writeCharacteristic(characteristicRead);

                                }

                            });
                            ad1.show();

                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = characteristic;
//                            mBluetoothLeService.setCharacteristicNotification(
//                                    characteristic, true);
                        }
                        return true;
                    }
                    return false;
                }
    };
*/

    private void clearUI() {
//        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
//        mDataField.setText(R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);


        /********我添加的*********/
        initView();
        initListener();
        initData();
        /************************/

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
//        mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
//        mGattServicesList.setOnChildClickListener(servicesListClickListner);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
//        mDataField = (TextView) findViewById(R.id.data_value);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);

//        /******启动服务*****/
//        Intent intent1 = new Intent();
//        intent1.setAction("StartBluetoothLeService");
//        intent1.putExtra(BluetoothLeService.EXTRAS_DEVICE_ADDRESS, mDeviceAddress);
//        startService(intent1);//启动服务后台循环重连

        /******绑定服务*****/
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);


    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
//        if (mBluetoothLeService != null) {
//            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
//            Log.d(TAG, "Connect request result=" + result);
//        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
        boolean isBind = (Boolean) sharedPrefrencesHelper.getSharedPreference(BIND_IFG, false);
        if (!isBind) {
            stopService();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean isBind = (Boolean) sharedPrefrencesHelper.getSharedPreference(BIND_IFG, false);
        Intent intent = getIntent();
        intent.putExtra("IsBind", isBind);
        setResult(1, intent);
        finish();
        return super.onKeyDown(keyCode, event);
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
//        if (data != null) {
//            mDataField.setText(data);
//        }
    }
/*
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
*/
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }


    private void initData() {
        sharedPrefrencesHelper = new SharedPreferencesHelper(
                DeviceControlActivity.this, DeviceControlActivity.this.getPackageName());
//        Log.i(TAG, "initData: " + DeviceControlActivity.this.getPackageName());

        if (!sharedPrefrencesHelper.contain(BIND_IFG)) {
            rb_cancelbinddev.setChecked(true);
        } else {
            Object isBind = sharedPrefrencesHelper.getSharedPreference(BIND_IFG, false);
            if (isBind instanceof Boolean) {
                if ((Boolean) isBind) {
                    rb_binddev.setChecked(true);
                } else {
                    rb_cancelbinddev.setChecked(true);
                }
            }
        }

        // 初始化CircleBar
        idCircleBar.setProgress(0, 1);
        idCircleBar.setText(0);
        idCircleBar.setmMaxCount(10000);
        idCircleBar.startCustomAnimation();


//        SportsDBHelper
//                sportsDBHelper = SportsDBHelper.getInstance(DeviceControlActivity.this, SportsDBHelper.DB_NAME);
//        sportsDBHelper.deleteDB(DeviceControlActivity.this);
//
        SportsDBHelper dbHelper = SportsDBHelper.getInstance(DeviceControlActivity.this, SportsDBHelper.DB_NAME);
        if (dbHelper != null) {
            List<SportsDBHelper.SportsData> data = dbHelper.query();
            for (SportsDBHelper.SportsData dt : data) {
                Log.i(TAG, "" + dt.day + ", " + dt.step + "," + dt.mileage + "," + dt.calorie + "," + dt.heartbeat);
            }
        }
    }

    private void initView() {
        tv_step = (TextView) findViewById(R.id.tv_step);
        tv_mileage = (TextView) findViewById(R.id.tv_mileage);
        tv_calori = (TextView) findViewById(R.id.tv_calorie);
        rb_binddev = (RadioButton) findViewById(R.id.rb_binddev);
        rb_cancelbinddev = (RadioButton) findViewById(R.id.rb_cancelbinddev);
        rg_devselector = (RadioGroup) findViewById(R.id.rg_devselector);

        tv_heart_rate = (TextView) findViewById(R.id.tv_heart_rate);

        idCircleBar = (CircleBar) findViewById(R.id.id_circle_bar);
//        id_save_db = (Button) findViewById(R.id.id_save_db);
//        id_query_db = (Button) findViewById(R.id.id_query_db);
    }

    private void updateCircleBar(int steps, int mileage, int calorie) {
//        idCircleBar.setProgress(steps, 1);
//        idCircleBar.startCustomAnimation();
        switch (idCircleBar.getCurrentType()) {
            case 1:
                idCircleBar.setText(steps);
                break;
            case 2:
                idCircleBar.setText(mileage);
                break;
            case 3:
                idCircleBar.setText(calorie);
                break;
            default:
                break;
        }
        idCircleBar.updateCircleBar();
    }

    private void initListener() {
        rg_devselector.setOnCheckedChangeListener(listener);
        idCircleBar.setOnCircleBarClickListener(new CircleBar.OnCircleBarClickListener() {
            @Override
            public void onClick() {
                idCircleBar.setNextType();
//                updateCircleBar(sportsData.step, sportsData.mileage, sportsData.calorie);
                sysnchronousData();

            }
        });
//
//        id_save_db.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                SportsDBHelper sportsDBHelper = SportsDBHelper.getInstance(DeviceControlActivity.this, "sports_db");
//                sportsDBHelper.insert(sportsData);
//            }
//        });
//
//        id_query_db.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                SportsDBHelper sportsDBHelper = SportsDBHelper.getInstance(DeviceControlActivity.this, "sports_db");
//                List<SportsDBHelper.SportsData> datas = sportsDBHelper.query(false, 3);
//                for (SportsDBHelper.SportsData data : datas) {
//                    Log.i(TAG, data.step + "," + data.mileage + "," + data.calorie + "," + data.heartbeat);
//                }
//
//            }
//        });
    }

    private void dispStep(String s_step) {
        if (s_step.isEmpty()) {
            tv_step.setText("0");
        } else {
            tv_step.setText(s_step);
        }
    }

    private void dispMileage(String s_mileage) {
        if (s_mileage.isEmpty()) {
            tv_mileage.setText("0");
        } else {
            tv_mileage.setText(s_mileage);
        }
    }

    private void dispCalori(String s_calori) {
        if (s_calori.isEmpty()) {
            tv_calori.setText("0");
        } else {
            tv_calori.setText(s_calori);
        }
    }
    private void dispHeartRate(String s_rate) {
        if (s_rate.isEmpty()) {
            tv_heart_rate.setText("0");
        } else {
            tv_heart_rate.setText(s_rate);
        }
    }



//    /**
//     * 此类用于重连
//     * **/
//    private class Reconnect implements Runnable {
//
//        @Override
//        public void run() {
//            Log.d(TAG, "run: Reconnect");
//            mBluetoothLeService.connect(mDeviceAddress);
//            reconnectHandler.postDelayed(reconnect, 2000);
//        }
//    }


    /**
     * 提示加载
     */
    public void showProgressDialog(String title, String message) {
        if (progressDialog == null) {

            progressDialog = ProgressDialog.show(DeviceControlActivity.this, title,
                    message, true, false);
        } else if (progressDialog.isShowing()) {
            progressDialog.setTitle(title);
            progressDialog.setMessage(message);
        }

        progressDialog.show();

    }

    /**
     * 隐藏提示加载
     */
    public void hideProgressDialog() {

        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }

    }

    /**
     * 读出数据库当天的数据
     * */
    private void sysnchronousData() {
        SportsDBHelper dbHelper = SportsDBHelper.getInstance(DeviceControlActivity.this, SportsDBHelper.DB_NAME);
        SportsDBHelper.SportsData data = dbHelper.query(Calendar.getInstance().get(Calendar.DATE));
//        Log.i(TAG, "" + data.day + ", " + data.step + "," + data.mileage + "," + data.calorie + "," + data.heartbeat);
        if (data != null) {
            updateCircleBar(data.step, data.mileage, data.calorie);
        }
    }

    /**
     * 将当天实时数据保存到数据库
     * */
    private void saveData() {
        if (sportsData.step == 0) return;
        SportsDBHelper sportsDBHelper = SportsDBHelper.getInstance(DeviceControlActivity.this, SportsDBHelper.DB_NAME);
        SportsDBHelper.SportsData data = sportsDBHelper.query(Calendar.getInstance().get(Calendar.DATE));
        if (data == null) {
//                    Log.i(TAG, "size:"+ datas.size());
            sportsData.day = Calendar.getInstance().get(Calendar.DATE);
            sportsDBHelper.insert(sportsData);
        } else {
            sportsData.day = Calendar.getInstance().get(Calendar.DATE);
            sportsDBHelper.update(sportsData, sportsData.day);
        }
    }
}
