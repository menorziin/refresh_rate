package com.marble.refreshrate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = Prefs(context)
        val hz = prefs.lastHz
        if (!prefs.applyOnBoot || hz < 0) return

        val pending = goAsync()
        Thread {
            // A MIUI às vezes sobrescreve a taxa logo após o boot; esperar um pouco evita isso.
            Thread.sleep(15_000)
            RefreshRate.apply(context, hz, prefs.forceSf)
            pending.finish()
        }.start()
    }
}
