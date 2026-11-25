package com.veil.browser.performance

import android.content.Context
import android.os.BatteryManager

data class BatteryImpactSnapshot(
    val geckoTabsMw: Int,
    val torDaemonMw: Int,
    val mediaAndServicesMw: Int,
    val totalMw: Int,
    val withinPf9Budget: Boolean
)

class BatteryImpactMonitor(private val context: Context) {

    companion object {
        const val PF9_BUDGET_MW = 420
    }

    fun sampleInstantCurrentMicroAmps(): Int {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
    }

    fun estimateSessionPower(
        activeBackgroundTabs: Int,
        suspendedTabs: Int,
        torConnected: Boolean,
        torPrewarm: Boolean,
        bridgeOverheadMw: Int,
        liteMode: Boolean,
        backgroundWorkLimited: Boolean
    ): BatteryImpactSnapshot {
        val foregroundMw = if (liteMode) 105 else 145
        val backgroundMw = activeBackgroundTabs * 48 + suspendedTabs * 4
        val geckoTabsMw = foregroundMw + backgroundMw

        val torDaemonMw = when {
            torConnected -> 135 + bridgeOverheadMw
            torPrewarm -> 22
            else -> 0
        }

        val servicesMw = if (backgroundWorkLimited) 14 else 44
        val total = geckoTabsMw + torDaemonMw + servicesMw

        return BatteryImpactSnapshot(
            geckoTabsMw = geckoTabsMw,
            torDaemonMw = torDaemonMw,
            mediaAndServicesMw = servicesMw,
            totalMw = total,
            withinPf9Budget = total <= PF9_BUDGET_MW
        )
    }
}
