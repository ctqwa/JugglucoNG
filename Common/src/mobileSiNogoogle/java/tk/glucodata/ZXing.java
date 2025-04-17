package tk.glucodata;

import static tk.glucodata.Applic.Toaster;
import static tk.glucodata.Applic.isWearable;
import static tk.glucodata.Applic.useZXing;
import static tk.glucodata.MainActivity.REQUEST_BARCODE;
import static tk.glucodata.Sibionics.connectSensor;

import static com.google.zxing.integration.android.IntentIntegrator.DATA_MATRIX;
import static com.google.zxing.integration.android.IntentIntegrator.QR_CODE;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import android.app.Activity;
import android.content.Intent;

class ZXing {
    final static String LOG_ID="ZXing";
    static void scanZXing(Activity act) {
         if(!isWearable&&useZXing) {
             IntentIntegrator intentIntegrator = new IntentIntegrator(act);
             intentIntegrator.setPrompt(Applic.app.getString(R.string.photomessage));
             intentIntegrator.setOrientationLocked(true); 
             intentIntegrator.setDesiredBarcodeFormats( DATA_MATRIX, QR_CODE);
             intentIntegrator.setRequestCode(REQUEST_BARCODE);
             intentIntegrator.initiateScan(); 
             }
          }
    static void zXingResult(int resultCode, Intent data,MainActivity act) {
         if(!isWearable&&useZXing) {
               Log.i(LOG_ID,"zXingResult(" +resultCode+",data)");
               IntentResult intentResult = IntentIntegrator.parseActivityResult(resultCode, data);
               if (intentResult != null) {
                  final var scan=intentResult.getContents();
                  if ( scan== null) 
                    Toaster( "Cancelled");
                   else {
                        Log.i(LOG_ID,"Scan: "+scan);
                        Toaster(scan);
                        connectSensor(scan,act);
                        return;
                     }
                  }
                 else {
                    Log.i(LOG_ID,"intentResult == null"); 
                    }
                }
            return;
           }
           
}
