package com.refreshrate.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // A sobreposição não depende de root nem de Shizuku.
        OverlayService.sync(context)

        val prefs = Prefs(context)
        val preset = prefs.last ?: return
        if (!prefs.applyOnBoot) return

        val pending = goAsync()
        Thread {
            // Algumas ROMs sobrescrevem a taxa logo após o boot; esperar um pouco evita isso.
            Thread.sleep(15_000)
            RefreshRate.apply(context, preset, prefs.forceSf)
            pending.finish()
        }.start()
    }
}
