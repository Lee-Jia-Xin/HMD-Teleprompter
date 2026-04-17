#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <SoftwareSerial.h>

#define SCREEN_WIDTH 128 
#define SCREEN_HEIGHT 64
#define OLED_RESET    -1
#define SCREEN_ADDRESS 0x3C

Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);
SoftwareSerial bleSerial(6, 5); // RX, TX

int yPos;
String speechBuffer = ""; 
String currentMessage = "";

void setup() {
  // Initialize I2C OLED
  if(!display.begin(SSD1306_SWITCHCAPVCC, SCREEN_ADDRESS)) {
    for(;;); // Don't proceed, loop forever if screen fails
  }

  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.setRotation(0);

  bleSerial.begin(9600); 
  Serial.begin(9600);

  // Ready Message
  display.setCursor(20, 25);
  display.println(F("HMD READY"));
  display.display();
}

void loop() {
  // 1. DATA COLLECTION
  while (bleSerial.available() > 0) {
    char c = bleSerial.read();
    if (c == '\n' || c == '\r') {
      if (speechBuffer.length() > 0) {
        currentMessage = speechBuffer;
        speechBuffer = "";
        yPos = SCREEN_HEIGHT;
        display.clearDisplay();
      }
    } else {
      speechBuffer += c;
    }
  }

  // 2. DISPLAY PHASE
  if (currentMessage != "") {
    if (currentMessage.length() > 20) { 
      scrollText(currentMessage);
    } 
    else {
      normalDisplay(currentMessage);
      currentMessage = ""; // Static display
    }
  }
  
  delay(20); 
}

void scrollText(String text) {
  display.clearDisplay();
  display.setTextSize(2);
  display.setCursor(0, yPos);
  
  wrappedPrintUtf8(text);
  display.display();
  
  yPos -= 1; 

  // Reset if text scrolls off top
  if (yPos < -80) { 
    currentMessage = "READY"; 
    display.clearDisplay();
  }
}

void normalDisplay(String text) {
  display.clearDisplay();
  display.setTextSize(2); 
  display.setCursor(0, 20);
  wrappedPrintUtf8(text);
  display.display();
}

void wrappedPrintUtf8(String text) {
  // SSD1306 has built-in wrapping, but we use your logic for UTF-8
  for (int i = 0; i < text.length(); i++) {
    uint8_t c = (uint8_t)text[i];
    
    if (c < 128) {
      display.write(c); 
    } 
    else if (c == 0xC2) {
      i++;
      uint8_t c2 = (uint8_t)text[i];
      switch(c2) {
        case 0xBF: display.write(0xA8); break; // ¿
        case 0xA1: display.write(0xAD); break; // ¡
      }
    }
    else if (c == 0xC3) {
      i++;
      uint8_t c2 = (uint8_t)text[i];
      switch(c2) {
        case 0xA1: display.write(0xA0); break; // á
        case 0xA9: display.write(0x82); break; // é
        case 0xAD: display.write(0xA1); break; // í
        case 0xB3: display.write(0xA2); break; // ó
        case 0xBA: display.write(0xA3); break; // ú
        case 0xB1: display.write(0xA4); break; // ñ
      }
    }
  }
}
