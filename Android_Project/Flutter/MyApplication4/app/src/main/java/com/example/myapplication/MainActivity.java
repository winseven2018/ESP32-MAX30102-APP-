package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;

import com.example.myapplication.Application.BleAdvice;
import com.example.myapplication.Application.MyBleWrapperCallback;
import com.example.myapplication.Application.GPSLocationManager;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;
import com.tbruyelle.rxpermissions2.RxPermissions;


import java.io.IOException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import cn.com.heaton.blelibrary.ble.Ble;
import cn.com.heaton.blelibrary.ble.BleLog;
import cn.com.heaton.blelibrary.ble.callback.BleConnectCallback;
import cn.com.heaton.blelibrary.ble.callback.BleNotifyCallback;
import cn.com.heaton.blelibrary.ble.model.BleDevice;
import cn.com.heaton.blelibrary.ble.model.BleFactory;
import cn.com.heaton.blelibrary.ble.utils.ByteUtils;
import cn.com.heaton.blelibrary.ble.utils.ThreadUtils;
import cn.com.superLei.aoparms.annotation.Retry;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    public AppCompatActivity appCompatActivity;

    //主服务的uuid与其他特征的uuid
    UUID MSerivce = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    UUID HRService = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");
    UUID SPO2Service = UUID.fromString("dda0b41d-d59e-42a2-a72b-69e3410afab6");
    UUID TempService = UUID.fromString("c136b1df-7694-4587-b123-963aac86e021");
    UUID AlarmStaus = UUID.fromString("c137b1df-7694-4587-b123-963aac86e021");
    //指定蓝牙设备与MAC地址
    String address = "84:F7:03:53:E4:0A";
    String name = "BLE_LIBRA";
    public LocationClient mLocationClient = null;
    private final MyLocationListener myListener = new MyLocationListener();
    Timer timer = new Timer();

    @SuppressLint("SetTextI18n")
    @Retry(count = 3, delay = 100, asyn = true)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //LocationClient.setAgreePrivacy(true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        appCompatActivity = this;
        initLocation();
        initBle();
        MyLocationListener myLocationListener = new MyLocationListener();

        TextView HRView = (TextView) findViewById(R.id.HR);
        TextView SPO2View = (TextView) findViewById(R.id.SPO2);
        TextView TEMPView = (TextView) findViewById(R.id.Temp);
        TextView LOCATIONView = (TextView)findViewById(R.id.Location);
        TextView StausView = (TextView)findViewById(R.id.Staus);
        TextView ConnectStaus = findViewById(R.id.ConnectStaus);


        ConnectStaus.setText("正在发现并连接设备······");
        ConnectStaus.setBackgroundColor(Color.rgb(255, 0, 0));
        BleDevice BleDevice = new BleAdvice(address, name);
        Ble<BleDevice> ble = Ble.getInstance();
        BleDevice.setAutoConnecting(true);
        ble.connect(address, new BleConnectCallback<BleDevice>() {

            //连接状况改变时的回调
            @Override
            public void onConnectionChanged(BleDevice device) {
                BleLog.e("BleApplication", "连接状况改变了");
            }

            //连接失败时的回调
            @Override
            public void onConnectFailed(BleDevice device, int errorCode) {
                super.onConnectFailed(device, errorCode);
                ConnectStaus.setText("连接失败，正在重连，错误代码："+errorCode);
            }


            //当收到连接成功并搜索到指定的服务与特征后的回调
            @SuppressLint("RestrictedApi")
            @Override
            public void onReady(BleDevice device) {
                super.onReady(device);
                StausView.setText("---");
                HRView.setText("---");
                TEMPView.setText("---");
                SPO2View.setText("---");
                LOCATIONView.setText("---");
                ConnectStaus.setText("连接成功！正在读取数据···");
                ConnectStaus.setBackgroundColor(Color.rgb(0, 255, 0));
                ble.enableNotify(device, true, new BleNotifyCallback<BleDevice>() {     //开启监听
                    @Override
                    public void onChanged(BleDevice device, BluetoothGattCharacteristic characteristic) {       //当特征码的值发生变化
                        BleLog.e("MainActivity", "uuid:" + characteristic.getUuid() + ",value:" + ByteUtils.bytes2HexStr(characteristic.getValue()));
                        if (characteristic.getUuid().equals(HRService)) {       //匹配特征码对应的数值
                            BleLog.e("MainActivity", "心跳速率获取到了:" + ByteUtils.byte2int(characteristic.getValue()));
                            ThreadUtils.ui(new Runnable() {
                                @SuppressLint("SetTextI18n")
                                @Override
                                public void run() {
                                    ConnectStaus.setText("读取数据成功！");
                                    HRView.setText(""+ByteUtils.byte2int(characteristic.getValue()));
                                    LOCATIONView.setText(""+myListener.getLongitude()+","+myListener.getLatitude()                                                                                                                );
                                }
                            });
                        } else if (characteristic.getUuid().equals(SPO2Service)) {
                            BleLog.e("MainActivity", "SPO2获取到了:" + ByteUtils.byte2int(characteristic.getValue()));
                            ThreadUtils.ui(new Runnable() {
                                @SuppressLint("SetTextI18n")
                                @Override
                                public void run() {
                                    SPO2View.setText(""+ByteUtils.byte2int(characteristic.getValue()));
                                }
                            });
                        } else if (characteristic.getUuid().equals(TempService)) {
                            BleLog.e("MainActivity", "温度获取到了:" + ByteUtils.byte2int(characteristic.getValue()));
                            ThreadUtils.ui(new Runnable() {
                                @SuppressLint("SetTextI18n")
                                @Override
                                public void run() {
                                    TEMPView.setText(""+ByteUtils.byte2int(characteristic.getValue())); }
                            });
                        }else if (characteristic.getUuid().equals(AlarmStaus)) {
                            BleLog.e("MainActivity", "报警信息获取到了:" + ByteUtils.byte2int(characteristic.getValue()));
                            if(ByteUtils.byte2int(characteristic.getValue()) == 1) {
                                ThreadUtils.ui(new Runnable() {
                                    @SuppressLint("SetTextI18n")
                                    @Override
                                    public void run() {
                                        StausView.setBackgroundColor(Color.rgb(255, 0, 0));
                                        StausView.setTextColor(Color.rgb(255,255,255));
                                        StausView.setText("体征异常！");
                                    }
                                });
                            }else{
                                ThreadUtils.ui(new Runnable() {
                                    @SuppressLint("SetTextI18n")
                                    @Override
                                    public void run() {
                                        StausView.setBackground(getResources().getDrawable(R.drawable.border));
                                        StausView.setBackgroundColor(Color.rgb(255, 255, 255));
                                        StausView.setTextColor(Color.rgb(0,0,0));
                                        StausView.setText("体征正常");
                                    }
                                });
                            }
                        }
                    }

                    @Override
                    public void onNotifySuccess(cn.com.heaton.blelibrary.ble.model.BleDevice device) {
                        super.onNotifySuccess(device);
                    }

                    @Override
                    public void onNotifyFailed(cn.com.heaton.blelibrary.ble.model.BleDevice device, int failedCode) {
                        super.onNotifyFailed(device, failedCode);
                    }
                });
            }
        });
    }


    public class MyLocationListener extends BDAbstractLocationListener {
        private double latitude;
        private double longitude;
        private String Address;
        @SuppressLint("RestrictedApi")
        @Override
        public void onReceiveLocation(BDLocation location) {
            //此处的BDLocation为定位结果信息类，通过它的各种get方法可获取定位相关的全部结果
            //以下只列举部分获取经纬度相关（常用）的结果信息
            //更多结果信息获取说明，请参照类参考中BDLocation类中的说明
            this.latitude = location.getLatitude();    //获取纬度信息
            this.longitude = location.getLongitude();    //获取经度信息
            float radius = location.getRadius();    //获取定位精度，默认值为0.0f
            BleLog.e("MainActivity", "获取到纬度：" + location.getLatitude()+",经度："+location.getLongitude());
            String coorType = location.getCoorType();
            //获取经纬度坐标类型，以LocationClientOption中设置过的坐标类型为准
            int errorCode = location.getLocType();
            //获取定位类型、定位错误返回码，具体信息可参照类参考中BDLocation类中的说明
            String Address = location.getAddrStr();
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }
    }
    private void initLocation(){
        try {
            mLocationClient = new LocationClient(getApplicationContext());
            mLocationClient.registerLocationListener(myListener);
            LocationClientOption option = new LocationClientOption();
            option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);   //LocationMode.Hight_Accuracy：高精度；
            option.setCoorType("bd09ll");   //BD09ll：百度经纬度坐标；
            option.setOpenGps(true);    //可选，设置是否使用gps，默认false
            option.setLocationNotify(true); //可选，设置是否当GPS有效时按照1S/1次频率输出GPS结果，默认false
            option.setScanSpan(1001);
            option.setIgnoreKillProcess(true);
            mLocationClient.start();
        }catch(Exception e) {
            e.printStackTrace();
        }
    }
    private void initBle() {
        Ble.options()//开启配置
                .setLogBleEnable(true)//设置是否输出打印蓝牙日志（非正式打包请设置为true，以便于调试）
                .setThrowBleException(true)//设置是否抛出蓝牙异常 （默认true）
                .setAutoConnect(true)//设置是否自动连接 （默认false）
                .setIgnoreRepeat(false)//设置是否过滤扫描到的设备(已扫描到的不会再次扫描)
                .setConnectTimeout(10 * 1000)//设置连接超时时长（默认10*1000 ms）
                .setMaxConnectNum(7)//最大连接数量
                .setScanPeriod(12 * 1000)//设置扫描时长（默认10*1000 ms）
                .setUuidService(MSerivce)//设置主服务的uuid（必填）
                .setUuidWriteCha(HRService)//设置可写特征的uuid （必填,否则写入失败）
                .setUuidReadCha(HRService)//设置可读特征的uuid （选填）
                .setFactory(new BleFactory() {//实现自定义BleDevice时必须设置
                    @Override
                    public BleDevice create(String address, String name) {
                        return new BleAdvice(address, name);//自定义BleDevice的子类
                    }
                })
                .setBleWrapperCallback(new MyBleWrapperCallback())//设置全部蓝牙相关操作回调（例： OTA升级可以再这里实现,与项目其他功能逻辑完全解耦）
                .create(appCompatActivity, new Ble.InitCallback() {
                    @Override
                    public void success() {
                        BleLog.e("MainActivity", "初始化成功");
                    }

                    @Override
                    public void failed(int failedCode) {
                        BleLog.e("MainActivity", "初始化失败：" + failedCode);
                    }
                });
    }
}