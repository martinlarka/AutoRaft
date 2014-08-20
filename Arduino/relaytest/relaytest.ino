int activateRelay = 7;
int stearRelay = 6;
int stearState = LOW;
int activateState = LOW;

int retracted = 720;
int extracted = 314;

void setup() {
  pinMode(activateRelay, OUTPUT);
  pinMode(stearRelay, OUTPUT);
  Serial.begin(9600);
}

void loop() {
//  stearTo(0);
//  Serial.println("DONE");
//  delay(20000);
  
  deactivateStearing();
  extract();
  delay(500);
  activateStearing();
  for (int i = 0; i<20; i++) {
    // read the input on analog pin 0:
    int sensorValue = analogRead(A0);
    // print out the value you read:
    Serial.println(sensorValue);
   delay(300); 
  }
  
  deactivateStearing();
  retract();
  delay(500);
  activateStearing();
  for (int i = 0; i<20; i++) {
    // read the input on analog pin 0:
    int sensorValue = analogRead(A0);
    // print out the value you read:
    Serial.println(sensorValue);
   delay(300); 
  }
}

void deactivateStearing() {
  if (activateState == HIGH) {
    activateState = LOW;
    digitalWrite(activateRelay, activateState); 
  }
}

void activateStearing() {
  if (activateState == LOW) {
    activateState = HIGH;
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

void stearTo(int bearing) {
  int sensorValue = analogRead(A0);
  int currentBearing = map(sensorValue, retracted, extracted, -45, 45);
  activateStearing();
  if (currentBearing < bearing) {
     extract();
  }
  else {
     retract();
  } 
  if ( currentBearing - bearing < 2 ) {
    currentBearing = bearing;
  }
  deactivateStearing();
}
