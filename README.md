![value](valuemmolL.png)
# Juggluco
Displays Freestyle Libre 2 and 3, Sibionics GS1Sb, AccuChek SmartGuide and Dexcom G7 and ONE+ output

Juggluco is an app that receives glucose values via Bluetooth from Freestyle Libre 2, 2+, 3 and 3+, Chinese market Sibionics GS1Sb and Dexcom G7 and ONE+ sensors. Juggluco can scan NovoPen® 6 and NovoPen Echo® Plus.

Juggluco can send glucose values to all kinds of smartwatches, see left menu→Watch→Help.

The phone version of Juggluco can talk out incoming glucose values, display it in a widget on the home screen and in floating glucose above other apps. Juggluco can display the usual glucose statistics. Other apps can receive data from Juggluco via glucose broadcasts and the web server in Juggluco. Juggluco can send data to Health Connect, Libreview and Nightscout. Data can also be exported to a file or send to another exemplar of Juggluco on another phone, tablet, emulator or watch. It has the option to set low and high glucose alarms and medication reminders.

<h4>Start</h4>To use a Libre 2 sensor, scan it with Juggluco. To take over a Libre 3 sensor, you need to enter in left menu→Settings→Exchange data→Libreview the same account as when activating the sensor and press <i>Get account ID</i>, to receive a number from Libreview. When you now scan the sensor this number will be send to the sensor. A Libreview account is not needed when the Libre 3 sensor is activated with Juggluco: enter an arbitrary number before scanning the sensor. The first time it takes 2 to 10 minutes before Juggluco receives a glucose value via Bluetooth from the sensor. To prevent interference, force stop apps and turn off devices previously used with the sensor. To keep Juggluco running in the background, allow background activity and turn off battery optimizations for Juggluco. Don't hide Juggluco's notification.

After scanning a Libre 2 sensor with Juggluco, Abbott's Libre 2 app can only scan the sensor. When two apps are on the same phone they can both receive glucose values from European Libre 2 sensors, but this can give connection problems. Libre 3 sensors can still be used with Abbott's Libre 3 app after using it with Juggluco: stop Juggluco and scan the sensor with Abbott's Libre 3 app, agreeing that you stop the current sensor and start a new one. When going back to Juggluco you have to scan again. To use a Dexcom G7 or ONE+ sensor, scan the data matrix on the applicator with left menu→<i>Photo</i>. Do the same with the data matrix on the package, to start a Sibionics GS1Sb sensor.

WARNING: Glucose sensors are not always accurate. When a glucose reading disagrees with your feelings, use a blood glucose test strip.

<h1>Wear OS</h1>The phone version of Juggluco scans the sensor and sends this data to the WearOS version of Juggluco. Bluetooth on both phone and watch needs to be turn on. For fast transmission, also WIFI. After initialization, you can turn on and off directly connecting the sensor with the watch, with left menu→Watch→Wear OS config→<i>Direct sensor-watch connection</i>. Of the Samsung Galaxy watches, directly connecting to the sensor works very well with Watch4 as long as <b>the watch is on the same arm as the sensor</b>. Watch 5 to 7 only receive a glucose reading every 2 minutes and drain the battery quickly because they disconnect after each reading and are busy reconnecting. With Libre2 (only) TicWatch Pro works as well as Watch4, but Bluetooth is switched off if the watch isn't moved for a long time and not on the charger. Wear OS version contains a watch face and glucose complications.

https://www.juggluco.nl/Jugglucohelp/introhelp.html

## BUILD Juggluco
The following files need to be added to build Juggluco and can be found by unzipping an Arm/Arm64/x86/x86_64 Juggluco apk from
https://www.juggluco.nl/Juggluco/download.html

libcalibrat2.so and libcalibrate.so in lib/* of the APK should be put in the corresponding directories (e.g. the libraries from armeabi-v7a of the apk should be put in armeabi-v7) in:    
./Common/src/main/jniLibs/x86_64/    
./Common/src/main/jniLibs/armeabi-v7a/   
./Common/src/main/jniLibs/x86/   
./Common/src/main/jniLibs/arm64-v8a/   
   
libcrl_dp.so  liblibre3extension.so  and libinit.so  in the corresponding directories of:   
./Common/src/libre3/jniLibs/x86_64/   
./Common/src/libre3/jniLibs/armeabi-v7a/   
./Common/src/libre3/jniLibs/x86/   
./Common/src/libre3/jniLibs/arm64-v8a/   

libnative-algorithm-jni-v113B.so  libnative-encrypy-decrypt-v110.so  libnative-struct2json.so libnative-algorithm-v1_1_3_B.so   libnative-sensitivity-v110.so in   
./Common/src/mobileSi/jniLibs/armeabi-v7a/   
./Common/src/mobileSi/jniLibs/arm64-v8a/


