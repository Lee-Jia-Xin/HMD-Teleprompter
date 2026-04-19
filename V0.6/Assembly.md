# Components

## Optics

### Objective lens

Please refer to Objective Lens documentation for instructions.

### Eyepiece lens

Please refer to Eyepiece Lens documentation for instructions.

### Mirror

The mirror used in this prototype is obtained by cutting a rectangle out of a arcylic mirror. It can be made of any mirror, but for ease of cutting using knives, acrylic was used. If you can use a first surface mirror, it will eliminate optical ghosting in this stage.

### Visor

The visor is the last optical component before the light enters the eyes. The visor is made of transparent PET sheet harvested from a pen container. It can be made of any smooth, transparent, rigid sheet including but not limited to acrylic sheet, PP sheet and glass panes. The purpose of this instrument is to reflect the display's image into the user's eyes while retaining the user's ability to see their surrounding environment.

## Electronics

### Arduino Nano

An Arduino Nano is used as the central processor for the HMD device. An SSD1306 OLED display is connected to the Arduino Nano by pins 5V, GND, A4 and A5 to VCC, GND, SDA, SCL. A HC-05 Bluetooth module is also connected to the Nano by pins 5V, GND and D6 to VCC, GND, TX.  

The arduino program is provided in `arduino code.ino` file. Please refer to it.

### HC-05

A HC-05 module is used for Bluetooth communication between the HMD device and a phone, tablet or computer. The wiring is documented in the Arduino Nano segment above. The program is also included in `arduino code.ino` file. Please refer to it.

### SSD1306

An SSD1306 OLED module is used as the primry display for the device. The wiring is documented in the Arduino Nano segment above. The program is also included in `arduino code.ino` file. Please refer to it.

# Assembly

1. An unsoldered Arduino Nano and unsoldered SSD1306 are soldered together at a 90° angle with the terminals close together, screen facing inward and Arduino nano's reset button facinng outwards.
2. An unsoldered HC-05 is soldered to the Nano via 24 AWG wires. This is to retain the abiltiy of the module to be rotated to a more convenient orientation and/or spot to fit into the housing of the device.
3. Two TP4056 modules are soldered to the VIN and GND pins of the Arduino Nano in series in order to get 7.4V from two 3.7V LiPo batteries. The B+ & B- pins are then connected to the battery's positive and negative terminals respectively.
4. to be continued (put into casing)
