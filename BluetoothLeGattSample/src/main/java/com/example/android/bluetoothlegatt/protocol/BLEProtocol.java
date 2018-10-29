package com.example.android.bluetoothlegatt.protocol;

import android.util.Log;

import com.example.android.bluetoothlegatt.utils.HexUtil;

/**
 * Created by yanwen on 18/10/20.
 */
public class BLEProtocol {
    //时间设置命令
    private static byte[] BLESetDataPro = {
            (byte) 0xfc, 0x00, 0x12, 0x12, 0x12,
            0x12, 0x12, 0x12, 0x06, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00
    };
    //步伐信息上报
    final public static byte[] BLEGetStepInfoPro = {
            0x03, 0x01, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00
    };

    public static void setBLETimePro(int i_year, int i_month, int i_day, int i_hour, int i_minute, int i_second, int i_week) {
        byte b_year = (byte) Integer.parseInt(Integer.toString(i_year, 10).substring(1), 16);
        byte b_month = (byte) Integer.parseInt(Integer.toString(i_month, 10), 16);
        byte b_day = (byte) Integer.parseInt(Integer.toString(i_day, 10), 16);
        byte b_hour = (byte) Integer.parseInt(Integer.toString(i_hour, 10), 16);
        byte b_minute = (byte) Integer.parseInt(Integer.toString(i_minute, 10), 16);
        byte b_second = (byte) Integer.parseInt(Integer.toString(i_second, 10), 16);

        BLESetDataPro[2] = b_year;
        BLESetDataPro[3] = b_month;
        BLESetDataPro[4] = b_day;
        BLESetDataPro[5] = b_hour;
        BLESetDataPro[6] = b_minute;
        BLESetDataPro[7] = b_second;
        BLESetDataPro[8] = (byte) i_week;
    }

    public static byte[] getBLEGetTimePro() {
        return BLESetDataPro;
    }


}
