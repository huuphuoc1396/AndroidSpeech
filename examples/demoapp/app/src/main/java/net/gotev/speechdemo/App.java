package net.gotev.speechdemo;

import android.app.Application;

import net.gotev.speech.logger.Logger;
import net.gotev.speech.Speech;

/**
 * @author Aleksandar Gotev
 */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        Speech.init(this, getPackageName());
        Logger.setLogLevel(Logger.LogLevel.DEBUG);
    }
}
