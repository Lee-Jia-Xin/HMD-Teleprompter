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

### Batteries

A JL-CD03C module is used to connect two 250mAh 3.7V LiPo batteries in series, to the Arduino Nano. The IN+ and IN- pins are left disconnected. The batteries are connected to the BAT+ and BAT- alongside the Arduino Nano via the VIN and GND pins.

### Arduino Nano

An Arduino Nano is used as the central processor for the HMD device. An SSD1306 OLED display is connected to the Arduino Nano by pins 5V, GND, A4 and A5 to VCC, GND, SDA, SCL. A HC-05 Bluetooth module is also connected to the Nano by pins 5V, GND and D6 to VCC, GND, TX.  

The arduino program is provided in `arduino code.ino` file. Please refer to it.

### HC-05

A HC-05 module is used for Bluetooth communication between the HMD device and a phone, tablet or computer. The wiring is documented in the Arduino Nano segment above. The program is also included in `arduino code.ino` file. Please refer to it.

### SSD1306

An SSD1306 OLED module is used as the primry display for the device. The wiring is documented in the Arduino Nano segment above. The program is also included in `arduino code.ino` file. Please refer to it.

# Assembly

## Electronics

1. An unsoldered Arduino Nano and unsoldered SSD1306 are soldered together at a 90° angle with the terminals close together, screen facing inward and Arduino nano's reset button facinng outwards.
2. An unsoldered HC-05 is soldered to the Nano via 24 AWG wires. This is to retain the abiltiy of the module to be rotated to a more convenient orientation and/or spot to fit into the housing of the device.
3. Two TP4056 modules are soldered to the VIN and GND pins of the Arduino Nano in series in order to get 7.4V from two 3.7V LiPo batteries. The B+ & B- pins are then connected to the battery's positive and negative terminals respectively.

## Casing
4. Using ice cream sticks, construct a rectangular frame with dimensions 105\*40\*52 (length\*height\*width). The space that it makes will house the Electronic assembly and optics. Use hot glue to secure the joints. Spots where the optical instruments are mounted should have ice cream sticks in the way so that the intrument can rigidly mount or poke a hole through the stick. The frame will be covered with cardboard later to block light from entering.
5. The SSD1306-Nano combo is hot glued onto an ice cream stick extension with the screen facing inward and Nano at the bottom of the frame.
6. Place the Objective Lens directly in front of the screen, but angle it such that the syringe pokes out the other side of the frame. Hot glue the syringe to an ice cream stick extension.
7. Two holes are poked through the top and bottom of the frame ice cream stick inline with the screen and Objective lens. A straightened paper clip is then inserted through these holes.
8. The mirror is stuck to the straightened paperclip in a horizontal manner.
9. A small handle is stuck to the top extra portion of the paperclip.
10. The bottom extra portion of the paperclip is bent into a 90° angle to stop the paperclip from falling off the frame. Hot glue is then applied to inner parts of the bottom of the paperclip, increasing friction to hold the rotation in place while allowing rotation when needed. Forcefully rotate the paperclip to free it from the grip of the hot glue, but do not remove the hot glue blob.
11. Bend the eyepiece lens syring needle in a 90° angle to allow the lens to be vertically oriented while the syringe is horizontally oriented.
12. Stick the Eyepiece lens onto the frame in such a position and orientation that when the view is pointed at the mirror, the objective lens does not obstruct the vision of the mirror.
13. At this point the main body is almost ready. Just find a way stick the rest of components inside the frame but still close to the frame for cooling and you are basically done with the main body.
14. Next, straighten a piece of paperclip as the arm that holds the visor up.
15. Make a small 90° bend at one end to increase surface area for hot glue bonding. (let's say they represent x1 and y1 axis)
16. Make a small 30° bend along the z1 axis that is perpendicular to both x1 and y1. This serves as the initial preset angle for the arm relative to the main body. The L shape that you made here, let's all it L1.
17. At the other end, make two 2cm 90° bends in such a way that there are two parallel parts with one part perpendicular to both. The L shape that you made here, let's all it L2. Then bend L2 so that it is perpendicular to L1. It should make an obtuse angle not acute.
18. Hot glue the visor piece to L2 in... the obvious way...
19. Hot glue L1 to the edge of the main body frame such that the visor "hangs" outside of the main body frame.
20. Cover the frame with cardboard or opaque tape and it is done. Left calibration.
