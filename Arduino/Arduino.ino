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

SoftwareSerial bluetooth(bluetoothTx, bluetoothRx);
Servo myservo;  // create servo object to control a servo 

void setup()
{
  
  Serial.begin(9600);  // Begin the serial monitor at 9600bps

  bluetooth.begin(115200);  // The Bluetooth Mate defaults to 115200bps
  bluetooth.print("$");  // Print three times individually
  bluetooth.print("$");
  bluetooth.print("$");  // Enter command mode
  delay(100);  // Short delay, wait for the Mate to send back CMD
  bluetooth.println("U,9600,N");  // Temporarily Change the baudrate to 9600, no parity
  // 115200 can be too fast at times for NewSoftSerial to relay the data reliably
  bluetooth.begin(9600);  // Start bluetooth serial at 9600
  
  myservo.attach(9);  // attaches the servo on pin 9 to the servo object 
}

void loop()
{
  if(bluetooth.available()) { // If the bluetooth sent any characters
    // Send any characters the bluetooth prints to the serial monitor
    int temp = bluetooth.read();
    myservo.write(map(temp, 0 ,255 , 20, 159));
    Serial.println(temp);
  }

}

