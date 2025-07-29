#pragma once
#include <math.h>
#include "CaliPara.hpp"

inline double mkweight(double age) {
    return 2.0L/
        (1.0L + expl(2.3148148148148148L* powl(10,-6) *age));
    }
inline double calibrateValue(const CaliPara &cali ,const uint32_t time,const double value) {
        const double w=mkweight(fabs(time-(double)cali.time));
        if(w<=0) {
            return NAN;
            }
        return w*(value*cali.a+cali.b)+(1.0-w)*value;
        }
