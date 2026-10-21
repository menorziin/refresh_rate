package com.refreshrate.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** De onde vem o privilégio para rodar `settings put` e afins. */
enum class Backend { ROOT, SHIZUKU }

/** Situação de acesso mostrada na tela principal. */
enum class Access { ROOT, SHIZUKU, SHIZUKU_NEEDS_PERMISSION, SHIZUKU_NOT_RUNNING, NONE }

/**
 * Executa comandos privilegiados via root (`su`) ou via Shizuku, que roda com o
 * usuário `shell` do ADB e funciona sem root. Sempre chame fora da main thread.
 */
object Shell {

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val SHIZUKU_SETUP_URL = "https://shizuku.rikka.app/guide/setup/"

    fun isShizukuInstalled(ctx: Context): Boolean = runCatching {
        ctx.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0); true
    }.getOrDefault(false)

    fun shizukuRunning(): Boolean =
        runCatching { Shizuku.pingBinder() && !Shizuku.isPreV11() }.getOrDefault(false)

    fun shizukuGranted(): Boolean = shizukuRunning() &&
        runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)

    /** O binder do Shizuku chega de forma assíncrona logo depois que o processo do app inicia. */
    fun awaitShizuku(timeoutMs: Long): Boolean {
        if (shizukuRunning()) return true
        val latch = CountDownLatch(1)
        val listener = Shizuku.OnBinderReceivedListener { latch.countDown() }
        Shizuku.addBinderReceivedListenerSticky(listener, Handler(Looper.getMainLooper()))
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        Shizuku.removeBinderReceivedListener(listener)
        return shizukuRunning()
    }

    fun rootAvailable(): Boolean = runCatching {
        val p = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor() == 0 && out.contains("uid=0")
    }.getOrDefault(false)

    /** Verifica o Shizuku antes do root, para não abrir o pedido de superusuário à toa. */
    fun detect(ctx: Context): Access {
        val installed = isShizukuInstalled(ctx)
        if (installed && awaitShizuku(2000) && shizukuGranted()) return Access.SHIZUKU
        if (rootAvailable()) return Access.ROOT
        return when {
            shizukuRunning() -> Access.SHIZUKU_NEEDS_PERMISSION
            installed -> Access.SHIZUKU_NOT_RUNNING
            else -> Access.NONE
        }
    }

    fun run(ctx: Context, vararg commands: String): Result<String> = runCatching {
        val prefs = Prefs(ctx)
        // Só espera o binder do Shizuku se ele tiver chance de ser usado; quem usa
        // root e não tem Shizuku não paga essa espera.
        val useShizuku = shizukuGranted() ||
            (prefs.lastBackend != Backend.ROOT && isShizukuInstalled(ctx) &&
                awaitShizuku(3000) && shizukuGranted())

        val process = if (useShizuku) shizukuProcess() else ProcessBuilder("su").start()
        process.outputStream.bufferedWriter().use { w ->
            w.write("exec 2>&1"); w.newLine()
            commands.forEach { w.write(it); w.newLine() }
            w.write("exit"); w.newLine()
        }
        val output = process.inputStream.bufferedReader().readText()
        val code = process.waitFor()
        if (code != 0) error("${if (useShizuku) "shizuku" else "su"} exit $code: $output")

        prefs.lastBackend = if (useShizuku) Backend.SHIZUKU else Backend.ROOT
        output
    }

    /**
     * Abre um `sh` com o usuário shell através do Shizuku. O método newProcess ficou
     * privado na API 13, mas continua presente; o proguard-rules.pro o preserva.
     */
    private fun shizukuProcess(): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java,
        ).apply { isAccessible = true }
        return method.invoke(null, arrayOf("sh"), null, null) as Process
    }
}
