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

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.example.android.bluetoothlegatt.utils.HexUtil;
import com.example.android.bluetoothlegatt.utils.SharedPreferencesHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTING =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTING";//正在链接中
    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";

    public final static UUID UUID_HEART_RATE_MEASUREMENT =
            UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT);


    private static final int RECONNECT_TIME_INTERVAL = 5000;
    private Handler mHandler = new Handler();
    private Runnable runnable = null;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "onCreate: service");

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
//        flags = START_STICKY;
        SharedPreferencesHelper preferencesHelper = new SharedPreferencesHelper(BluetoothLeService.this, "com.example.android.bluetoothlegatt");
        Boolean isBind = (Boolean) preferencesHelper.getSharedPreference(DeviceControlActivity.BIND_IFG, false);
        if (isBind) {
            mBluetoothDeviceAddress = (String) preferencesHelper.getSharedPreference("DEVICE_ADDRESS", "address");
        } else {
            if (intent == null) return super.onStartCommand(intent, flags, startId);
            mBluetoothDeviceAddress = intent.getStringExtra("DEVICE_ADDRESS");
        }
        reconnect();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnect();
        close();
        Log.i(TAG, "onDestroy: service");

//        SharedPreferencesHelper preferencesHelper = new SharedPreferencesHelper(BluetoothLeService.this, "com.example.android.bluetoothlegatt");
//        if ((Boolean) preferencesHelper.getSharedPreference(DeviceControlActivity.BIND_IFG, false)) {
//            Intent intent = new Intent();
//            intent.setAction("StartBluetoothLeService");
//            startService(intent);//启动服务后台循环重连
//        }
    }

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());
                /**********一旦连接上则取消重连********/
                cancelReconnect();

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) { // 断开后如果一直处于断开状态此信号会隔一段时间检测到一次
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);

                /*************意外断开连接尝试重连**************/
                reconnect();
            } else if (newState == BluetoothProfile.STATE_CONNECTING) {
                intentAction = ACTION_GATT_CONNECTING;
                mConnectionState = STATE_CONNECTING;
                Log.i(TAG, "The GATT is Connecting....");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                //获得手表的服务UUID
                BluetoothGattService bluetoothGattService = getSupportedGattService(UUID.fromString(SampleGattAttributes.SERVICE_UUID));
                //设置读取消息的通知
                BluetoothGattCharacteristic characteristicRead = bluetoothGattService.getCharacteristic(UUID.fromString(SampleGattAttributes.CHARACTER_READ_UUID));
//                Log.d("tag", characteristicRead.getUuid().toString());
                if (enableNatification(characteristicRead, true)) {
                    Log.d("tag", "enabled!!!");
                } else {
                    Log.d("tag", "disabled!!!");
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
//            String msg = DeviceControlActivity.bin2hex(characteristic.getStringValue(0));
//            Log.d("BLE Write:", msg);
            Log.d(TAG, "onCharacteristicWrite: " + status);
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        // This is special handling for the Heart Rate Measurement profile.  Data parsing is
        // carried out as per profile specifications:
        // http://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml
        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            int flag = characteristic.getProperties();
            int format = -1;
            if ((flag & 0x01) != 0) {
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
                Log.d(TAG, "Heart rate format UINT16.");
            } else {
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
                Log.d(TAG, "Heart rate format UINT8.");
            }
            final int heartRate = characteristic.getIntValue(format, 1);
            Log.d(TAG, String.format("Received heart rate: %d", heartRate));
            intent.putExtra(EXTRA_DATA, String.valueOf(heartRate));
        } else {
            // For all other profiles, writes the data formatted in HEX.
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
//                final StringBuilder stringBuilder = new StringBuilder(data.length);
//                for(byte byteChar : data)
//                    stringBuilder.append(String.format("%02X ", byteChar));
//                intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
                String string =HexUtil.bytesToHexString(data);
                intent.putExtra(EXTRA_DATA, string);
            }
        }
        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
//        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }
    public void writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.writeCharacteristic(characteristic);
    }
    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
//        mBluetoothGatt.setCharacteristicNotification(DeviceControlActivity.characteristicRead, enabled);

        // This is specific to Heart Rate Measurement.
        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }
    }

    public boolean enableNatification(BluetoothGattCharacteristic characteristic, boolean enabled) {
        if (!mBluetoothGatt.setCharacteristicNotification(characteristic, enabled)) {
            return false;
        }
        BluetoothGattDescriptor clientConfig = characteristic.getDescriptor(
                UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
        if (clientConfig == null) return false;
        if (enabled) {
            clientConfig.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        } else {
            clientConfig.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        }
        return mBluetoothGatt.writeDescriptor(clientConfig);
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }

    public BluetoothGattService getSupportedGattService(UUID uuid){
        if (mBluetoothGatt == null) return null;
        return mBluetoothGatt.getService(uuid);
    }

    private int count = 0;
    /**
     * 我添加
     * 重连和取消重连
     */
    public void reconnect() {
//        if (!initialize()) {
//            Log.e(TAG, "Unable to initialize Bluetooth");
//        }
//        connect(mBluetoothDeviceAddress);

        Log.i(TAG, "connect: "+mConnectionState);
        if (mConnectionState == STATE_CONNECTED) {
            Log.i(TAG, "connect state is connected!!");
            return;
        }
        if (runnable == null) {
            runnable = new Runnable() {
                @Override
                public void run() {
                    close();
                    if (!initialize()) {
                        Log.e(TAG, "Unable to initialize Bluetooth");
                    }
                    connect(mBluetoothDeviceAddress);
                    count++;
                    Log.i(TAG, "run: " + count);
                    Log.d(TAG, "run: " + count);
                    if (count >= 5) { //连续重连五次还没连接上则重新进行扫描周围设备
                        count = 0;
                        scanLeDevice(true);
                        cancelReconnect();
                    } else {
                        mHandler.postDelayed(this, RECONNECT_TIME_INTERVAL);
                    }
                }
            };
            mHandler.post(runnable);
        }
    }
    public void cancelReconnect() {
        mHandler.removeCallbacks(runnable);
        runnable = null;
    }

    /**
     * 获得蓝牙连接的状态
     * */
    public int getCurrentConnectState() {
        return mConnectionState;
    }

    /**
     *重新扫描周围设备(oppo手机关闭蓝牙后需要)
     * */
    Handler scanHandle = new Handler();
    private List<String> deviceAddrs = new ArrayList<String>();
    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            scanHandle.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mBluetoothAdapter.stopLeScan(leScanCallback);
//                    for (int i = 0; i < deviceAddrs.size(); ++i) {
//                        Log.i(TAG, "run: " + deviceAddrs.get(i));
//                    }
                    if (!deviceAddrs.contains(mBluetoothDeviceAddress)) {
//                        Log.i(TAG, "请确保设备已经打开且再附近！！");
                        Toast.makeText(BluetoothLeService.this, "请确保设备已经打开且再附近！！", Toast.LENGTH_SHORT).show();
                    } else {
                        reconnect();
                    }
                }
            }, 10000);
            mBluetoothAdapter.startLeScan(leScanCallback);
        } else {
            mBluetoothAdapter.stopLeScan(leScanCallback);
        }
    }

    private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice bluetoothDevice, int i, byte[] bytes) {
//            Log.i(TAG, "onLeScan: " + bluetoothDevice.getAddress() + "," + i);
            if (!deviceAddrs.contains(bluetoothDevice.getAddress())) {
                deviceAddrs.add(bluetoothDevice.getAddress());
            }
        }
    };
}
