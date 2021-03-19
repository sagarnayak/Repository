package com.sagar.repository

import com.sagar.repository.enums.NetworkSpeed
import com.sagar.repository.enums.NetworkSpeed.FAST
import com.sagar.repository.enums.NetworkSpeed.SLOW
import com.sagar.repository.models.NetworkCallTime
import kotlin.math.min

class NetworkCallTimeMaster {
    companion object {
        const val NETWORK_TIME_CALCULATION_PERIOD = 10
        const val NETWORK_TIME_CALCULATION_PERIOD_MIN = 4
        const val NETWORK_TIME_CALCULATION_EMA_SMOOTHING = 0.181818
        const val NETWORK_SPEED_THRESHOLD_MILLS = 1000L
    }

    protected val networkCallTimes: ArrayList<NetworkCallTime> = ArrayList()
    protected var networkSpeed = NetworkSpeed.CAN_NOT_DETERMINE

    fun getCurrentNetworkSpeed() = networkSpeed

    fun gotNetworkCallTime(newNetworkCallTime: NetworkCallTime) {
        newNetworkCallTime.timeTaken = newNetworkCallTime.endTime - newNetworkCallTime.startTime
        networkCallTimes.add(newNetworkCallTime)

        if (networkCallTimes.size < NETWORK_TIME_CALCULATION_PERIOD_MIN)
            return

        val averagePeriod = min(NETWORK_TIME_CALCULATION_PERIOD, networkCallTimes.size)
        val smoothingFactor = NETWORK_TIME_CALCULATION_EMA_SMOOTHING

        networkCallTimes.forEachIndexed { index, networkCallTime ->
            if (index >= (averagePeriod - 1)) {
                var calculatedSMA = 0.0
                networkCallTimes.subList(
                    (index + 1) - averagePeriod,
                    index
                ).forEach {
                    calculatedSMA += it.timeTaken
                }

                networkCallTime.sma = calculatedSMA / averagePeriod

                if (index == (averagePeriod - 1)) {
                    networkCallTime.ema = calculatedSMA
                } else {
                    networkCallTime.ema =
                        (
                                (
                                        networkCallTime.timeTaken -
                                                networkCallTimes[index - 1].ema
                                        ) *
                                        smoothingFactor
                                ) +
                                networkCallTimes[index - 1].ema
                }
            }
        }

        networkSpeed =
            if (networkCallTimes[networkCallTimes.size - 1].ema > NETWORK_SPEED_THRESHOLD_MILLS)
                SLOW
            else
                FAST
    }
}