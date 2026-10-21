# Shell.kt chama Shizuku.newProcess por reflexão; impede o R8 de remover ou renomear.
-keep class rikka.shizuku.Shizuku { *; }
-keep class rikka.shizuku.ShizukuRemoteProcess { *; }
