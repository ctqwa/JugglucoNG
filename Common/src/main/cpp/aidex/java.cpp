#include "SensorGlucoseData.hpp"
#include "fromjava.h"
#include "glucose.hpp"
#include "jniclass.hpp"
#include "logs.hpp"
#include "streamdata.hpp"
#include <cmath>
#include <jni.h>

extern jlong glucoseback(uint32_t nu, uint32_t glval, float drate,
                         SensorGlucoseData *hist);
extern void wakewithcurrent();

extern "C" JNIEXPORT jlong JNICALL fromjava(aidexProcessData)(
    JNIEnv *env, jclass cl, jlong dataptr, jbyteArray value, jlong mmsec,
    jfloat glucose, jfloat rawGlucose, jfloat calibrationFactor) {
  if (!value) {
    LOGAR("aidexProcessData value==null");
    return 1LL;
  }

  aidexstream *sdata = reinterpret_cast<aidexstream *>(dataptr);
  if (!sdata) {
    LOGAR("aidexProcessData sdata==null");
    return 1LL;
  }
  SensorGlucoseData *sens = sdata->hist;
  if (!sens) {
    LOGAR("aidexProcessData SensorGlucoseData==null");
    return 1LL;
  }

  const uint32_t timsec = mmsec / 1000L;
  const CritAr data(env, value);
  const uint8_t *decrypted = reinterpret_cast<const uint8_t *>(data.data());

  // Use the glucose value passed from Kotlin directly.
  // This ensures consistency between the UI/Notification and the background
  // sync.
  int internalVal = (glucose > 0) ? (int)std::round(glucose) : 0;
  // Raw values stored in rawpolls are expected to be in mmol/L * 10.
  // getGlucoseHistory() converts rawpolls to mg/dL * 10 by multiplying 18.0182.
  int rawVal = 0;
  if (rawGlucose > 0) {
    constexpr float mgdlToMmol = 1.0f / 18.0182f;
    rawVal = (int)std::round(rawGlucose * mgdlToMmol * 10.0f);
  }

  // Trend/Rate (AiDex doesn't have a clear rate byte yet, using NAN)
  float change = NAN;

#ifndef NOLOG
  time_t tim = timsec;
  LOGGER("aidexProcessData glucose=%d %s", internalVal, ctime(&tim));
#endif

  // Calculate proper ID to advance history
  int id = sens->getinfo()->pollcount;
  if (id > 0) {
    uint32_t start = sens->getinfo()->starttime;
    id = (timsec - start + 30) / 60;
  }

  // Unified persistence: store in both stream and history
  // Using 60 seconds interval for AiDex
  sens->savepollallIDs<60>(timsec, id, internalVal, 0, change, rawVal);

  // Trigger UI and Notification sync
  jlong res = glucoseback(timsec, internalVal, change, sens);
  wakewithcurrent();

  return res;
}

extern "C" JNIEXPORT void JNICALL fromjava(aidexSetStartTime)(JNIEnv *env,
                                                              jclass cl,
                                                              jlong dataptr,
                                                              jlong timeMs) {
  aidexstream *sdata = reinterpret_cast<aidexstream *>(dataptr);
  if (sdata && sdata->hist) {
    auto *info = sdata->hist->getinfo();
    info->starttime = timeMs / 1000L;
    if (info->days < 10 || info->days > maxdays) {
      info->days = 15;
    }
    if (!info->wearduration2) {
      info->wearduration2 = static_cast<uint16_t>(info->days * 24 * 60);
    }
    LOGGER("aidexSetStartTime: %ld -> starttime=%u days=%u wear=%u\n", timeMs,
           info->starttime, info->days, info->wearduration2);
  }
}

extern "C" JNIEXPORT void JNICALL fromjava(aidexSetWearDays)(JNIEnv *env,
                                                             jclass cl,
                                                             jlong dataptr,
                                                             jint days) {
  if (days < 10 || days > maxdays) {
    return;
  }
  aidexstream *sdata = reinterpret_cast<aidexstream *>(dataptr);
  if (sdata && sdata->hist) {
    auto *info = sdata->hist->getinfo();
    info->days = static_cast<uint8_t>(days);
    info->wearduration2 = static_cast<uint16_t>(days * 24 * 60);
    LOGGER("aidexSetWearDays: days=%d wear=%u\n", days, info->wearduration2);
  }
}
