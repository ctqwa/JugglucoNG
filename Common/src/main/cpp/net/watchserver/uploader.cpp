#if 1
//#ifndef WEAROS
#include <jni.h>
#include <algorithm>
#include <atomic>
#include <cctype>
#include <ctime>
#include <memory>
#include <string>
#include <string_view>
#include <vector>
#include "settings/settings.hpp"
#include "share/logs.hpp"
#include "sensoren.hpp"
#include "nums/numdata.hpp"
#include "common.hpp"
extern Settings *settings;
extern Sensoren *sensors;
constexpr int HTTP_OK=200;
constexpr int HTTP_CREATED=201;
constexpr int HTTP_CONFLICT=409;
/*[
  {
    "type": "string",
    "dateString": "string",
    "date": 0,
    "sgv": 0,
    "direction": "string",
    "noise": 0,
    "filtered": 0,
    "unfiltered": 0,
    "rssi": 0
  }
]

[{"device":"xDrip-LibreReceiver","date":1678294924000,"dateString":"2023-03-08T18:02:04.000+0100","sgv":63,"delta":-1.993,"direction":"Flat","type":"sgv","filtered":63000,"unfiltered":63000,"rssi":100,"noise":1}]1
*/
extern JNIEnv *getenv();

jclass nightpostclass=nullptr;
jstring jnightuploadEntriesurl=nullptr;
jstring jnightuploadEntries3url=nullptr;
jstring jnightuploadTreatmentsurl=nullptr;
jstring jnightuploadTreatments3url=nullptr;
jstring jnightuploadsecret= nullptr;
jclass nightscoutcalibrationclass=nullptr;
static int lastNightUploadCode = 0;
static std::atomic<int> lastNightUploadResponseCode{0};
static std::atomic<long long> lastNightUploadAttemptTime{0};
static std::atomic<long long> lastNightUploadSuccessTime{0};
static std::atomic<int> lastNightUploadWaitMinutes{0};
static bool lastNightUploadConfigError = false;
struct NightUploadRange {
    uint32_t start{};
    uint32_t end{};
    };
static constexpr int maxRecentNightUploadSensors = 1024;
static NightUploadRange recentNightUploads[maxRecentNightUploadSensors]{};
/*
void makeuploadurl(JNIEnv *env) {
        const int namelen=settings->data()->nightuploadnamelen;
        const char *name=settings->data()->nightuploadname;
        static constexpr const char lasturl[]=R"(/api/v1/entries)";
        char fullname[namelen+sizeof(lasturl)];
        memcpy(fullname,name,namelen);
        memcpy(fullname+namelen,lasturl,sizeof(lasturl));
        if(jnightuploadEntriesurl)
            env->DeleteGlobalRef(jnightuploadEntriesurl);
        auto local=env->NewStringUTF(fullname);
        jnightuploadEntriesurl=  (jstring)env->NewGlobalRef(local);
        env->DeleteLocalRef(local);
        } */
static bool hasScheme(std::string_view text) {
    return text.rfind("http://", 0) == 0 || text.rfind("https://", 0) == 0;
    }

static int normalizeNightscoutBaseUrl(char *dest, int destsize, const char *src, int srclen) {
    if(!dest || destsize <= 0) {
        return 0;
        }
    if(!src || srclen <= 0) {
        dest[0]='\0';
        return 0;
        }
    const char *start=src;
    const char *end=src+srclen;
    while(start<end && isspace(static_cast<unsigned char>(*start)))
        ++start;
    while(end>start && isspace(static_cast<unsigned char>(end[-1])))
        --end;
    std::string normalized(start,end-start);
    if(normalized.empty()) {
        dest[0]='\0';
        return 0;
        }
    if(!hasScheme(normalized)) {
        normalized.insert(0,"https://");
        }
    while(!normalized.empty() && normalized.back()=='/')
        normalized.pop_back();
    const auto entriespos=normalized.find("/api/");
    if(entriespos!=std::string_view::npos)
        normalized.resize(entriespos);
    int len=std::min<int>(normalized.size(),destsize-1);
    memcpy(dest,normalized.data(),len);
    dest[len]='\0';
    return len;
    }

static bool makeuploadurl(JNIEnv *env,std::string_view pathstr,jstring &url) {

        const int namelen=settings->data()->nightuploadnamelen;
        const char *name=settings->data()->nightuploadname;
        if(namelen<=0 || !hasScheme(std::string_view(name,namelen))) {
            if(url) {
                env->DeleteGlobalRef(url);
                url=nullptr;
                }
            return false;
            }
        char fullname[namelen+pathstr.size()+1];
        memcpy(fullname,name,namelen);
        memcpy(fullname+namelen,pathstr.data(),pathstr.size()+1);
        auto local=env->NewStringUTF(fullname);

        if(url)
            env->DeleteGlobalRef(url);
        url=  (jstring)env->NewGlobalRef(local);
        env->DeleteLocalRef(local);
        return true;
        }
static bool makeuploadurls(JNIEnv *env) {
    const bool entriesv1=makeuploadurl(env,R"(/api/v1/entries)",jnightuploadEntriesurl);
    const bool entriesv3=makeuploadurl(env,R"(/api/v3/entries)",jnightuploadEntries3url);
    const bool treatmentsv1=makeuploadurl(env,R"(/api/v1/treatments)",jnightuploadTreatmentsurl);
    const bool treatmentsv3=makeuploadurl(env,R"(/api/v3/treatments)",jnightuploadTreatments3url);
    return entriesv1&&entriesv3&&treatmentsv1&&treatmentsv3;
    }

static bool ensureNightscoutBaseUrl() {
    char normalized[sizeof(settings->data()->nightuploadname)]{};
    const int len=normalizeNightscoutBaseUrl(
        normalized,
        sizeof(normalized),
        settings->data()->nightuploadname,
        settings->data()->nightuploadnamelen
    );
    if(len<=0)
        return false;
    if(len!=settings->data()->nightuploadnamelen || memcmp(normalized,settings->data()->nightuploadname,len)!=0) {
        memcpy(settings->data()->nightuploadname,normalized,len+1);
        settings->data()->nightuploadnamelen=len;
        settings->updated();
        }
    return true;
    }

extern std::string sha1encode(const char *secret, int len);
static void makeuploadsecret(JNIEnv *env) {
        const bool useV3=settings->data()->nightscoutV3;
        if(useV3) {
            jnightuploadsecret=nullptr;
            }
        else  {
            if(jnightuploadsecret)
                env->DeleteGlobalRef(jnightuploadsecret);

            const char *secret=settings->data()->nightuploadsecret;
            std::string encoded=sha1encode(secret,strlen(secret));
            auto local=env->NewStringUTF(encoded.data());
            jnightuploadsecret=  (jstring)env->NewGlobalRef(local);
            }
        }
bool inituploader(JNIEnv *env) {
    if(!settings->data()->nightuploadon)  
        return false;
    if(!ensureNightscoutBaseUrl()) {
        lastNightUploadCode = -2;
        lastNightUploadResponseCode = -2;
        lastNightUploadConfigError = true;
        return false;
        }
    if(nightpostclass==nullptr) {
        constexpr const char nightpostclassstr[]="tk/glucodata/NightPost";
        if(jclass cl=env->FindClass(nightpostclassstr)) {
            LOGGER("found %s\n",nightpostclassstr);
            nightpostclass=(jclass)env->NewGlobalRef(cl);
               env->DeleteLocalRef(cl);
               }
            else  {
            LOGGER("FindClass(%s) failed\n",nightpostclassstr);
            return false;
            }
        }
    if(nightscoutcalibrationclass==nullptr) {
        constexpr const char calibrationclassstr[]="tk/glucodata/NightscoutCalibration";
        if(jclass cl=env->FindClass(calibrationclassstr)) {
            nightscoutcalibrationclass=(jclass)env->NewGlobalRef(cl);
            env->DeleteLocalRef(cl);
            }
        }
    makeuploadsecret(env); 
    if(!makeuploadurls(env)) {
        lastNightUploadCode = -2;
        lastNightUploadResponseCode = -2;
        lastNightUploadConfigError = true;
        LOGSTRING("Nightscout uploader disabled until URL is valid\n");
        return false;
        }
    extern void startuploaderthread();
    startuploaderthread();
    LOGAR("end inituploader");
    return true;
       }

static int getNightscoutCalibrationOverrideForItem(SensorGlucoseData *sens,const char *sensorname,int autoMgdl,int rawCurrent,long long timestampMillis) {
    if(nightscoutcalibrationclass==nullptr)
        return 0;
    auto env=getenv();
    if(env==nullptr)
        return 0;
    const static jmethodID nightscoutCalibrationOverride = env->GetStaticMethodID(
        nightscoutcalibrationclass,
        "getNightscoutCalibrationOverride",
        "(Ljava/lang/String;IIIJ)I"
    );
    if(nightscoutCalibrationOverride==nullptr)
        return 0;
    auto jsensor=env->NewStringUTF(sensorname);
    const auto *info=sens->getinfo();
    const int viewMode=info?info->viewMode:0;
    const int overrideValue=env->CallStaticIntMethod(
        nightscoutcalibrationclass,
        nightscoutCalibrationOverride,
        jsensor,
        viewMode,
        autoMgdl,
        rawCurrent,
        timestampMillis
    );
    env->DeleteLocalRef(jsensor);
    return overrideValue;
    }

static bool isNightUploadSuccess(const int res) {
    return res==HTTP_OK || res==HTTP_CREATED;
    }

static bool isNightUploadAccepted(const int res) {
    return isNightUploadSuccess(res) || res==HTTP_CONFLICT;
    }

static NightUploadRange *getRecentNightUploadRange(const int sensorid,const bool ensure=false) {
    if(sensorid<0 || sensorid>=maxRecentNightUploadSensors)
        return nullptr;
    auto *range=&recentNightUploads[sensorid];
    if(!ensure && !range->start && !range->end)
        return nullptr;
    return range;
    }

static void rememberRecentNightUpload(const int sensorid,const uint32_t start,const uint32_t end) {
    if(!start || !end)
        return;
    auto *range=getRecentNightUploadRange(sensorid,true);
    if(!range)
        return;
    if(!range->start || start<range->start)
        range->start=start;
    if(end>range->end)
        range->end=end;
    }

static void pruneRecentNightUpload(const int sensorid,const uint32_t tim) {
    auto *range=getRecentNightUploadRange(sensorid);
    if(!range)
        return;
    if(range->end && range->end+600<tim) {
        *range={};
        }
    }

static bool isRecentNightUploadCovered(const int sensorid,const uint32_t tim) {
    const auto *range=getRecentNightUploadRange(sensorid);
    if(!range)
        return false;
    return range->start && tim>=range->start && tim<=range->end;
    }


//static boolean upload(String httpurl,byte[] postdata,String secret) ;
extern vector<Numdata*> numdatas;
static void reset() {
    const int last=sensors->last();
    settings->data()->nightsensor=0;
    settings->data()->lastuploadtime=0;
    for(auto &range:recentNightUploads)
        range={};
    for(int sensorid=0;sensorid<=last;sensorid++) {
        if(SensorGlucoseData *sens=sensors->getSensorData(sensorid)) {
            sens->getinfo()->nightiter=0;
            }
        }
    for(auto *numdata:numdatas)
        numdata->setNightSend(0);
    }
static int nightupload(jstring jnightuploadurl,const char *data,int len,bool put) {
    if(jnightuploadurl==nullptr) {
        lastNightUploadCode = -2;
        lastNightUploadResponseCode = -2;
        lastNightUploadConfigError = true;
        return -2;
        }
    const static jmethodID  upload=getenv()->GetStaticMethodID(nightpostclass,"upload","(Ljava/lang/String;[BLjava/lang/String;Z)I");
    auto env=getenv();
    jbyteArray uit=env->NewByteArray(len);
        env->SetByteArrayRegion(uit, 0, len,(const jbyte *)data);
    lastNightUploadAttemptTime = static_cast<long long>(time(nullptr));
    int res=env->CallStaticIntMethod(nightpostclass,upload,jnightuploadurl,uit,jnightuploadsecret,put);
    lastNightUploadCode = res;
    lastNightUploadResponseCode = res;
    lastNightUploadConfigError = (res==-2);
    if(res==HTTP_OK || res==201) {
        lastNightUploadSuccessTime = static_cast<long long>(time(nullptr));
        lastNightUploadConfigError = false;
    }
    LOGGER("nightupload=%d\n",res);
    env->DeleteLocalRef(uit);
    return res;
    }
int nightuploadEntries(const char *data,int len) {
    return nightupload(jnightuploadEntriesurl,data,len,false);
    }
int nightuploadEntries3(const char *data,int len) {
    return nightupload(jnightuploadEntries3url,data,len,false);
    }

int nightuploadTreatments(const char *data,int len) {
    return nightupload(jnightuploadTreatmentsurl,data,len,true);
    }
int nightuploadTreatments3(const char *data,int len) {
    return nightupload(jnightuploadTreatments3url,data,len,false);
    }


extern double     calibrateONEtest(const SensorGlucoseData *sens,const ScanData &value);
//extern int Tdatestring(time_t tim,char *buf) ;
#include "datestring.hpp"
extern double getdelta(float change);
extern std::string_view getdeltaname(float change);
extern int mkv1streamid(char *outiter,const sensorname_t *name,int num);
template <class T> int mkuploaditem(SensorGlucoseData *sens,char *buf,const sensorname_t *sensorname,const T &item,const bool includeId=false,const bool trailingComma=true) {
    const time_t tim=item.gettime();
    const char *sensornameStr=sensorname->data();
    int mgdL;
    const int rawCurrent=sens->getRawForPoll(&item);
    const int overrideValue=getNightscoutCalibrationOverrideForItem(sens,sensornameStr,item.getmgdL(),rawCurrent,tim*1000LL);
    if(overrideValue>0) {
        mgdL=overrideValue;
         }
    else if(double calibrated=calibrateONEtest(sens,item);!isnan(calibrated)) {
        mgdL=(int)round(calibrated);
         }
    else
        mgdL=item.getmgdL();
    float change= item.getchange();
    const char * directionlabel=getdeltaname(change).data();
    double delta=getdelta(change);
    char timestr[50];
    Tdatestring(tim,timestr);
    char *out=buf;
    out+=sprintf(out,R"({"type":"sgv","device":"%s","dateString":"%s","date":%lld,"sgv":%d,"delta":%.3f,"direction":"%s","noise":1,"filtered":%d,"unfiltered":%d,"rssi":100)",sensornameStr,timestr,tim*1000LL,mgdL,delta,directionlabel,mgdL*1000,mgdL*1000);
    if(includeId) {
        addar(out,R"(,"_id":")");
        out+=mkv1streamid(out,sensorname,item.getid());
        addar(out,R"(")");
        }
    if(trailingComma)
        addar(out,R"(},)");
    else
        addar(out,R"(})");
    return out-buf;
    }
/*
template <class T> int mkuploaditemv3(SensorGlucoseData *sens,char *buf,const char *sensorname,const T &item) {
    const time_t tim=item.gettime();
    if(double calibrated=calibrateONEtest(sens,*item),!isnan(calibrated)) {
        mgdL=(int)round(calibrated);
         }
    else
        mgdL=item.getmgdL();
    float change= item.getchange();
    const char * directionlabel=getdeltaname(change).data();
    double delta=getdelta(change);
    char timestr[50];
    Tdatestring(tim,timestr);

    return sprintf(buf,R"({"type":"sgv","device":"%s","dateString":"%s","date":%lld,"sgv":%d,"delta":%.3f,"direction":"%s","noise":1,"filtered":%d,"unfiltered":%d,"rssi":100},)",sensorname,timestr,tim*1000LL,mgdL,delta,directionlabel,mgdL*1000,mgdL*1000);

    }
*/
extern  const int nighttimeback;
const int nighttimeback=60*60*24*30;

static bool uploadRecentV1(const int sensorid,SensorGlucoseData *sens,const sensorname_t *sensorname,std::span<const ScanData> gdata,const uint32_t mintime) {
    uint32_t newest=0;
    uint32_t oldest=0;
    for(int iter=gdata.size()-1;iter>=0;--iter) {
        const ScanData &el=gdata[iter];
        if(!el.valid(iter) || el.gettime()<=mintime)
            continue;
        pruneRecentNightUpload(sensorid,el.gettime());
        if(isRecentNightUploadCovered(sensorid,el.gettime()))
            return true;
        char buf[512];
        char *ptr=buf;
        *ptr++='[';
        ptr+=mkuploaditem(sens,ptr,sensorname,el,true,false);
        *ptr++=']';
        *ptr='\0';
        const int res=nightuploadEntries(buf,ptr-buf);
        if(!isNightUploadAccepted(res))
            return false;
        newest=el.gettime();
        oldest=el.gettime();
        break;
        }
    if(oldest && newest)
        rememberRecentNightUpload(sensorid,oldest,newest);
    return true;
    }

static bool uploadV1ChunkIndividually(const int sensorid,SensorGlucoseData *sens,const sensorname_t *sensorname,std::span<const ScanData> gdata,const int startiter,const int chunkend,const uint32_t mintime) {
    for(int iter=startiter;iter<chunkend;iter++) {
        const ScanData &el=gdata[iter];
        if(!el.valid(iter) || el.gettime()<=mintime)
            continue;
        pruneRecentNightUpload(sensorid,el.gettime());
        if(isRecentNightUploadCovered(sensorid,el.gettime()))
            continue;
        char buf[512];
        char *ptr=buf;
        *ptr++='[';
        ptr+=mkuploaditem(sens,ptr,sensorname,el,true,false);
        *ptr++=']';
        *ptr='\0';
        const int res=nightuploadEntries(buf,ptr-buf);
        if(!isNightUploadAccepted(res))
            return false;
        }
    return true;
    }

static const char *writeNightscoutV3UploadEntry(char *buf,SensorGlucoseData *sens,const sensorname_t *sensorname,const ScanData *el) {
extern char * writev3entry(char *outin,const ScanData *val, const sensorname_t *sensorname,bool server=true);
    const int overrideValue=getNightscoutCalibrationOverrideForItem(
        sens,
        sensorname->data(),
        el->getmgdL(),
        sens->getRawForPoll(el),
        el->gettime()*1000LL
    );
    if(overrideValue>0) {
        ScanData newel=*el;
        newel.g=overrideValue;
        return writev3entry(buf,&newel,sensorname,false);
        }
    if(double calibrated=calibrateONEtest(sens,*el);!isnan(calibrated)) {
        ScanData newel=*el;
        newel.g=(int32_t)round(calibrated);
        return writev3entry(buf,&newel,sensorname,false);
        }
    return writev3entry(buf,el,sensorname,false);
    }

static bool uploadRecentV3(const int sensorid,SensorGlucoseData *sens,const sensorname_t *sensorname,std::span<const ScanData> gdata,const uint32_t mintime) {
    uint32_t newest=0;
    uint32_t oldest=0;
    for(int iter=gdata.size()-1;iter>=0;--iter) {
        const ScanData &el=gdata[iter];
        if(!el.valid(iter) || el.gettime()<=mintime)
            continue;
        pruneRecentNightUpload(sensorid,el.gettime());
        if(isRecentNightUploadCovered(sensorid,el.gettime()))
            return true;
        char buf[320];
        const char *ptr=writeNightscoutV3UploadEntry(buf,sens,sensorname,&el);
        const int res=nightuploadEntries3(buf,ptr-buf);
        if(!isNightUploadAccepted(res))
            return false;
        newest=el.gettime();
        oldest=el.gettime();
        break;
        }
    if(oldest && newest)
        rememberRecentNightUpload(sensorid,oldest,newest);
    return true;
    }

static bool uploadCGM3(const bool prioritizeRecent=false) {
    LOGSTRING("upload\n");
    int last=sensors->last();
    if(last<0) {
        LOGAR("No sensors");
        return true;
        }
    time_t nu=time(nullptr);
    uint32_t mintime=nu-nighttimeback;
    if(!settings->data()->nightsensor)
        settings->data()->nightsensor=sensors->firstafter(mintime);
    int startsensor= settings->data()->nightsensor;

/*    constexpr const auto twoweeks=15*24*60*60;
    time_t old=nu-twoweeks; */

    int newstartsensor=startsensor;
    for(int sensorid=last;sensorid>=startsensor;--sensorid) {
        if(SensorGlucoseData *sens=sensors->getSensorData(sensorid)) {
            std::span<const ScanData> gdata=sens->getPolldata();
            const sensorname_t *sensorname=sens->shortsensorname();
            int len=gdata.size();
            int positer=sens->getinfo()->nightiter;
            LOGGER("%d: positer=%d\n",sensorid,positer);
            int left=len-positer;
            bool send=false;
            if(left>=0) {
                if(prioritizeRecent && left>1) {
                    if(!uploadRecentV3(sensorid,sens,sensorname,gdata,mintime)) {
                        settings->data()->nightsensor=newstartsensor;
                        return false;
                        }
                    }
                for(;positer<len;positer++) { //Geen overlappende data?
                    const ScanData *el= &gdata[positer];
                    if(el->valid(positer)&&el->gettime()>mintime) {
                        pruneRecentNightUpload(sensorid,el->gettime());
                        if(isRecentNightUploadCovered(sensorid,el->gettime())) {
                            sens->getinfo()->nightiter=positer+1;
                            continue;
                            }
                        constexpr const int max3entry=320;
                        char buf[max3entry];
                        const char *ptr=writeNightscoutV3UploadEntry(buf,sens,sensorname,el);
                        const int buflen=ptr-buf;
                        logwriter(buf,buflen);
                        auto res=nightuploadEntries3(buf,buflen);
                        if(isNightUploadAccepted(res)) {
                            sens->getinfo()->nightiter=positer+1;
                            send=true;
                            }
                        else {
                            LOGSTRING("nightupload failure\n");
                            if(send)
                                settings->data()->nightsensor=sensorid;
                            else
                                settings->data()->nightsensor=newstartsensor;
                            return false;
                            }
                        }
                    }
                if(send)
                    newstartsensor=sensorid;
                continue;
                }
//            if(sens->getstarttime()>old) 
            if(sens->getmaxtime()>nu) 
                newstartsensor=sensorid;

            }
        }
    settings->data()->nightsensor=newstartsensor;
    return true;
    }
static bool uploadCGM(const bool prioritizeRecent=false) {
    LOGSTRING("upload\n");
    int last=sensors->last();
    if(last<0) {
        LOGAR("No sensors");
        return true;
        }
    time_t nu=time(nullptr);
    uint32_t mintime=nu-nighttimeback;
    if(!settings->data()->nightsensor)
        settings->data()->nightsensor=sensors->firstafter(mintime);
    int startsensor= settings->data()->nightsensor;
    constexpr const int itemsize=420;
/*
    constexpr const auto twoweeks=15*24*60*60;
    time_t old=nu-twoweeks; */

    int newstartsensor=startsensor;
    constexpr const int maxuploaditems=120;
    for(int sensorid=last;sensorid>=startsensor;--sensorid) {
        if(SensorGlucoseData *sens=sensors->getSensorData(sensorid)) {
            std::span<const ScanData> gdata=sens->getPolldata();
            const sensorname_t *sensorname=sens->shortsensorname();
            const int totallen=gdata.size();
            int positer=sens->getinfo()->nightiter;
            LOGGER("%d: positer=%d\n",sensorid,positer);
            int left=totallen-positer;
            if(left>=0) {
                bool sent=false;
                if(prioritizeRecent && left>1) {
                    if(!uploadRecentV1(sensorid,sens,sensorname,gdata,mintime)) {
                        return false;
                        }
                    }
                while(positer<totallen) {
                    const int chunklimit=positer<120?40:maxuploaditems;
                    const int chunkend=std::min(totallen,positer+chunklimit);
                    const int chunkitems=chunkend-positer;
                    const int arraysize=3+chunkitems*itemsize;
                    LOGGER("arraysize=%d\n",arraysize);
                    char  *start=new(std::nothrow)  char[arraysize];
                    if(!start) {
                        LOGGER("new char[%d] failed\n",arraysize);
                        return false;
                        }
                    unique_ptr<char[]> destruct(start);
                    char *ptr=start;
                    *ptr++='[';
                    for(int iter=positer;iter<chunkend;iter++) { //Geen overlappende data?
                        const ScanData &el= gdata[iter];
                        if(el.valid(iter)&&el.gettime()>mintime) {
                            pruneRecentNightUpload(sensorid,el.gettime());
                            if(isRecentNightUploadCovered(sensorid,el.gettime())) {
                                continue;
                                }
                            ptr+=mkuploaditem(sens,ptr,sensorname,el,false,true);
                            }
                        }
                    LOGGER("%d new positer=%d\n",sensorid,chunkend);
                    if(ptr>(start+1)) {
                        ptr[-1]=']';
                        *ptr='\0';
                        int datalen=ptr-start;
                        LOGGER("%d: UPLOADER #%d\n",sensorid,datalen);
                        LOGGERN(start,datalen);
                        const int res = nightuploadEntries(start,datalen);
                        if(isNightUploadSuccess(res)) {
                            sens->getinfo()->nightiter=chunkend;
                            positer=chunkend;
                            LOGGER("%d nightupload Success\n",sensorid);
                            LOGGER("%d saved nightiter=%d\n", sensorid,chunkend);
                            newstartsensor=sensorid;
                            sent=true;
                            continue;
                            }
                        if(res==HTTP_CONFLICT && uploadV1ChunkIndividually(sensorid,sens,sensorname,gdata,positer,chunkend,mintime)) {
                            sens->getinfo()->nightiter=chunkend;
                            positer=chunkend;
                            LOGGER("%d nightupload conflict resolved individually\n",sensorid);
                            LOGGER("%d saved nightiter=%d\n", sensorid,chunkend);
                            newstartsensor=sensorid;
                            sent=true;
                            continue;
                            }
                        else {
                            LOGGER("nightupload failure code=%d\n",res);
                            LOGSTRING("nightupload failure\n");
                            return false;
                            }
                        }
                    else  {
                        LOGGER("ADDED nothing %d new positer=%d\n",sensorid,chunkend);
                        sens->getinfo()->nightiter=chunkend;
                        positer=chunkend;
                        }
                    }
                if(sent)
                    continue;
                }
            if(sens->getmaxtime()>nu) 
                newstartsensor=sensorid;
            }
        }
    settings->data()->nightsensor=newstartsensor;
    return true;
    }

#include "datbackup.hpp"
Backup::condvar_t  uploadercondition;
bool uploaderrunning=false;

extern bool networkpresent;

extern bool uploadtreatments(bool);
static void uploaderthread() {
    int waitmin=0;
    uploaderrunning=true;
    lastNightUploadWaitMinutes = waitmin;
    const char view[]{"UPLOADER"};
    LOGGERN(view,sizeof(view)-1);
       prctl(PR_SET_NAME, view, 0, 0, 0);

    while(true) {

          if(!networkpresent||!uploadercondition.dobackup) {
            if(!networkpresent) {
                waitmin=60;
                lastNightUploadWaitMinutes = waitmin;
                }
                std::unique_lock<std::mutex> lck(uploadercondition.backupmutex);
            LOGGER("UPLOADER before lock waitmin=%d\n",waitmin);
             auto now = std::chrono::system_clock::now();
            #ifndef NOLOG
            auto status=
            #endif
                        uploadercondition.backupcond.wait_until(lck, now + std::chrono::minutes(waitmin));
            LOGGER("UPLOADER after lock %stimeout\n",(status==std::cv_status::no_timeout)?"no-":"");
            }
        if(uploadercondition.dobackup&Backup::wakeend) {
            uploadercondition.dobackup=0;
            uploaderrunning=false;
            LOGSTRING("end uploaderthread\n");
            return;
            }
        const auto current=uploadercondition.dobackup;
        uploadercondition.dobackup=0;
        bool useV3=settings->data()->nightscoutV3;
        const bool prioritizeRecent=(current&Backup::wakestream);
        if(current&(Backup::wakestream|Backup::wakeall)) {
            bool uploaded = useV3?uploadCGM3(prioritizeRecent):uploadCGM(prioritizeRecent);
            if(!uploaded && !useV3 && lastNightUploadCode==404) {
                LOGSTRING("Nightscout v1 endpoint returned 404, retrying with v3\n");
                settings->data()->nightscoutV3 = true;
                settings->updated();
                auto env = getenv();
                makeuploadsecret(env);
                makeuploadurls(env);
                useV3 = true;
                uploaded = uploadCGM3(prioritizeRecent);
            }
            if(!uploaded) {
                waitmin=lastNightUploadConfigError?0:15;
                lastNightUploadWaitMinutes = waitmin;
                continue;
                }
            }
        if(current&(Backup::wakenums|Backup::wakeall)) {
            bool treatmentsOk = uploadtreatments(useV3);
            if(!treatmentsOk && !useV3 && lastNightUploadCode==404) {
                LOGSTRING("Nightscout v1 treatments endpoint returned 404, retrying with v3\n");
                settings->data()->nightscoutV3 = true;
                settings->updated();
                auto env = getenv();
                makeuploadsecret(env);
                makeuploadurls(env);
                useV3 = true;
                treatmentsOk = uploadtreatments(true);
            }
            if(!treatmentsOk) {
                waitmin=lastNightUploadConfigError?0:15;
                lastNightUploadWaitMinutes = waitmin;
                continue;
                }
            }
        waitmin=5*60;
        lastNightUploadWaitMinutes = waitmin;
        }
    }

void startuploaderthread();
void wakeuploader() {
    if(!uploaderrunning && settings->data()->nightuploadon) {
        auto env=getenv();
        if(env && inituploader(env)) {
            lastNightUploadConfigError = false;
            }
    }
    if(uploaderrunning) {
        lastNightUploadWaitMinutes = 0;
        uploadercondition.wakebackup(Backup::wakeall);
    }
    }
void wakestreamuploader() {
    if(uploaderrunning) 
        uploadercondition.wakebackup(Backup::wakestream);
    }
#include "fromjava.h"    
extern "C" JNIEXPORT void JNICALL fromjava(wakeuploader) (JNIEnv *env, jclass clazz) {
    wakeuploader();
    } 
extern "C" JNIEXPORT void JNICALL fromjava(resetuploader) (JNIEnv *env, jclass clazz) {
    reset();
    wakeuploader();
    } 

extern "C" JNIEXPORT jint JNICALL fromjava(getnightscoutlastresponsecode) (JNIEnv *env, jclass clazz) {
    return lastNightUploadResponseCode.load();
    }
extern "C" JNIEXPORT jlong JNICALL fromjava(getnightscoutlastattempttime) (JNIEnv *env, jclass clazz) {
    return lastNightUploadAttemptTime.load();
    }
extern "C" JNIEXPORT jlong JNICALL fromjava(getnightscoutlastsuccesstime) (JNIEnv *env, jclass clazz) {
    return lastNightUploadSuccessTime.load();
    }
extern "C" JNIEXPORT jint JNICALL fromjava(getnightscoutretryminutes) (JNIEnv *env, jclass clazz) {
    return lastNightUploadWaitMinutes.load();
    }
extern "C" JNIEXPORT jboolean JNICALL fromjava(getnightscoutuploaderrunning) (JNIEnv *env, jclass clazz) {
    return uploaderrunning;
    }

void enduploaderthread() {
    if(uploaderrunning) {
        uploadercondition.wakebackup(Backup::wakeend);
        }
    }
void startuploaderthread() {
    if(!uploaderrunning) {
        std::thread libre(uploaderthread);
        libre.detach();
        } 
    }
#endif
