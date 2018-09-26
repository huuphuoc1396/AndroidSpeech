package net.gotev.speechdemo

import android.app.Application
import net.gotev.speech.Speech
import net.gotev.speech.logger.Logger

/**
 * @author Aleksandar Gotev
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Speech.init(this, packageName)
        Logger.setLogLevel(Logger.LogLevel.DEBUG)
    }
}
