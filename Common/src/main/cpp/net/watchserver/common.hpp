#pragma once
#include "SensorGlucoseData.hpp"
#include "gltype.hpp"
template <typename T>
inline void	addstrview(char *&uitptr,const T indata) {
	memcpy(uitptr,indata.data(),indata.size());
	uitptr+=indata.size();
	}

template <class T, size_t N>
inline static constexpr void addar(char *&uitptr,const T (&array)[N]) {
	constexpr const int len=N-1;
	memcpy(uitptr,array,len);
	uitptr+=len;
	}

inline double getdelta(float change) {
	static constexpr const double deltatimes=5.0;
	 return isnan(change)?0:change*deltatimes; //json has no nan. This is obviously wrong, I don't know what else to do. Return null?
	 }

int resolveExportedMgdl(const SensorGlucoseData *sens, const ScanData *val,
                        const sensorname_t *sensorname);
const ScanData *makeExportedScan(const SensorGlucoseData *sens,
                                 const ScanData *val,
                                 const sensorname_t *sensorname,
                                 ScanData &storage);
