package com.audioenhancer.booster

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** `exported="true"` wajib supaya BOOT_COMPLETED/MY_PACKAGE_REPLACED (broadcast
 *  sistem, protected — app lain gak bisa spoof action ini) bisa nyampe ke receiver
 *  ini. TAPI exported=true juga berarti app lain bisa kirim explicit intent ke
 *  receiver ini dengan action APAPUN (explicit intent lewati pengecekan
 *  intent-filter). Makanya action divalidasi manual di bawah — kalau bukan salah
 *  satu dari 2 action yang memang kita tunggu, diabaikan, biar app lain gak bisa
 *  paksa service kita nyala pakai action bikinan sendiri. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val serviceIntent = Intent(context, AudioEnhancerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
