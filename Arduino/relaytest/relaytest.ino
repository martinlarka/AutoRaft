int activateRelay = 4;
int stearRelay = 7;
int stearState = LOW;
int activateState = LOW;

void setup() {
  pinMode(activateRelay, OUTPUT);
  pinMode(stearRelay, OUTPUT);
}

void loop() {
 delay(500);
 if (stearState == LOW) {
   stearState = HIGH;
 }
 else {
   stearState = LOW;   
 }
 digitalWrite(stearRelay, stearState);
 
 delay(500);
 if (activateState == LOW) {
   activateState = HIGH;
 }
 else {
   activateState = LOW;   
 }
 digitalWrite(activateRelay, activateState);
}
