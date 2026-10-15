package studio.cluvex.aether

import android.app.Application
import studio.cluvex.aether.data.SettingsStore

class AetherApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        SettingsStore.load(this)
    }

    companion object {
        @Volatile
        lateinit var instance: AetherApp
            private set
    }
}
