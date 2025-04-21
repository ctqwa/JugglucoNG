/*      This file is part of Juggluco, an Android app to receive and display         */
/*      glucose values from Freestyle Libre 2 and 3 sensors.                         */
/*                                                                                   */
/*      Copyright (C) 2021 Jaap Korthals Altes <jaapkorthalsaltes@gmail.com>         */
/*                                                                                   */
/*      Juggluco is free software: you can redistribute it and/or modify             */
/*      it under the terms of the GNU General Public License as published            */
/*      by the Free Software Foundation, either version 3 of the License, or         */
/*      (at your option) any later version.                                          */
/*                                                                                   */
/*      Juggluco is distributed in the hope that it will be useful, but              */
/*      WITHOUT ANY WARRANTY; without even the implied warranty of                   */
/*      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.                         */
/*      See the GNU General Public License for more details.                         */
/*                                                                                   */
/*      You should have received a copy of the GNU General Public License            */
/*      along with Juggluco. If not, see <https://www.gnu.org/licenses/>.            */
/*                                                                                   */
/*      Thu Apr 04 20:10:13 CEST 2024                                                 */


package tk.glucodata;


import static tk.glucodata.Applic.Toaster;
import static tk.glucodata.Applic.isWearable;
import static tk.glucodata.Applic.useZXing;
import static tk.glucodata.InsulinTypeHolder.getradiobutton;
import static tk.glucodata.Log.doLog;
import static tk.glucodata.ZXing.scanZXing;
import static tk.glucodata.settings.Settings.removeContentView;
import static tk.glucodata.util.getbutton;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.Toast;

//import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
//import com.google.mlkit.vision.barcode.common.Barcode;
//import com.google.mlkit.vision.barcode.common.Barcode;
//import com.google.mlkit.vision.barcode.common.Barcode;

//import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
//import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

public class Sibionics {
private static final String LOG_ID="Sibionics";


private static void     wrongtag() {
    Toaster("Wrong QR code") ;
    }




/*
E007-0M0063KNUJ0....
      LT2309GEPD   
802JPPLT2309GEPD

GEPD802J
31108 GEPD802J PP7
Sensorname
sensorgegs
int len=sensorgegs.size();
startpos=len-19

longserialnumber 


(01) 06972831640165
(11) 231209
(17) 241208
(10) LT41 231108 C

(21) 231108 GEPD802J PP76

0106972831640165112312091724120810LT41231108C21231108GEPD802JPP76 
^]0106972831641483112411201726051910LT46241155C^]21P22411J6EP
0106972831640165112312091724120810LT41231108C21231108 GEPD 802J PP76 
*/
//LT2309GEPD
/*
Sibionics2:
*/

private static void selectType(long dataptr,MainActivity act) {
    int subtype=Natives.getSiSubtype(dataptr);
    if(subtype==3) {
        Natives.freedataptr(dataptr);
        return;
        }

    var group=new RadioGroup(act);
    int id=0;
    group.addView(getradiobutton(act,R.string.eusibionics,id++));
    group.addView(getradiobutton(act,R.string.hematonix,id++));
    group.addView(getradiobutton(act,R.string.chsibionics,id));
//    group.addView(getradiobutton(act,R.string.sibionics2,id));

    group.check(subtype);

    group.setOnCheckedChangeListener( (g,i)-> {
        Natives.setSiSubtype(dataptr,i);
         });
   var ok=getbutton(act, R.string.ok);
    int height = GlucoseCurve.getheight();
    int width = GlucoseCurve.getwidth();
   final int rand=(int)tk.glucodata.GlucoseCurve.metrics.density*15;
   group.setPadding(rand,rand,(int)tk.glucodata.GlucoseCurve.metrics.density*25,(int)tk.glucodata.GlucoseCurve.metrics.density*20);
   var layout=new Layout(act,(l,w,h)->{
         l.setX((width-w)*.5f);
         l.setY((height-h)*.3f);
         return new int[] {w,h};
           },new View[]{group},new View[]{ok});
   layout.setBackgroundColor(Applic.backgroundcolor);
   layout.setPadding(0,0,0,rand);
   MainActivity.setonback(() -> {
      removeContentView(layout);
      Natives.freedataptr(dataptr);
      });
   ok.setOnClickListener(v-> {
        MainActivity.doonback();
        });

    act.addContentView(layout, new ViewGroup.LayoutParams(WRAP_CONTENT,WRAP_CONTENT));
    }


static void connectSensor(final String scantag,MainActivity act)  {
     if(!isWearable) {
            if(scantag.endsWith("MirrorJuggluco")) {
                MirrorString.makeMirror(scantag,act);
                return;
                }
             else {
                String name=Natives.addSIscangetName(scantag);
                if(name!=null)  {
                   MainActivity.tocalendarapp=true;
                   var dataptr= Natives.getdataptr(name);
                   int type=Natives.getLibreVersion(dataptr);
                   {if(doLog) {Log.i(LOG_ID,"type="+type);};};
                   if(type== 0x10) {
                        selectType(dataptr,act);
                        }
                   else
                        Natives.freedataptr(dataptr);
                   var res=SensorBluetooth.updateDevices();
                   Applic.wakemirrors();
                   if(res) {
                        act.finepermission(); 
                        }
                      else
                        act.systemlocation();
                   return;
                   }
                }
             }
          wrongtag(); 
         }





public static void scan(MainActivity act) {
    if (!isWearable) {
        if(!Natives.getGoogleScan())
            scanZXing(act);
        else {
            try {
                scanGoogle(act);
                } catch (Throwable th) {
                    Log.stack(LOG_ID, "scanGoogle", th);
                    scanZXing(act);
                    }
           }
      }
}
private static void scanGoogle(MainActivity act) {
     if(!isWearable) {
        if(doLog) {Log.i(LOG_ID, "before scan");};
        final var options =  new com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder().setBarcodeFormats( com.google.mlkit.vision.barcode.common.Barcode.FORMAT_DATA_MATRIX, com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE).build();
        final var scanner =  com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(act, options);
        scanner.startScan().addOnSuccessListener(
           barcode -> {
               var rawValue = barcode.getRawValue();
               var message="Scanned: "+rawValue;
               if(doLog) {Log.i(LOG_ID,message);};
               connectSensor(rawValue,act);
               })
           .addOnCanceledListener(
               () -> {
                    var message="Scan cancelled";
                    if(doLog) {Log.i(LOG_ID,message);};
                    Toast.makeText(act, message, Toast.LENGTH_LONG).show();
                     // Task canceled
                   })
       .addOnFailureListener(
           e -> {
            var message=e.getMessage();
            if(doLog) {Log.i(LOG_ID,message);};
            Toast.makeText(act, message, Toast.LENGTH_SHORT).show();  
            if(useZXing) {
                Toast.makeText(act, "Move to zXing", Toast.LENGTH_SHORT).show();
                scanZXing(act);
                }
        
         // Task failed with an exception
           });

   }
    }

/*
static void testsibionics() {
if(doLog) {
  String tag="^]0106972831640820112312221724122110LT48231127G^]212311271NTK237GAA21";
  var name=Natives.addSIscangetName(tag);
  long dataptr=Natives.getdataptr(name);
  var si=new SiGattCallback(name, dataptr);
 si.testchanged();
  }
};*/
}
