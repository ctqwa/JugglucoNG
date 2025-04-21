/*      This file is part of Juggluco, an Android app to receive and display         */
/*      glucose values from Freestyle Libre 2, Libre 3, Dexcom G7/ONE+ and           */
/*      Sibionics GS1Sb sensors.                                                     */
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
/*      Fri Feb 07 12:28:12 CET 2025                                                 */



package tk.glucodata;

import static android.text.Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE;
import static android.text.Html.fromHtml;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static tk.glucodata.Layout.getMargins;
import static tk.glucodata.Log.doLog;
import static tk.glucodata.NumberView.avoidSpinnerDropdownFocus;
import static tk.glucodata.help.helplight;
import static tk.glucodata.settings.Settings.removeContentView;
import static tk.glucodata.util.getbutton;
import static tk.glucodata.util.getcheckbox;

import android.content.DialogInterface;
import android.os.Build;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;

class Sensors {
final private static String LOG_ID="Sensors";
/*
public static native long[] activeSensorPtrs( );
public static native String namefromSensorptr(long sensorptr);
public static native String sensortextfromSensorptr(long sensorptr);
public static native void finishfromSensorptr(long sensorptr);
*/

private static void confirmFinish(MainActivity act,long ptr) {
   var serial=Natives.namefromSensorptr(ptr);
    AlertDialog.Builder builder = new AlertDialog.Builder(act);
    builder.setTitle(serial).setMessage(R.string.finishsensormessage).
      setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
         public void onClick(DialogInterface dialog, int id) {
                Natives.finishfromSensorptr(ptr);
                act.requestRender();
                MainActivity.doonback();
                bluediag.start(act);
                }
                }).
        setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
            }
        }).show().setCanceledOnTouchOutside(false);
    }

static void setinfo(TextView info,long ptr) {
        String text=Natives.sensortextfromSensorptr(ptr);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            info.setText(fromHtml(text,TO_HTML_PARAGRAPH_LINES_CONSECUTIVE));
            }
        else {
            info.setText(fromHtml(text));
            }
        }
static void show(MainActivity act) {
    long[] ptrs=Natives.activeSensorPtrs();
    if(ptrs.length==0) {
        bluediag.nosensors(act);
        return;
        }
    var info=new TextView(act);
    info.setMovementMethod(new ScrollingMovementMethod());
    var help=getbutton(act,R.string.helpname);
    help.setOnClickListener(v-> helplight(R.string.sensormirror,act));
    var close=getbutton(act,R.string.closename);
    close.setOnClickListener(v -> {
        MainActivity.doonback();
        });
    var finish=getbutton(act,R.string.finish);
    var list=new ArrayList<Long>(ptrs.length);
    for(var p : ptrs) {
        list.add(p);
        }
    var spin=new Spinner(act);
    avoidSpinnerDropdownFocus(spin);    
    var adap = new RangeAdapter<>(list, act, ptr -> {
        if(ptr != 0L) {
            var name=Natives.namefromSensorptr(ptr);
            if(name!=null)
                return name;
            }
        return "Error";
        });
    spin.setAdapter(adap);
    int[] waspos={0};
    setinfo(info,ptrs[0]);
    finish.setOnClickListener(v -> {
        var pos=waspos[0];
        if(pos>=0&&pos<ptrs.length) {
            var ptr=ptrs[pos];
            confirmFinish(act,ptr);
            }
        });
     final boolean wasused= Natives.getusebluetooth();
     var usebluetooth=getcheckbox(act, R.string.use_bluetooth,wasused);
    usebluetooth.setOnCheckedChangeListener(
         (buttonView,  isChecked) -> {
             {if(doLog) {Log.i(LOG_ID,"usebluetooth "+isChecked);};};
             if(isChecked!=wasused) {
                 act.setbluetoothmain( isChecked);
                 act.requestRender();
                 MainActivity.doonback();
                 bluediag.start(act);
             }
         });
    spin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
        @Override
        public  void onItemSelected (AdapterView<?> parent, View view, int position, long id) {
            {if(doLog) {Log.i(LOG_ID,"onItemSelected "+position);};};
            if(position!=waspos[0]) {
                waspos[0]=position;
                setinfo(info,ptrs[position]);
               
                }

        }
        @Override
        public  void onNothingSelected (AdapterView<?> parent) {

        } });
    int pads=(int)(GlucoseCurve.metrics.density*20);
     getMargins(usebluetooth).topMargin=(int)(GlucoseCurve.metrics.density*15);
     var spinmar= getMargins(spin);
     spinmar.topMargin=pads;
     spinmar.bottomMargin=pads;

    var buttons=new Layout(act, (x,w,h)->{ 
         return new int[] {w,h};
           } 
                   , new View[]{finish},new View[]{help},new View[]{usebluetooth},new View[]{spin},new View[]{close}
          );
     buttons.usebaseline=false;
    var width=GlucoseCurve.getwidth(); 
    var height=GlucoseCurve.getheight(); 
    getMargins(finish).topMargin=(int)(MainActivity.systembarTop);
    var layout=new Layout(act, (x,w,h)->{ 
       // float ypos=(height-h+MainActivity.systembarTop-MainActivity.systembarBottom)*.5f;
        x.setX((width-w+MainActivity.systembarLeft-MainActivity.systembarRight)*.5f);
       // x.setY(ypos);
        {if(doLog) {Log.i(LOG_ID,"baselines info="+info.getBaseline()+" buttons="+buttons.getBaseline());};};
//        if(ypos<MainActivity.systembarTop) getMargins(finish).topMargin=(int)(MainActivity.systembarTop-ypos);
         return new int[] {w,h};
           },
          new View[]{info,buttons});
     layout.usebaseline=false;
    layout.setBackgroundResource(R.drawable.dialogbackground);
    layout.setPadding((int)(GlucoseCurve.metrics.density*10),0,(int)(GlucoseCurve.metrics.density*5),0);
    act.addContentView(layout, new ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
    MainActivity.setonback(() -> {
            removeContentView(layout);
            });

    }

};
