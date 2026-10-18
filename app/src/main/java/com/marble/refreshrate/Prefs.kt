package com.marble.refreshrate

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("hz", Context.MODE_PRIVATE)

    var lastHz: Int
        get() = sp.getInt("last_hz", -1)
        set(v) = sp.edit().putInt("last_hz", v).apply()

    var applyOnBoot: Boolean
        get() = sp.getBoolean("boot", true)
        set(v) = sp.edit().putBoolean("boot", v).apply()

    var forceSf: Boolean
        get() = sp.getBoolean("force_sf", false)
        set(v) = sp.edit().putBoolean("force_sf", v).apply()
}
