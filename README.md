# kurento-group-call-mcu

This is sample application of Kurento group call with MCU.  This application is created based on Java version of "kurento-group-call" [Kurento Tutorial](https://doc-kurento.readthedocs.io/en/latest/tutorials/java/tutorial-groupcall.html) version 6.15.0.  The original kurento-group-call shows many-to-many video conference using SFU. In this application, MCU is added on the SFU connection using composite mixer (please see Media Pipeline Structure Section).



## Required

Kurento Media Server Host (6.15.0 is used)

Environment to run Kurento Java Tutorial Sample Application

## How to Use

1. Setup Kurento-Media-Server and run Sample Tutorial of Java version.

   Kurento [Installation Guide](https://doc-kurento.readthedocs.io/en/latest/user/installation.html)

   [Java - Group Call](https://doc-kurento.readthedocs.io/en/latest/tutorials/java/tutorial-groupcall.html)

2. check out this repository

   ```
   git checkout https://github.com/kozokomiya/kurento-group-call-mcu.git
   cd kurento-group-call-mcu
   ```

3. edit 'start.sh' to specify Kurento server host in `HOST_ADR`

   ```
   # HOST name or IP Address of kurento-media-server HOST
   HOST_ADR=xx.xx.xx.xx
   
   ```

4. Run this sample application

   ```
   ./start.sh
   ```

5. Access the application connecting to the URL `https://localhost:8443/` or `https://<sample-application-host>:8443` in a WebRTC capable browser (Chrome, Firefox).

6. enter 'Room name' and 'User name' and 'join'.  The 'Room name' and the 'User name' must be different.

## Media Pipeline Structure 

Left side is pipeline structure of the original "kurento-group-call" application (SFU).

In this application, MCU connection is added on the top of SFU connection (orange part).  Composite video output stream will be displayed on the user browser as well as other user's.  Only video stream is connected to the browser in order to avoid audio howling.

  

## Issues

Composite Mixer output stream will be freezed upon user join and leave several times. It can be [known issue](https://groups.google.com/g/kurento/c/EQQFpBiJwvw/m/ZFiyrm2jBAA) on the original composte mixer of Kurento.

