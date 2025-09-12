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
/*      Fri Sep 12 17:59:51 CEST 2025                                                */

#ifdef DEXCOM
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <jni.h>
#include "jniclass.hpp"
#include "fromjava.h"
#include "logs.hpp"
#include "streamdata.hpp"
#include "SensorGlucoseData.hpp"
#include "datbackup.hpp"
#include "hexstr.hpp"
#include "glucose.hpp"
#include "../calibrate/calculate.hpp"
extern int rate2changeindex(float rate);
/*
struct AccuData {
    uint8_t start[2];//= {0x0D,0x43};
    uint16_t preGlu:12;
    uint16_t divideGlu:4;
    uint16_t min;
    uint8_t two;
    uint16_t trend:11;
    uint16_t sign:1;
    uint16_t divideTrend:4;
    uint8_t unknown2;
    uint8_t CGMQuality;
    uint8_t rest[2];
public:

float divided(int value) const {
    switch(divide) {
        case 0xF: value/10.0f;
        case 0xE: value/100.0f;
        default: return value;
        }
    }
    float getTrend() const {
        if(sign) {
            return  divided(trend-2048);
            }
        return  divided(trend);
        }
    float mgdL() const {
        if(0xF000&preGlu)  {
            return (0xFFF&preGlu)*.1f;
            }
        return preGlu;
        }
    uint32_t getTime(uint32_t starttime) const {
        return starttime+min*60;
        }
    }__attribute__ ((packed));

*/

struct indexCmd {
    uint8_t start[3] {(uint8_t)0x01,(uint8_t)0x03,(uint8_t)0x01};
    uint16_t startIndex;

    indexCmd (uint16_t index):startIndex(index) {};
} __attribute__ ((packed)) ;

struct AccuData {
    uint8_t start[2];//= {0x0D,0x43};
    uint16_t preGlu:12;
    uint16_t divideGlu:4;
    uint16_t min;
    uint8_t two;
    uint16_t trend:11;
    uint16_t sign:1;
    uint16_t divideTrend:4;
    uint8_t CGMQuality;
    uint8_t rest[3];

//static float divider(float value,decltype(AccuData::divideTrend) divi)  {
static float divider(float value,decltype(AccuData::divideTrend) divi)  {
    switch(divi) {
        case 0xF: return value*.1f;;
        case 0xE: return value*.01f;
        default: return value;
        }
    }
/*float divided(int value) const {
    switch(divide) {
        case 0xF: return value*.1f;;
        case 0xE: return value*.01f;
        default: return value;
        }
    }*/
public:
    float getTrend() const {
        if(sign) {
            return  divider(trend-2048,divideTrend);
            }
        return  divider(trend,divideTrend);
        }
    float mgdL() const {
        return divider(preGlu,divideGlu);
        }
    uint32_t getTime(uint32_t starttime) const {
        return starttime+min*60;
        }
    }__attribute__ ((packed)) ;
 extern void wakewithcurrent();
/*
float makearrow(const SensorGlucoseData *sens,float mgdL,uint32_t was)  {
    std::vector<double> w {1.0,1.0,0.5};
    std::vector<double> x,y;
    x.push_back(was);
    y.push_back(mgdL);

    const auto stream=sens->getPolldata();
    int el=1;
    for(const ScanData *iter=&stream.back();el<3&&iter>=&stream.begin()[0];--iter,++el) {
            y.push_back(iter->getmgdL());
            x.push_back(iter->gettime());
            }
    if(el>1) {
          return getA(w,x,y,x.size())*50;
        }
    return NAN;
    } */
extern jlong glucoseback(uint32_t nu,uint32_t glval,float drate,SensorGlucoseData *hist);
extern "C" JNIEXPORT jlong JNICALL   fromjava(accuProcessData)(JNIEnv *env, jclass cl,jlong dataptr,jbyteArray value,jlong mmsec) {
    if(!value) {
        LOGAR("accuProcessData value==null");
          return 1LL;
        }
     const auto arlen=env->GetArrayLength(value);
     if(arlen<sizeof(AccuData))  {
        LOGGER("accuProcessData size  value %d < AccuData %d\n",arlen,sizeof(AccuData));
        return 0LL;
        }
      const CritAr  bluedata(env,value);

     accustream *sdata=reinterpret_cast<accustream *>(dataptr);
     SensorGlucoseData *sens=sdata->hist;
      if(!sens) {
          LOGAR("accuProcessData SensorGlucoseData==null");
          return 1LL;
         }
    const uint32_t timsec=mmsec/1000L;
    const AccuData *accu=reinterpret_cast<const AccuData *>(bluedata.data());
    if(accu->start[0]!=0x0D||accu->start[1]!=0x43)
        return 0LL;
    const uint32_t starttime=sens->getinfo()->starttime;
    uint32_t eventTime=accu->getTime(starttime);
    if(eventTime>timsec) {
        LOGGER("accuProcessData: ERROR eventtime %u > now %u\n",eventTime,timsec);
        eventTime=timsec;
        sens->getinfo()->starttime=timsec-60*accu->min;
        }
    float mgdLf=accu->mgdL();
    uint32_t mgdL= std::round(mgdLf);
    if(mgdL<40||mgdL>400) {
        LOGGER("accuProcessData: ERROR min=%d value %d mg/dL %.1f mmol/L\n",accu->min,mgdL,mgdLf/18.0);
        if((timsec-eventTime)<maxbluetoothage) {
            return 0LL;
            }
        return 1LL;
        }
   // float change=makearrow(sens, mgdLf,eventTime) ;
    float change=accu->getTrend() ;
    int abbotttrend=rate2changeindex(change);
    #ifndef NOLOG
    time_t tim=eventTime;
    const char *label=abbotttrend<6?GlucoseNow::trendString[abbotttrend]:"Error";
    LOGGER("accuProcessData glucose id=%d %.1f mmol/L rate=%.1f label=%s %s",accu->min, mgdLf/18.0f,change,label,ctime(&tim));
    #endif

    sens->savestreamonly(eventTime,accu->min,mgdL,abbotttrend, change);
    jlong res;
    if((timsec-eventTime)<maxbluetoothage) {
         sens->sensorerror=false;
         const int sensorindex=sdata->sensorindex;
         sensor *sensor=sensors->getsensor(sensorindex);
         if(sensor->finished) {
                LOGGER("accuProcessData finished was %d becomes 0\n", sensor->finished);
                sensor->finished=0;
                backup->resensordata(sensorindex);
                }
         res=glucoseback(eventTime,mgdL,change,sens);
         wakewithcurrent();
         }
      else {
        sens->receivehistory=timsec;
        res=1LL;
        }
     backup->wakebackup(Backup::wakestream);
     return res;
    }
extern "C" JNIEXPORT jbyteArray JNICALL   fromjava(accuAskValues)(JNIEnv *env, jclass cl,jlong dataptr) {
   if(!dataptr) {
       LOGAR("getAccuAskValues dataptr==0");
       return nullptr;
       }
   const SensorGlucoseData *usedhist=reinterpret_cast<streamdata *>(dataptr)->hist ; 
   if(!usedhist) {
       LOGAR("getAccuAskValues usedhist==null");
       return nullptr;
       }
   int last=usedhist->getLastIndex();

   int askindex=last>=0?(last+5):60;
#ifdef ONLY8HOURS
    uint32_t now=time(nullptr);
   uint32_t minpassed=(now-usedhist->getinfo()->starttime)/60;
   uint32_t last8hours=minpassed-8*60;
   if(askindex<last8hours)
        askindex=last8hours;
#endif
   indexCmd cmd(askindex);
   #ifndef NOLOG
   {
   const hexstr cmdstr(reinterpret_cast<const uint8_t*>(&cmd),sizeof(indexCmd));
   LOGGER("accuAskValues index=%d {%s}\n",askindex,cmdstr.str());
   }
   #endif
   constexpr const int len=sizeof(indexCmd);
   jbyteArray uit=env->NewByteArray(len);
   env->SetByteArrayRegion(uit, 0, len,reinterpret_cast<jbyte*>(&cmd));
   return uit;
   }

struct StartBytes {
     uint16_t minback;
     uint8_t same020[3];
     uint16_t unknown;
     } __attribute__ ((packed));

extern "C" JNIEXPORT void JNICALL   fromjava(accuSetStartTime)(JNIEnv *env, jclass cl,jlong dataptr,jbyteArray value) {
    if(!value) {
        LOGAR("accuSetStartTime value==null");
        return;
        }
     accustream *sdata=reinterpret_cast<accustream *>(dataptr);
     SensorGlucoseData *sens=sdata->hist;
     if(!sens) {
          LOGAR("accuSetStartTime SensorGlucoseData==null");
          return;
         }
    if(sens->pollcount()>0) {
        LOGAR("accuSetStartTime pollcount>0");
        return; 
        }
     const auto arlen=env->GetArrayLength(value);
     if(arlen<sizeof(StartBytes))  {
        LOGGER("accuSetStartTime size  value %d < StartBytes %d\n",arlen,sizeof(StartBytes));
        return;
        }
    const CritAr  bluedata(env,value);
    const StartBytes *start=reinterpret_cast<const StartBytes *>(bluedata.data());


    uint32_t now=time(nullptr);
    sens->getinfo()->starttime=now-start->minback*60;
    #ifndef NOLOG
    const time_t starttime=sens->getinfo()->starttime;
    LOGGER("accuSetStartTime minback=%d starttime=%u %s",start->minback,starttime,ctime(&starttime));
    #endif
    }
//mgdL/init.txt:2025-09-03-18:25:32 10386 start onCharacteristicRead 00002aa9-0000-1000-8000-00805f9b34fb 9A 12 00 02 00 52 8B
              //  Natives.saveStarttime(dataptr,value);
#ifdef MAIN
int main(int argc,char **argv) {
    int index=atoi(argv[1]);
    indexCmd cmd(index);
    uint8_t *ptr=(uint8_t*)&cmd;
    for(int i=0;i<sizeof(cmd);++i) {
        printf("%02x ",ptr[i]);
        }
    puts("");
    }
#endif
#endif
