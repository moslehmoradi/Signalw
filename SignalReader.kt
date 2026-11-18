package com.signalnama.widget

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellSignalStrength
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

data class SimSignalSnapshot(
    val slotIndex: Int,
    val operatorName: String,
    val networkTech: String,
    val rsrpDbm: Int,
    val asuLevel: Int,
    val level: Int, // 0..4 (or 5 mapped)
    val qualityLabel: String,
    val hasPermission: Boolean
)

object SignalReader {

    fun hasRequiredPermissions(context: Context): Boolean {
        val phoneGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        val locationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return phoneGranted && locationGranted
    }

    @SuppressLint("MissingPermission")
    fun readActiveSims(context: Context): List<SimSignalSnapshot> {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        if (!hasRequiredPermissions(context)) {
            return listOf(
                SimSignalSnapshot(
                    slotIndex = 0,
                    operatorName = "ایرانسل",
                    networkTech = "نیاز به مجوز",
                    rsrpDbm = -120,
                    asuLevel = 0,
                    level = 0,
                    qualityLabel = "مجوز صادر نشده",
                    hasPermission = false
                )
            )
        }

        val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        val activeSubs = subManager.activeSubscriptionInfoList.orEmpty()

        if (activeSubs.isEmpty()) {
            return listOf(readForTelephonyManager(telephony, 0, "ایرانسل"))
        }

        val limit = if (true) 2 else 1
        return activeSubs.take(limit).mapIndexed { idx, subInfo ->
            val slotTelephony = telephony.createForSubscriptionId(subInfo.subscriptionId)
            val fallbackName = if (idx == 0) "ایرانسل" else "همراه اول"
            val carrier = subInfo.carrierName?.toString()?.takeIf { it.isNotBlank() } ?: fallbackName
            readForTelephonyManager(slotTelephony, subInfo.simSlotIndex, carrier)
        }
    }

    @SuppressLint("MissingPermission")
    private fun readForTelephonyManager(
        tm: TelephonyManager,
        slotIndex: Int,
        carrierName: String
    ): SimSignalSnapshot {
        val networkTech = resolveNetworkType(tm.dataNetworkType)

        // 1. Try modern SignalStrength API (Android 9+)
        var bestDbm = Int.MIN_VALUE
        var bestAsu = 0
        var bestLevel = 0

        val signalStrength = tm.signalStrength
        if (signalStrength != null) {
            bestLevel = signalStrength.level // 0..4
            val strengths: List<CellSignalStrength> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                signalStrength.cellSignalStrengths
            } else {
                emptyList()
            }

            for (cs in strengths) {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cs is CellSignalStrengthNr -> {
                        if (cs.ssRsrp != Int.MAX_VALUE && cs.ssRsrp in -140..-40) {
                            bestDbm = cs.ssRsrp
                            bestAsu = cs.asuLevel
                            break
                        }
                    }
                    cs is CellSignalStrengthLte -> {
                        val rsrp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) cs.rsrp else cs.dbm
                        if (rsrp != Int.MAX_VALUE && rsrp in -140..-40) {
                            bestDbm = rsrp
                            bestAsu = cs.asuLevel
                            break
                        }
                    }
                    else -> {
                        if (cs.dbm != Int.MAX_VALUE && cs.dbm in -140..-40) {
                            bestDbm = cs.dbm
                            bestAsu = cs.asuLevel
                        }
                    }
                }
            }
        }

        // 2. Fallback to allCellInfo if needed
        if (bestDbm == Int.MIN_VALUE) {
            val cellInfos = tm.allCellInfo.orEmpty()
            for (info in cellInfos) {
                if (!info.isRegistered) continue
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info is CellInfoNr) {
                    val nr = info.cellSignalStrength as? CellSignalStrengthNr
                    if (nr != null && nr.ssRsrp in -140..-40) {
                        bestDbm = nr.ssRsrp
                        bestAsu = nr.asuLevel
                        bestLevel = nr.level
                        break
                    }
                } else if (info is CellInfoLte) {
                    val lte = info.cellSignalStrength
                    if (lte.rsrp in -140..-40) {
                        bestDbm = lte.rsrp
                        bestAsu = lte.asuLevel
                        bestLevel = lte.level
                        break
                    }
                }
            }
        }

        if (bestDbm == Int.MIN_VALUE) {
            bestDbm = -120
        }

        val quality = when {
            bestDbm >= -80 -> "عالی"
            bestDbm >= -92 -> "خوب"
            bestDbm >= -104 -> "متوسط"
            bestDbm >= -114 -> "ضعیف"
            else -> "بدون آنتن"
        }

        return SimSignalSnapshot(
            slotIndex = slotIndex,
            operatorName = carrierName,
            networkTech = networkTech,
            rsrpDbm = bestDbm,
            asuLevel = bestAsu.coerceIn(0, 97),
            level = bestLevel.coerceIn(0, 4),
            qualityLabel = quality,
            hasPermission = true
        )
    }

    @Suppress("DEPRECATION")
    private fun resolveNetworkType(type: Int): String {
        return when (type) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G / LTE"
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_HSPA -> "H+"
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_HSDPA -> "3G"
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_GPRS -> "2G / E"
            else -> "بدون دیتا"
        }
    }
}
