package com.marble.refreshrate

/** Executa comandos como root via `su`. Sempre chame fora da main thread. */
object Root {

    fun run(vararg commands: String): Result<String> = runCatching {
        val process = ProcessBuilder("su").redirectErrorStream(true).start()
        process.outputStream.bufferedWriter().use { w ->
            commands.forEach { w.write(it); w.newLine() }
            w.write("exit"); w.newLine()
        }
        val output = process.inputStream.bufferedReader().readText()
        val code = process.waitFor()
        if (code != 0) error("su terminou com código $code: $output")
        output
    }

    fun isAvailable(): Boolean = run("id").getOrNull()?.contains("uid=0") == true
}
