#pragma once
#ifdef OLDEVERSENSE 
#include <thread>
#include "settings/settings.hpp"
#include "SensorGlucoseData.hpp"

   extern void sendEverSenseoldthread(const SensorGlucoseData *sens,int startpos,int endpos,int modulo);

inline void sendEverSenseold( SensorGlucoseData *sens,int modulo) {
	if(settings->data()->everSenseBroadcast.nr) {
	    const int	startpos=sens->getbroadcastfrom();
	    const int endpos=sens->pollcount();
	    LOGGER("broadcastfrom %d endpos=%d modulo=%d\n",startpos,endpos,modulo);
	    if(endpos>startpos) {
	       std::thread EverSensethread(sendEverSenseoldthread,sens,startpos,endpos,modulo);
	       EverSensethread.detach();
	       sens->setbroadcastfrom(INT16_MAX);
	       }
	   }
	}
#else
#define sendEverSenseold(sens, modulo) 
#endif
