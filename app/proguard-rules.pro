# R8 para PillMate.
# Room, Glance, Compose, kotlinx-serialization y zip4j traen sus propias reglas (consumer rules)
# dentro de la librería: aquí solo va lo específico de la app.

# Sin ofuscar: F-Droid compila el APK en sus servidores y no publica el mapping.txt, así que un
# stack trace ofuscado que mande un usuario sería ilegible. R8 sigue quitando código sin usar.
-dontobfuscate

# Números de línea en los stack traces.
-keepattributes SourceFile,LineNumberTable
