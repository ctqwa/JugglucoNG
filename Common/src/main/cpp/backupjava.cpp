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
/*      Fri Jan 27 12:35:35 CET 2023                                                 */


#include <jni.h>
#include <alloca.h>
#include "fromjava.h"
#include "datbackup.hpp"
#include "net/netstuff.hpp"
#include <string_view>
extern jclass JNIString;
extern jstring myNewStringUTF(JNIEnv *env,const std::string_view str);
void netwakeup();
extern bool networkpresent;

extern "C" JNIEXPORT jboolean  JNICALL   fromjava(backuphasrestore)(JNIEnv *env, jclass cl) {
    return backup->getupdatedata()->hasrestore;
    }
extern "C" JNIEXPORT jint  JNICALL   fromjava(backuphostNr)(JNIEnv *env, jclass cl) {
    return backup->    gethostnr();
    }

extern "C" JNIEXPORT jboolean  JNICALL   fromjava(detectIP)(JNIEnv *envin, jclass cl,jint pos) {
    const passhost_t &host=backup->getupdatedata()->allhosts[pos];
    return host.detect;
    }
extern "C" JNIEXPORT jboolean  JNICALL   fromjava(getbackupHasHostname)(JNIEnv *envin, jclass cl,jint pos) {
    const passhost_t &host=backup->getupdatedata()->allhosts[pos];
    return host.hashostname();
    }
extern "C" JNIEXPORT jobjectArray  JNICALL   fromjava(getbackupIPs)(JNIEnv *env, jclass cl,jint pos) {
    if(!backup)  {
        LOGSTRING("backup==null\n");
        return nullptr;
        }
    const auto hostnr=backup->gethostnr();
    if(pos>=hostnr) {
        LOGGER("pos(%d)>=backup->gethostnr()(%d)\n",pos,hostnr);
        return nullptr;
        }
    passhost_t &host=backup->getupdatedata()->allhosts[pos];
    LOGGER("%s pos=%d nr=%d index=%d\n",host.getnameif(),pos,host.nr,host.index);
    int len=host.nr;
    if(len<0||len>passhost_t::maxip) {
        LOGGER("host.nr==%d\n",len);
        host.nr=len=0;
        }
    jobjectArray  ipar = env->NewObjectArray(len,JNIString,nullptr);
    if(!ipar) {
        LOGGER(R"(NewObjectArray(%d,JNIString==null)""\n",len);
        return nullptr;
        }
    if(len>0) {
        if(host.hashostname()) {
            env->SetObjectArrayElement(ipar,0,env->NewStringUTF(host.gethostname()));
            }
        else  {
            for(int i=0;i<len;i++) {
                namehost name(host.ips+i);
                LOGGER("%s\n",name.data());
                env->SetObjectArrayElement(ipar,i,env->NewStringUTF(name));
                }
            }
        }
    return ipar;
    }

/*
extern "C" JNIEXPORT jstring JNICALL   fromjava(getbackuphostname)(JNIEnv *envin, jclass cl,jint pos) {
    if(address(backup->getupdatedata()->allhosts[pos])) {
        auto host=backup->gethost(pos);
        return envin->NewStringUTF(host);
        }
    return nullptr;
    }
    */


int getposbylabel(const char *label) {
    const int nr=backup->gethostnr();
    for(int pos=0;pos<nr;++pos) {
        const passhost_t &host=backup->getupdatedata()->allhosts[pos];
        if(!host.hasname)
           continue;
        if(!strcmp(host.getname(),label)) {
            LOGGER("getposbylabel(%s)=%d\n",label,pos);
            return pos;
            }
         }
    LOGGER("getposbylabel(%s)=-1\n",label);
    return -1;
    }
bool removebylabel(const char *label) {
    int pos = getposbylabel(label);
    if (pos < 0)
        return false;
    backup->deletehost(pos);
    return true;
    }
/*
extern "C" JNIEXPORT jboolean JNICALL   fromjava(removebylabel)(JNIEnv *env, jclass cl,jstring jlabel) {
      const char *label = env->GetStringUTFChars( jlabel, NULL);
        if(!label) return false;
        destruct   dest([jlabel,label,env]() {env->ReleaseStringUTFChars(jlabel, label);});
    return removebylabel(label);
    } */

#ifndef ABBOTT
passhost_t * getwearoshost(const bool create,const char *label,bool,bool=false);
bool resetbylabel(const char *label,bool galaxy) {
    
    int pos=getposbylabel(label);
    if(pos<0)
        return false;
    const passhost_t &host=backup->getupdatedata()->allhosts[pos];
    const int nr=host.nr;
    if(nr>0) {
        struct sockaddr_in6 ips[passhost_t::maxip];
        memcpy(ips,host.ips,sizeof(ips));

        passhost_t *newhost=getwearoshost(true,label,galaxy,true);
        memcpy(newhost->ips,ips,sizeof(ips));
        newhost->nr=std::min(nr,passhost_t::maxip);
        }
    else {
        backup->deletehost(pos);
        }
    return true;
    }
    
extern "C" JNIEXPORT jboolean JNICALL   fromjava(resetbylabel)(JNIEnv *env, jclass cl,jstring jlabel,jboolean galaxy) {
    const char *label = env->GetStringUTFChars( jlabel, NULL);
    if(!label) return false;
    LOGGER("resetbylabel(%s,%d)\n",label,galaxy);
    destruct   dest([jlabel,label,env]() {env->ReleaseStringUTFChars(jlabel, label);});
    return resetbylabel(label,galaxy);
    }
#endif
const char *gethostlabel(int pos) {
    if(!backup||pos>=backup->gethostnr())
        return nullptr;
    const passhost_t &host=backup->getupdatedata()->allhosts[pos];
    if(!host.hasname)
        return nullptr;
    return host.getname();
    }
bool gethosttestip(int pos) {
    if(!backup||pos>=backup->gethostnr())
        return true;
    const passhost_t &host=backup->getupdatedata()->allhosts[pos];
    return !host.noip;
    }
extern "C" JNIEXPORT jboolean JNICALL   fromjava(getbackuptestip)(JNIEnv *envin, jclass cl,jint pos) {
    return gethosttestip(pos);
    }
extern "C" JNIEXPORT jstring JNICALL   fromjava(getbackuplabel)(JNIEnv *envin, jclass cl,jint pos) {
    if(const char *label=gethostlabel(pos))
        return myNewStringUTF(envin,label);
    return nullptr;
    }
extern "C" JNIEXPORT jstring JNICALL   fromjava(getbackuppassword)(JNIEnv *envin, jclass cl,jint pos) {
    if(!backup||pos>=backup->gethostnr())
        return nullptr;
    return myNewStringUTF(envin,backup->getpass(pos).data());
    }
extern "C" JNIEXPORT jstring JNICALL   fromjava(getbackuphostport)(JNIEnv *envin, jclass cl,jint pos) {
    if(!backup||pos>=backup->gethostnr())
        return nullptr;
    
    char port[6];
    backup->getport(pos,port);
    return envin->NewStringUTF(port);
    }
extern "C" JNIEXPORT jboolean JNICALL   fromjava(isWearOS)(JNIEnv *envin, jclass cl,jint pos) {
    if(!backup||pos<0||pos>=backup->gethostnr()) {
        LOGGER("isWearos(%d)=false\n",pos);
        return false;
        }
    auto ret= backup->getupdatedata()->allhosts[pos].wearos;
    LOGGER("isWearos(%d)=%d\n",pos,ret);
    return ret;
    }


bool getpassive(int pos);
bool getactive(int pos); 
extern "C" JNIEXPORT jboolean JNICALL   fromjava(getbackuphostactive)(JNIEnv *envin, jclass cl,jint pos) {
    bool active=getactive(pos);
    LOGGER("getbackuphostactive(%d)=%d\n",pos,active);
    return active;
    }

extern "C" JNIEXPORT jboolean JNICALL   fromjava(getbackuphostpassive)(JNIEnv *envin, jclass cl,jint pos) {
    return getpassive(pos);
    }
extern "C" JNIEXPORT int JNICALL   fromjava(getbackuphostreceive)(JNIEnv *envin, jclass cl,jint pos) {
    if(pos<backup->getupdatedata()->hostnr) 
        return backup->getupdatedata()->allhosts[pos].receivefrom;
    return 0;
    }
extern "C" JNIEXPORT jboolean JNICALL   fromjava(getbackuphostnums)(JNIEnv *envin, jclass cl,jint pos) {
    if(pos<backup->getupdatedata()->hostnr) {
        int index=backup->getupdatedata()->allhosts[pos].index;
        if(index>=0)
            return  backup->getupdatedata()->tosend[index].sendnums;
        }
    return false;
    }
extern "C" JNIEXPORT jboolean JNICALL   fromjava(getbackuphoststream)(JNIEnv *envin, jclass cl,jint pos) {
    if(pos<backup->getupdatedata()->hostnr) {
        int index=backup->getupdatedata()->allhosts[pos].index;
        if(index>=0)
            return  backup->getupdatedata()->tosend[index].sendstream;
        }
    return false;
    }
extern "C" JNIEXPORT jboolean JNICALL   fromjava(getbackuphostscans)(JNIEnv *envin, jclass cl,jint pos) {
    if(pos<backup->getupdatedata()->hostnr) {
        int index=backup->getupdatedata()->allhosts[pos].index;
        if(index>=0)
            return  backup->getupdatedata()->tosend[index].sendscans;
        }
    return false;
    }
extern "C" JNIEXPORT void JNICALL   fromjava(setreceiveport)(JNIEnv *env, jclass cl,jstring jport) {
    jint portlen= env->GetStringUTFLength( jport);
    if(portlen<6) {
        char newport[portlen+1];
        jint jlen = env->GetStringLength( jport);
         env->GetStringUTFRegion( jport, 0,jlen, newport); 
        if(backup->getupdatedata()->port[portlen]||memcmp(newport, backup->getupdatedata()->port,portlen)) {
            memcpy(backup->getupdatedata()->port,newport,portlen);
            backup->getupdatedata()->port[portlen]='\0';
    //        backup->stopreceiver();
            backup->startreceiver(true);
            }
        }
    }
extern "C" JNIEXPORT jstring JNICALL   fromjava(getreceiveport)(JNIEnv *env, jclass cl) {

    return env->NewStringUTF(backup->getupdatedata()->port);
    }
/*
extern "C" JNIEXPORT jboolean JNICALL   fromjava(stringarray)(JNIEnv *env, jclass cl,jobjectArray jar ) {
    constexpr const int maxad=4;    
    int len=env->GetArrayLength(jar);
    LOGSTRING("stringarray ");
    const char port[]="8795";
    struct sockaddr_in6     connect[maxad];
    int uselen=std::min(maxad,len);
    for(int i=0;i<uselen;i++) {
        jstring  jname=(jstring)env->GetObjectArrayElement(jar,i);
        int namelen= env->GetStringUTFLength( jname);
        char name[namelen+1];
        env->GetStringUTFRegion( jname, 0,namelen, name); name[namelen]='\0';
        LOGGER("%s ",name);
        if(!getaddr(name,port,connect+i))
            return  false;
        }
    LOGSTRING("\nips: ");
    for(int i=0;i<uselen;i++) {
        LOGGER("%s ",namehost(connect+i));
        }
    LOGSTRING("\n");
    return true;
    }
    */

//extern "C" JNIEXPORT jint JNICALL   fromjava(changebackuphost)(JNIEnv *env, jclass cl,jint pos,jobjectArray jnames,jint nr,jboolean detect,jstring jport,jboolean nums,jboolean stream,jboolean scans,jboolean recover,jboolean receive,jboolean reconnect,jboolean accepts,jstring jpass,jlong starttime) {
//extern bool mkwearos;

#ifndef TESTMENU
#include <mutex>
extern std::mutex change_host_mutex;
#endif
extern "C" JNIEXPORT jint JNICALL   fromjava(changebackuphost)(JNIEnv *env, jclass cl,jint pos,jobjectArray jnames,jint nr,jboolean detect,jstring jport,jboolean nums,jboolean stream,jboolean scans,jboolean recover,jboolean receive,jboolean activeonly,jboolean passiveonly,jstring jpass,jlong starttime,jstring jlabel,jboolean testip,jboolean hashostname) {
#ifndef TESTMENU
    LOGAR("changebackuphost const std::lock_guard<std::mutex> lock(change_host_mutex)");
  const std::lock_guard<std::mutex> lock(change_host_mutex);
#endif
LOGGER("changebackuphost(%d,%p,%d,%d,%p,%d,%d,%d,%d%,%d,%d,%d,%p,%ld,%p,%d,%d)\n", pos, jnames, nr, detect, jport, nums, stream, scans, recover, receive, activeonly, passiveonly, jpass, starttime, jlabel, testip, hashostname);
    jint portlen= env->GetStringUTFLength( jport);
    jint jlen = env->GetStringLength( jport);
    char port[portlen+1]; env->GetStringUTFRegion( jport, 0,jlen, port); port[portlen]='\0';
    char *passptr=nullptr;
    jint passlen=0;
    if(jpass) {
        passlen= env->GetStringUTFLength( jpass);

        jint jpasslen = env->GetStringLength( jpass);
        passptr=(char *)alloca(passlen+1); 
        env->GetStringUTFRegion( jpass, 0,jpasslen, passptr); passptr[passlen]='\0';
        }
    const char *label=jlabel?env->GetStringUTFChars( jlabel, NULL):nullptr;
     jint res=backup->changehost(pos,env,jnames,nr,detect,std::string_view(port,portlen),nums,stream,scans,recover,receive,activeonly,std::string_view(passptr,passlen),starttime,passiveonly,label,testip,true,hashostname);
    if(jlabel)
        env->ReleaseStringUTFChars(jlabel, label);
    return res;
    }
extern "C" JNIEXPORT jboolean JNICALL   fromjava(isreceiving)(JNIEnv *env, jclass cl) {
    return backup->isreceiving() ;
    }
extern "C" JNIEXPORT void JNICALL   fromjava(deletebackuphost)(JNIEnv *env, jclass cl,jint pos) {
    backup->deletehost(pos);
    }
extern "C" JNIEXPORT jlong JNICALL   fromjava(lastuptodate)(JNIEnv *env, jclass cl,jint pos) {
    return lastuptodate[pos]*1000LL;
    }
extern "C" JNIEXPORT void JNICALL   fromjava(setWifi)(JNIEnv *env, jclass cl,jboolean val) {
    settings->data()->keepWifi=val;
    }
extern "C" JNIEXPORT jboolean JNICALL   fromjava(getWifi)(JNIEnv *env, jclass cl) {
    return settings->data()->keepWifi;
    }
extern "C" JNIEXPORT jboolean JNICALL   fromjava(stopWifi)(JNIEnv *env, jclass cl) {
    if(settings->data()->keepWifi)
        return false;
        
    return     (time(nullptr)-((long)lastuptodate[0]))<2*60;
    }

extern "C" JNIEXPORT void JNICALL   fromjava(resetbackuphost)(JNIEnv *env, jclass cl,jint pos) {
    backup->resethost(pos) ;
    }
extern void wakeaftermin(const int waitmin) ;
extern void wakeuploader();

extern "C" JNIEXPORT void JNICALL   fromjava(networkpresent)(JNIEnv *env, jclass cl) {
      LOGAR("networkpresent");
    if(backup) {
        backup->getupdatedata()->wakesender();
        networkpresent=true;
        backup->notupdatedsettings();
    //    backup->wakebackup();
        }
    else
        networkpresent=true;

    wakeuploader();
#if !defined(WEAROS) && !defined(TESTMENU)
     wakeaftermin(0) ;
#endif
    LOGAR("end networkpresend");
    }
void resetnetwork() {
    LOGSTRING("resetnetwork\n");
    if(backup) {
        backup->closeallsocks();
        backup->getupdatedata()->wakesender();
        networkpresent=true;
        backup->notupdatedsettings();
        }
    }
extern "C" JNIEXPORT void JNICALL   fromjava(resetnetwork)(JNIEnv *env, jclass cl) {
    resetnetwork();
    }

extern "C" JNIEXPORT void JNICALL   fromjava(networkabsent)(JNIEnv *env, jclass cl) {
      LOGSTRING("networkabsent\n");
    resetnetwork();
/*    networkpresent=false;
    if(backup) {
        backup->closeallsocks();
        } */
    }
extern "C" JNIEXPORT void JNICALL   fromjava(wakestreamsender)(JNIEnv *env, jclass cl) {
    if(backup) {
        backup->getupdatedata()->wakestreamsender();
        }
    }
extern "C" JNIEXPORT void JNICALL   fromjava(wakestreamhereonly)(JNIEnv *env, jclass cl) {
    if(backup) {
        backup->wakebackup(Backup::wakestream);
        }
    }
    /*
extern "C" JNIEXPORT void JNICALL   fromjava(wakeallsender)(JNIEnv *env, jclass cl) {
    if(backup) {
        backup->getupdatedata()->wakesender();
        }
    } */
extern "C" JNIEXPORT void JNICALL   fromjava(wakebackup)(JNIEnv *env, jclass cl) {
    if(backup) {
        backup->getupdatedata()->wakesender();
        backup->wakebackup();
        }
    }
extern "C" JNIEXPORT void JNICALL   fromjava(wakehereonly)(JNIEnv *env, jclass cl) {
    if(backup) {
        backup->wakebackup();
        }
    }

extern "C" JNIEXPORT jboolean JNICALL   fromjava(getHostDeactivated)(JNIEnv *envin, jclass cl,jint pos) {
    if(pos<backup->getupdatedata()->hostnr) {
        return backup->getupdatedata()->allhosts[pos].deactivated;
        }
    return true;
    }
extern "C" JNIEXPORT void JNICALL   fromjava(setHostDeactivated)(JNIEnv *envin, jclass cl,jint pos,jboolean val) {
    backup->deactivateHost(pos,val);
    }



#if !defined(WEAROS)&& __NDK_MAJOR__ >= 26
template <typename OUTTYPE> int getownips(OUTTYPE *outips,int max,bool &haswlan);

template <typename OutputIt>
OutputIt insertbool( OutputIt inserter, std::string_view name,bool value) {
        return std::format_to( inserter,R"(,"{}":{})",name,value);
        }



static std::string escape(std::string_view input) {
    std::string out;
    out.reserve(input.size()+2);
    auto inserter=std::back_inserter(out);
    const char *end=input.end();
    const constexpr std::string_view zoek=R"("\/)";
    for(auto *iter=input.data();;) {
        auto *hit=std::find_first_of(iter,end,std::begin(zoek),std::end(zoek));
        std::copy(iter,hit,inserter);
        if(hit==end)
            return out;
       *inserter++='\\';
       *inserter++=*hit++;
       iter=hit;
       } 
    }


std::string mkbackjson(int pos) {
    struct updatedata &back=*backup->getupdatedata();
    if(pos<0||pos>= back.hostnr) {
        return ""s;
        }
    std::string uit;
    uit.reserve(1024);
    auto inserter=std::back_inserter(uit);
    const passhost_t &host=back.allhosts[pos];
    if(!host.hashostname()) {
        const int maxi=passhost_t::maxip-host.hasname;
        alignas (namehost) uint8_t buf[maxi*sizeof(namehost)];
        namehost *ips=reinterpret_cast<namehost*>(buf); //skip constructor
        bool haswlan;
        int ipnr=getownips(ips,maxi,haswlan);
        inserter=std::format_to( inserter,R"({{"nr":{},"names":[)",ipnr);
        if(ipnr>0) {
            for(int i=0;;) {
                const char *start=ips[i].data();
                inserter=std::format_to( inserter,R"("{}")",start);
                if(++i>=ipnr) 
                    break;
                *inserter++=',';
                }
            }
        if(ipnr||host.getActive()) {
            char detect[]=R"(],"detect":false)";
            inserter=std::copy(std::begin(detect),std::end(detect)-1,inserter);
            }
        else {
            char detect[]=R"(],"detect":true)";
            inserter=std::copy(std::begin(detect),std::end(detect)-1,inserter);
            }
//        inserter=insertbool(inserter, "hasname",false);
        }
     else {
          char empty[]=R"({"nr":0,"names":[],"detect":true)";
          inserter=std::copy(std::begin(empty),std::end(empty)-1,inserter);
          inserter=insertbool(inserter, "hasname",true);
        }
    inserter=std::format_to( inserter,R"(,"port":{})",(char *)back.port);
    if(host.receivedatafrom()) {
        int sendindex=host.index;
        if(sendindex>=0) {
            updateone &send=back.tosend[sendindex];
            inserter=insertbool(inserter, "nums",!send.sendnums);
            inserter=insertbool(inserter, "scans",!send.sendscans);
            inserter=insertbool(inserter, "stream",!send.sendstream);
            const bool receives=!(send.sendnums&& send.sendscans&&send.sendstream);
            inserter=insertbool(inserter, "receive",receives);
            }
        else {
            inserter=insertbool(inserter, "nums",true);
            inserter=insertbool(inserter, "scans",true);
            inserter=insertbool(inserter, "stream",true);
//            inserter=insertbool(inserter, "receive",false);
             }
         }
     else {
/*            inserter=insertbool(inserter, "nums",false);
            inserter=insertbool(inserter, "scans",false);
            inserter=insertbool(inserter, "stream",false); */

            inserter=insertbool(inserter, "receive",true);
            } 
        
        LOGGER("getActive=%d getPassive=%d\n",host.getActive(),host.getPassive());
        inserter=insertbool(inserter,"activeonly",!host.getActive()&&host.getPassive());
        inserter=insertbool(inserter,"passiveonly",host.getActive());
        if(host.haspass()) 
            inserter=std::format_to( inserter,R"(,"pass":"{}")", escape(Backup::passback(host.pass).data()));
        else {
    //       const char passw[]=R"(,"pass":NULL)";
     //       inserter=std::copy(std::begin(passw),std::end(passw)-1,inserter);
            }
        if(host.hasname) {
            inserter=std::format_to( inserter,R"(,"label":"{}")",escape(host.getname()));
           // inserter=insertbool(inserter, "testip",false);
            }
        else {
      //     const char noname[]=R"(,"label":NULL)";
       //   inserter=std::copy(std::begin(noname),std::end(noname)-1,inserter);
            inserter=insertbool(inserter, "testip",true);
            }
   const char mirrorJuggluco[]=R"(} MirrorJuggluco)";
   inserter=std::copy(std::begin(mirrorJuggluco),std::end(mirrorJuggluco)-1,inserter);
   *inserter='\0';
    LOGGERN(uit.data(),uit.size());
    LOGGER("size=%d\n",uit.size());
    return uit;
    }

extern "C" JNIEXPORT jstring JNICALL   fromjava(getbackJson)(JNIEnv *envin, jclass cl,jint pos) {
    auto jsonstr=mkbackjson(pos);
    return myNewStringUTF(envin,jsonstr.data());
    }

extern void makepass(char *pass,int len);

extern "C" JNIEXPORT jint JNICALL   fromjava(makeHomeCopy)(JNIEnv *envin, jclass cl) {
    int pos=-1;
    bool nums=true,scans=true,stream=true;
    bool activeonly=false,passiveonly=true;
    const int passlen=16;
    char passstr[passlen+1];
    makepass(passstr,passlen);
    passstr[passlen]='\0';
    uint32_t  starttime=0L;
    bool testip=true,hashostname=false;
    char label[10]="auto";
    makepass(label+4,5);
    constexpr const bool detect=true;
     jint res=backup->changehost(pos,nullptr,nullptr,0,detect,std::string_view(nullptr,0),nums,stream,scans,false,false,activeonly,std::string_view(passstr,passlen),starttime,passiveonly,label,testip,true,hashostname);
     return res;
     }
#endif
