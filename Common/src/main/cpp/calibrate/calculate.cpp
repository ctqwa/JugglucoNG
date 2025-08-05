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
/*      Sat Jul 05 15:19:21 CEST 2025                                                */


#include <vector>
#include <math.h>
#include <algorithm>
#include <numeric>
#include "secs.h"
#include "calculate.hpp"
#include "SensorGlucoseData.hpp"
#include "nums/numdata.hpp"
#include "sensoren.hpp"
#include "calibrateValue.hpp"
extern vector<Numdata*> numdatas;


/*
static constexpr const double  maxCalSecs=14*daysecs;
static double mkweight(double age) {
    return (maxCalSecs-age)/maxCalSecs; 
    } */
static  const ScanData *firstnotless(const ScanData *scan, const ScanData *endscan,const uint32_t tim) {
    const ScanData scanst{.t=tim};
    auto comp=[](const ScanData &el,const ScanData &se ){return el.t<se.t;};
    const ScanData *hit=std::lower_bound(scan,endscan, scanst,comp);
    while(hit<endscan&&!hit->valid())  {
        ++hit;
        }
    return hit;
    }
extern std::mutex caliMutex;
std::mutex caliMutex;
extern void setCalibrates(uint16_t sensorindex) ;
extern void removeCalibration(const Num *num) {
    if(num->type!=settings->data()->bloodvar)
        return;
    const uint32_t tim=num->gettime();
    vector<int> sens=sensors->sensorsInPeriod(tim-5*60, tim+5*60);
    if(sens.size()) {
        for(int index:sens) {
            SensorGlucoseData *sens=sensors->getSensorData(index);
                {
                const std::lock_guard<std::mutex> lock(caliMutex);
                sens->removeCali(tim);
                 }
            }
        setCalibrates(*std::ranges::min_element(sens));
        }
    }

static bool wrongChange(const ScanData *value) {
        if(value->getchange()>0.7) {
            #ifndef NOLOG
            time_t tim=value->gettime();
            LOGGER("Rises too much %.1f %s",value->getchange(),ctime(&tim));
            #endif
            return true;
            }
        if(value->getchange()<-0.7) {
            #ifndef NOLOG
            time_t tim=value->gettime();
            LOGGER("falls too much %.1f %s",value->getchange(),ctime(&tim));
            #endif
            return true;
            }
       return false;
    }

static bool notSuitable(uint32_t numtim,const ScanData *value,int maxdifference) {
        const int64_t sensLater=(int64_t)value->gettime()-numtim;
        if(sensLater>maxdifference) {
                LOGGER("too late sensor %u, blood %d %lld\n",value->gettime(),numtim,sensLater);
                return true;
                }
         return wrongChange(value);
        }

    constexpr const int maxdifference=3*60;
/*bool shouldexclude(const uint32_t time) {
   uint32_t starttime=time-maxdifference;
   uint32_t endtime=time+maxdifference;
   vector<int> indices=sensors->sensorsInPeriod(starttime, endtime);
   if(!indices.size()) 
        return true;
    bool wrong=true;
    for(int index:indices) {
        auto *sens=sensors->getSensorData(index);
        std::span<const ScanData> stream=sens->getPolldata();
        const ScanData *after=firstnotless(stream.begin(),stream.end(),time);
        for(auto *iter=cur.end()-1;iter>=cur.begin();--iter) {
            if(iter->valid()) {
                if(wrongChange(iter))
                    return true;
                #ifndef NOLOG
                time_t tim=iter->gettime();
                LOGGER("shouldExclude: good value %.1f %s",iter->getchange(),ctime(&tim));
                #endif
                wrong=false;
                break; 
                }
            }
        }
    return wrong; 
    }*/

const ScanData *findNextStream(const ScanData *startsen,const ScanData *endsen,uint32_t numtim) {
        const ScanData *after=firstnotless(startsen,endsen,numtim);
        if(after==endsen)  {
            do{
              --after;
              }while(after>startsen&&!after->valid());
            if(after==startsen) {
                LOGGER("findNextStream no value around %u\n",numtim);
                return nullptr;
                }
            if((after->gettime()+maxdifference)<numtim) {
        #ifndef NOLOG
         time_t tim=after->gettime();
                LOGGER("findNextStream: last value too early %.1f %s",after->getchange(),ctime(&tim));
        #endif
                return nullptr;
                }
            }
        if(notSuitable(numtim,after,maxdifference)) {
            return nullptr;
            }
        #ifndef NOLOG
         time_t tim=after->gettime();
        LOGGER("findNextStream: good value %.1f %s",after->getchange(),ctime(&tim));
        #endif
         return after;
         }

bool shouldexclude(const uint32_t time) {
   uint32_t starttime=time-maxdifference;
   uint32_t endtime=time+maxdifference;
   vector<int> indices=sensors->sensorsInPeriod(starttime, endtime);
   LOGGER("shouldexclude:  %u - %u %d\n",starttime,endtime,indices.size());
   if(!indices.size())  {
        LOGGER("shouldexclude: no sensors between %u and %u\n",starttime,endtime);
        return true;
        }
    for(int index:indices) {
        auto *sens=sensors->getSensorData(index);
        std::span<const ScanData> stream=sens->getPolldata();
        const ScanData *after= findNextStream(&stream.begin()[0],&stream.end()[0],time);
        if(!after) {
            return true;
            }

        }
    return false; 
    }
#include <tuple>
static std::tuple<double,double,double> calculate(const SensorGlucoseData *sens, const uint32_t newtime) {
    const auto oldtime=sens->firstpolltime();
    const int bloodvar=settings->data()->bloodvar;
    std::vector<double> y,x,w;
    std::span<const ScanData> stream=sens->getPolldata();
    const ScanData *startsen=&stream.begin()[0];
    const int mindistance=30*60;
    uint32_t nexttime=UINT_MAX;
    for(const Numdata *numdata:numdatas) {
        const ScanData *endsen=&stream.end()[0];
        const Num* endnum=numdata->firstAfter(newtime);
        const Num* begnum=numdata->begin();
        for(const Num *num=endnum-1;num>=begnum;--num) {
            if(numdata->valid(num)) {
                const uint32_t numtim=num->gettime();
                if(numtim<oldtime) {    
                    break;
                    }
                if(numtim>nexttime) {
                    #ifndef NOLOG
                    if(num->type==bloodvar) {
                        LOGGER("calibrate: %u too near previousvalue\n",numtim);
                        }
                    #endif
                    continue;
                    }
                if(!num->calibrator(bloodvar))
                    continue;
                const ScanData *after=findNextStream(startsen,endsen,numtim);
                if(!after)
                    continue;

                double weight=mkweight(newtime-numtim);
                if(weight<=0.0)
                    continue;
                nexttime=numtim-mindistance;
                endsen=after;

                const double streamvalue=after->getmgdL();
                #ifndef NOLOG
                char buf1[27],buf2[27];
                time_t tnumtim=numtim;
                time_t tsenstim=after->gettime();
                LOGGER("calculate: num %.1f %.23s stream %.1f %.23s\n",num->value,ctime_r(&tnumtim,buf1),gconvert(streamvalue*10),ctime_r(&tsenstim,buf2));
                #endif
                y.push_back(backconvert(num->value)*.1f);
                w.push_back(weight);
                x.push_back(streamvalue);
                }
            }
          }
    printvector("x",x);
    printvector("y",y);
    printvector("w",w);
    const int nr=x.size();
    const long double totweight=std::reduce(std::begin(w),std::end(w),(long double){});
    if(totweight>=4&&!settings->data()->DoNotCalibrateA) {
        const auto [meanstream,count]=mean_mgdL(stream);
        const double sdstream=sd_mgdL(meanstream,count,stream);
        const auto [meancali,countcali]=mean_mgdL(x);
        const double sdcali=sd_mgdL(meancali,countcali,x); //not devided by count-1. Data should be of the same kind
        LOGGER("mean sensor=%.2Lf mean calibratie=%.2Lf\n",meanstream,meancali);
        LOGGER("sd sensor=%.2f sd calibratie=%.2f\n",sdstream,sdcali);
        if(sdcali>(sdstream*.7)) {
            double preA=getA(w,x,y,nr);
            double a=moderateA(preA,totweight,3.0);
            double preB=getB(w,x,y,nr);
            double b=moderateB(preB,totweight,3.0);
            LOGGER("calibrate: preA=%.2f a=%.2f preB=%.2f b=%.2f\n",preA,a,preB,b);
            return {a,b,totweight}; 
             }
        }
     else {
        if(nr<1) {
            constexpr const double nan=NAN;
            return {nan,nan,nan};
            }
        }
     double preB=distance(w,x,y, nr)/totweight;
     double b =moderateB(preB,totweight,3.0);
     LOGGER("calibrate: preB=%.2f b=%.2f\n",preB,b);
     return {1.0,b,totweight};
    }

static void addCali(SensorGlucoseData *sens, const uint32_t newtime,const Num *num, const Numdata *numdata) {
    const auto [a,b,weight]=calculate(sens,  newtime);
    if(isnan(a)) {
        LOGAR("addCali a is nan");
        return;
        }
    if(isnan(b)) {
        LOGAR("addCali b is nan");
        return;
        }
      {
    const std::lock_guard<std::mutex> lock(caliMutex);
    if(num<numdata->end()&&num->gettime()==newtime) {
        sens->addCali(newtime,weight,a,b);
        }
   else {
       LOGGER("addCali %u not longer present\n",newtime);
        }
      }
    }
extern void addCalibration(uint32_t tim,int type) ;
void threadCalibration(uint32_t tim,const Num *num,const Numdata *numdata) {
    //sleep(60*5);
    vector<int> sens=sensors->sensorsInPeriod(tim-5*60, tim+5*60);
    if(sens.size()) {
        LOGGER("threadCalibration: %d sensors\n",sens.size());
        for(int index:sens) {
            addCali(sensors->getSensorData(index),tim,num,numdata);
            }
        setCalibrates(*std::ranges::min_element(sens));
        extern void render(); 
        render(); 
        backup->wakebackup(Backup::wakenums);

        }
    else {
        LOGGER("threadCalibration: no sensors at %u\n",tim);
        }
     
    }
extern void addCalibration(uint32_t tim,int type,Num *num,const Numdata *numdata) ;
void addCalibration(uint32_t tim,int type,Num *num,const Numdata *numdata) {
    if(type!=settings->data()->bloodvar)
        return;
    if(num->exclude) {
        LOGGER("addCalibration exclude %u\n",num->gettime());
        return;
        }

    if(shouldexclude(num->gettime()))  {
        LOGGER("addCalibration %u set exclude=true\n",num->gettime());
        num->exclude=true;
        return;
        }
    std::thread  th(threadCalibration,tim,num,numdata);
    th.detach();
    }

double calibrateValue(const CaliPara &cali ,const ScanData &el) {
       return calibrateValue(cali , el.gettime(),el.getmgdL());
    }

double     calibrateNowNoCheck(const SensorGlucoseData *sens,const uint32_t time, const double value) {
    const auto *info=sens->getinfo();
    const uint32_t nr=info->caliNr;
    if(nr==0) {
        return NAN;
        }
    const CaliPara &cali = info->caliPara[nr-1];
     return calibrateValue(cali,time,value);
    }

double     calibrateNow(const SensorGlucoseData *sens,const uint32_t time, const double value) {
    if(!settings->data()->DoCalibrate)
        return NAN;
    return     calibrateNowNoCheck(sens,time,  value) ;
    }
double     calibrateNowNoCheck(const SensorGlucoseData *sens,const ScanData &value) {
    return calibrateNowNoCheck(sens,value.gettime(),value.getmgdL());
    }
extern double     calibrateNow(const SensorGlucoseData *sens,const ScanData &value);
double     calibrateNow(const SensorGlucoseData *sens,const ScanData &value) {
    return calibrateNow(sens,value.gettime(),value.getmgdL());
    }

const CaliPara *getCaliBefore(const CaliPara *first,const CaliPara *end,uint32_t time) {
    CaliPara zoek;
    zoek.time=time;
    const CaliPara *cali=std::lower_bound(first,end,zoek,[](const CaliPara &one,const CaliPara &two) {
            return one.time<two.time;
            });
    if(cali==first) {
        return nullptr;
        }
    return cali-1;
    }

double     calibrateONE(const SensorGlucoseData *sens,const uint32_t time, const double value) {
    const auto *info=sens->getinfo();
    const uint32_t nr=info->caliNr;
    if(!nr)  {
        LOGGER("calibrateONE(%s,%u,%.1f) no calibrators\n",sens->shortsensorname(),time,value);
        return NAN;
        }
    const CaliPara *first = info->caliPara;
    if(settings->data()->CalibratePast) 
        return calibrateValue(first[nr-1],time,value);
        
    if(const CaliPara *cali=getCaliBefore( first,first+nr,time)) {
        return calibrateValue(*cali,time,value);
        }
    LOGGER("calibrateONE(%s,%u,%.1f) no calibrator before time\n",sens->shortsensorname(),time,value);
    return NAN;
    }
extern double     calibrateONE(const SensorGlucoseData *sens,const ScanData &value);
double     calibrateONE(const SensorGlucoseData *sens,const ScanData &value) {
    return calibrateONE(sens,value.gettime(),value.getmgdL());
    }
double     calibrateONEtest(const SensorGlucoseData *sens,const ScanData &value) {
    if(!settings->data()->DoCalibrate)
        return NAN;
     return calibrateONE(sens,value);
     }

void showCalis(const char *name,const CaliPara *first,const uint32_t nr) {
#ifndef NOLOG
    const int totlen=25+(24+12+2*8+10)*nr;
    char buf[totlen];
    int bufpos=0;
    bufpos=snprintf(buf,totlen,"%s: Calibrators nr=%u",name,nr);
    for(int i=0;i<nr;++i) {
        const CaliPara &cali=first[i];
        bufpos+=snprintf(buf+bufpos,totlen-bufpos,"\n%u a=%.2f b=%.2f %u",cali.time,cali.a,cali.b,cali.time);
        }
    LOGGERN(buf,bufpos);
#endif
    }
std::pair<const ScanData*,const ScanData*>      makecalibrated(const SensorGlucoseData *sens,const ScanData *input,ScanData *calibrated,int nr,bool allvalues) {
    const auto *info=sens->getinfo();
    const CaliPara *first= info->caliPara;
    const auto caliNr=info->caliNr;
    if(!caliNr) {
        LOGGER("%s: No calibrations\n",sens->shortsensorname()->data());
        if(allvalues) {
            memcpy(calibrated,input,nr*sizeof(input[0]));
            return {calibrated,calibrated+nr};
            }
        else
            return {};
        }
    showCalis(sens->shortsensorname()->data(),first,caliNr);

    const CaliPara *end = info->caliPara+caliNr;
    const ScanData *initer=input+nr-1;
    ScanData *outiter=calibrated+nr-1;
    while(!initer->valid()) {
        --initer;
        --outiter;
        if(initer<input) {
            LOGGER("%s: no valid stream values\n",sens->shortsensorname()->data());
            return {};
            }
        }
        
    CaliPara zoek;
    zoek.time=initer->gettime();
    const CaliPara *cali=std::lower_bound(first,end,zoek,[](const CaliPara &one,const CaliPara &two) {
            return one.time<two.time;
            });
    if(cali==first) {
    #ifndef NOLOG
        time_t tim=zoek.time;
        LOGGER("%s %u not in interval %s\n",sens->shortsensorname()->data(),zoek.time,ctime(&tim));
#endif
        if(allvalues) {
            memcpy(calibrated,input,nr*sizeof(input[0]));
            return {calibrated,calibrated+nr};
            }
        else
            return {};
        }
    --cali;
    LOGGER("%s: calibrator %u for %u\n",sens->shortsensorname()->data(),cali->time,zoek.time);
    ScanData *endout=outiter+1; 
    for(;initer>=input;--initer) {
       if(initer->valid()) {
            while(cali->time>initer->gettime()) {
                --cali;
                if(cali<first) {
                #ifndef NOLOG
                     time_t tim=initer->gettime();;
                     LOGGER("%s before first calibration %s",sens->shortsensorname()->data(), ctime(&tim));
                #endif
                     if(allvalues) {
                        int inleft=initer-input+1;
                        ScanData *startpos=outiter-inleft +1;
                        memcpy( startpos ,input,inleft*sizeof(outiter[0]));
                        return {startpos,endout};
                        }
                     else
                         return {outiter+1,endout};
                    }
                }
                double calvalue=calibrateValue(*cali,*initer);
                if(isnan(calvalue)) {
                    if(allvalues) {
                       *outiter--=*initer;
                        }
                    continue;
                    }
                *outiter=*initer;
                outiter--->g=calvalue;
              }
        }
     return {outiter+1,endout};
    }
std::pair<const ScanData*,const ScanData*>      makecalibratedback(const SensorGlucoseData *sens,const ScanData *input,ScanData *calibrated,int nr,bool allvalues) {
    const auto *info=sens->getinfo();
    const auto caliNr=info->caliNr;
    if(!caliNr) {
        if(allvalues) {
            memcpy(calibrated,input,nr*sizeof(input[0]));
            return {calibrated,calibrated+nr};
            }
         else 
            return {};
        }
    const CaliPara *cali = info->caliPara+caliNr-1;
    ScanData *outiter=calibrated;
    for(int i=0;i<nr;++i) {
        const ScanData &el=input[i];
        if(el.valid()) {
                double calvalue=calibrateValue(*cali,el);
                if(isnan(calvalue))
                    continue;
                *outiter=el;
//                outiter++->g=el.getmgdL()*cali->a+cali->b;
                outiter++->g=calvalue;
            }
        }
    return {calibrated,outiter};
    }
