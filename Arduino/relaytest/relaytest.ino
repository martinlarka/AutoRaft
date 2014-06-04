int activateRelay = 4;
int stearRelay = 7;
int stearState = LOW;
int activateState = LOW;

void setup() {
  pinMode(activateRelay, OUTPUT);
  pinMode(stearRelay, OUTPUT);
}

void loop() {
  deactivateStearing();
  starBoard();
  activateStearing();
  delay(20000);
  
  deactivateStearing();
  portSide();
  activateStearing();
  delay(20000);
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

void starBoard() {
   if (stearState == LOW) {
   stearState = HIGH;
 }
 digitalWrite(stearRelay, stearState);
}

void portSide() {
   if (stearState == HIGH) {
   stearState = LOW;
 }
 digitalWrite(stearRelay, stearState);
}
