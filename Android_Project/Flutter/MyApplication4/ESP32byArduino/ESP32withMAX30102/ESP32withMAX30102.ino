#include <Arduino.h>
#include <Wire.h>
#include "MAX30105.h"
#include "spo2_algorithm.h"
#include <stdio.h>
#include "freertos/FreeRTOS.h"
#include "esp_system.h"
#include <BLEDevice.h>
#include <BLEAdvertising.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include <BLE2902.h>
#include <BLECharacteristic.h>

MAX30105 particleSensor;
//#define MAX_BRIGHTNESS 255

//MAX30102
uint32_t irBuffer[100]; //红外LED传感器数据（32位）
uint32_t redBuffer[100];  //红色LED传感器数据（32位）
int32_t bufferLength; //缓冲区长度
int32_t spo2; //SPO2值
int8_t validSPO2; //指示SPO2计算是否有效的指示器
int32_t heartRate; //心率值
int8_t validHeartRate; //显示心率计算是否有效的指示器
float temperature;  //体温
int Alarm_Status; //报警

//LEDC
int freq = 2000;    // 频率
int channel = 0;    // 通道
int resolution = 8;   // 分辨率
const int Bee = 2;

BLECharacteristic *pCharacteristic;
BLECharacteristic *pCharacteristic2;
BLECharacteristic *pCharacteristic3;
BLECharacteristic *pCharacteristic4;
bool deviceConnected = true;
static BLEUUID ServiceUUID("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
static BLEUUID CharacteristicUUID("beb5483e-36e1-4688-b7f5-ea07361b26a8");
static BLEUUID Characteristic2UUID("dda0b41d-d59e-42a2-a72b-69e3410afab6");
static BLEUUID Characteristic3UUID("c136b1df-7694-4587-b123-963aac86e021");
static BLEUUID Characteristic4UUID("c137b1df-7694-4587-b123-963aac86e021");


class MyServerCallbacks : public BLEServerCallbacks
{
    void onConnect(BLEServer *pServer)
    {
      deviceConnected = true;
    };
    void onDisconnect(BLEServer *pServer)
    {
      deviceConnected = false;
    }
};


//将浮点数据转换为原始字节数据（小端）
//仅适用于蓝牙浮点数据传输
union Data {
  float flData;
  uint8_t DataA[4];
};

void setup()
{
  Wire.begin(5, 6); //I2C初始化连接串口
  ledcSetup(channel, freq, resolution); // 设置通道
  ledcAttachPin(Bee, channel);  // 将通道与对应的引脚连接
  Serial.begin(115200);   
  Serial.println("Initializing...");

  // Initialize sensor
  if (!particleSensor.begin(Wire, I2C_SPEED_FAST)) //使用自定义的I2C端口, 速率为400kHz
  {
    Serial.println("MAX30102 was not found. Please check wiring/power/solder jumper at MH-ET LIVE MAX30102 board. ");
    while (1);

  }

  //初始化MAX30102参数
  byte ledBrightness = 0x7F; //亮度选项: 0=Off to 255=50mA
  byte sampleAverage = 4; //样本均数: 1, 2, 4, 8, 16, 32
  byte ledMode = 2; //led模组模式: 1 = Red only, 2 = Red + IR, 3 = Red + IR + Green
  int sampleRate = 200; //样本频率
  int pulseWidth = 411; //数值宽度
  int adcRange = 16384; //直流电范围
  particleSensor.setup(ledBrightness, sampleAverage, ledMode, sampleRate, pulseWidth, adcRange); //初始化参数
  particleSensor.enableDIETEMPRDY();
  //蓝牙部分
  // 创建BLE设备
  BLEDevice::init("BLE_LIBRA");
  // 创建BLE服务
  BLEServer *pServer = BLEDevice::createServer();
  // 创建BLE服务
  BLEService *pService = pServer->createService(ServiceUUID);
  // 创建BLE特征
  //BLE读取特征
  pCharacteristic = pService->createCharacteristic(
                      CharacteristicUUID,
                      BLECharacteristic::PROPERTY_READ |
                      BLECharacteristic::PROPERTY_NOTIFY |
                      BLECharacteristic::PROPERTY_INDICATE
                    );
  pCharacteristic->addDescriptor(new BLE2902());


  pCharacteristic2 = pService->createCharacteristic(
                       Characteristic2UUID,
                       BLECharacteristic::PROPERTY_READ |
                       BLECharacteristic::PROPERTY_NOTIFY |
                       BLECharacteristic::PROPERTY_INDICATE
                     );
  pCharacteristic2->addDescriptor(new BLE2902());

  pCharacteristic3 = pService->createCharacteristic(
                       Characteristic3UUID,
                       BLECharacteristic::PROPERTY_READ |
                       BLECharacteristic::PROPERTY_NOTIFY |
                       BLECharacteristic::PROPERTY_INDICATE
                     );
  pCharacteristic3->addDescriptor(new BLE2902());

  pCharacteristic4 = pService->createCharacteristic(
                       Characteristic4UUID,
                       BLECharacteristic::PROPERTY_READ |
                       BLECharacteristic::PROPERTY_NOTIFY |
                       BLECharacteristic::PROPERTY_INDICATE
                     );
  pCharacteristic4->addDescriptor(new BLE2902());

  // 启动服务
  pService->start();
  // 启动广播
  pServer->getAdvertising()->start();
  union Data data;
}

void loop()
{
  int get_count = 0;
  int alaram_count = 0;
  bufferLength = 100; //缓冲区长度
  //读取前100个样本，并确定信号范围
  for (byte i = 0 ; i < bufferLength ; i++)
  {
    while (particleSensor.available() == false) //如果有新的数据
      particleSensor.check(); //检查传感器是否有新数据

    redBuffer[i] = particleSensor.getRed();
    irBuffer[i] = particleSensor.getIR();
    particleSensor.nextSample();
  }

  //计算前100个样本后的心率和SpO2（样本的前4秒）
  maxim_heart_rate_and_oxygen_saturation(irBuffer, bufferLength, redBuffer, &spo2, &validSPO2, &heartRate, &validHeartRate);

  //持续从MAX30102中取样。心率和血氧饱和度每1秒计算一次
  while (1)
  {
    Alarm_Status = 0;
    temperature = particleSensor.readTemperature();
    //将前25组样本转储到内存中，并将最后75组样本移到顶部
    for (byte i = 25; i < 100; i++)
    {
      redBuffer[i - 25] = redBuffer[i];
      irBuffer[i - 25] = irBuffer[i];
    }

    //在计算心率之前，采集25组样本。
    for (byte i = 75; i < 100; i++)
    {
      while (particleSensor.available() == false) 
        particleSensor.check(); 
      redBuffer[i] = particleSensor.getRed();
      irBuffer[i] = particleSensor.getIR();
      particleSensor.nextSample(); //转到下一个样本
      get_count++;

      //因为偶尔出现读数不准的情况，所以设定为每当出现危险数据时count+1
      if (heartRate < 60 | spo2 < 95) {
        alaram_count++;
      } else if (heartRate > 60 & spo2 > 95) {
        alaram_count = 0;
      }

    }
    //当count大于300时触发报警
    if (alaram_count > 300) {
      Alarm_Status = 1;
      for (int dutyCycle = 0; dutyCycle <= 255; dutyCycle = dutyCycle + 5)
      {
        ledcWrite(channel, dutyCycle);  // 输出LEDC
        delay(20);
      }
    } else {
      ledcWrite(channel, 0);  // 输出LEDC
      //delay(20);
    }

      Serial.print("HR=");
      Serial.print(heartRate, DEC);

      Serial.print(",SPO2=");
      Serial.print(spo2, DEC);

      Serial.print(",temperatureC=");
      Serial.print(temperature, 4);

      //Serial.print(", AlaramCount=");
      //Serial.println(alaram_count, DEC);
      if (deviceConnected) {
      //std::string datas((char*)&spo2, 4);
      int datas = (int)spo2;
      int datas2 = (int)heartRate;
      int datas3 = (int)temperature;
      pCharacteristic->setValue(datas);
      pCharacteristic->notify();

      pCharacteristic2->setValue(datas2);
      pCharacteristic2->notify();

      pCharacteristic3->setValue(datas3);
      pCharacteristic3->notify();

      pCharacteristic4->setValue(Alarm_Status);
      pCharacteristic4->notify();
    }

    maxim_heart_rate_and_oxygen_saturation(irBuffer, bufferLength, redBuffer, &spo2, &validSPO2, &heartRate, &validHeartRate);
  }
}
