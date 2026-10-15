# The Aether core is reached through dlopen + its stable C FFI, so there is
# nothing to keep alive here. Keep Kotlin reflection out of the shrinker path
# by leaving R8 off entirely; the binary is native-heavy anyway.
