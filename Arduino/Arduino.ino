/*
  Example Bluetooth Serial Passthrough Sketch
 by: Jim Lindblom
 SparkFun Electronics
 date: February 26, 2013
 license: Public domain

 This example sketch converts an RN-42 bluetooth module to
 communicate at 9600 bps (from 115200), and passes any serial
 data between Serial Monitor and bluetooth module.
 */
#include <SoftwareSerial.h>
#include <Servo.h> 

int bluetoothTx = 2;  // TX-O pin of bluetooth mate, Arduino D2
int bluetoothRx = 3;  // RX-I pin of bluetooth mate, Arduino D3
int lastHeading = 125;

int activateRelay = 4;
int stearRelay = 7;
int stearState = LOW;
int activateState = LOW;

int retracted = 720;
int extracted = 314;

int bearing = 0;

SoftwareSerial bluetooth(bluetoothTx, bluetoothRx);

void setup()
{
  pinMode(activateRelay, OUTPUT);
  pinMode(stearRelay, OUTPUT);
  
  Serial.begin(9600);  // Begin the serial monitor at 9600bps

  bluetooth.begin(115200);  // The Bluetooth Mate defaults to 115200bps
  bluetooth.print("$");  // Print three times individually
  bluetooth.print("$");
  bluetooth.print("$");  // Enter command mode
  delay(100);  // Short delay, wait for the Mate to send back CMD
  bluetooth.println("U,9600,N");  // Temporarily Change the baudrate to 9600, no parity
  // 115200 can be too fast at times for NewSoftSerial to relay the data reliably
  bluetooth.begin(9600);  // Start bluetooth serial at 9600
}

void loop() {
  int sensorValue = analogRead(A0);
  int currentBearing = map(sensorValue, retracted, extracted, -45, 45);
  
  if(bluetooth.available()) { // If the bluetooth sent any characters
    // Send any characters the bluetooth prints to the serial monitor
    int temp = bluetooth.read();
    bearing = map(temp, 0 ,255 , -45, 45); 
    stearTo(bearing, currentBearing);
  }
  else {
   if (bearing - currentBearing > 2) {
    stearTo(bearing, currentBearing);
  }
  else {
    deactivateStearing();
    delay(400); 
  }
   Serial.print("current bearing: ");
   Serial.println(currentBearing);
   Serial.print("bearing: ");
   Serial.println(bearing);
  }
  delay(100);
}

void deactivateStearing() {
  if (activateState == LOW) {
    activateState = HIGH;
    digitalWrite(activateRelay, activateState); 
  }
}

void activateStearing() {
  if (activateState == HIGH) {
    activateState = LOW;
    digitalWrite(activateRelay, activateState); 
  }
}

void extract() {
   if (stearState == LOW) {
   stearState = HIGH;
 }
 digitalWrite(stearRelay, stearState);
}

void retract() {
   if (stearState == HIGH) {
   stearState = LOW;
 }
 digitalWrite(stearRelay, stearState);
}

void stearTo(int bearing, int currentBearing) {
  activateStearing();
  if (currentBearing < bearing) {
     extract();
     Serial.println("extract");
  }
  else {
     Serial.println("retract");
     retract();
  } 
}

