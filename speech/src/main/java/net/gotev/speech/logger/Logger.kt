package net.gotev.speech.logger

import net.gotev.speech.BuildConfig

/**
 * Android Speech library logger.
 * You can provide your own logger delegate implementation, to be able to log in a different way.
 * By default the log level is set to DEBUG when the build type is debug, and OFF in release.
 * The default logger implementation logs in Android's LogCat.
 * @author huuphuoc1396
 */
class Logger private constructor() {

    private var logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.OFF

    private var delegate: LoggerDelegate = DefaultLoggerDelegate()

    enum class LogLevel {
        DEBUG,
        INFO,
        ERROR,
        OFF
    }

    interface LoggerDelegate {
        fun error(tag: String, message: String)
        fun error(tag: String, message: String, exception: Throwable)
        fun debug(tag: String, message: String)
        fun info(tag: String, message: String)
    }

    private object SingletonHolder {
        val instance = Logger()
    }

    companion object {

        @JvmStatic
        fun resetLoggerDelegate() {
            synchronized(Logger::class.java) {
                SingletonHolder.instance.delegate = DefaultLoggerDelegate()
            }
        }

        @JvmStatic
        fun setLoggerDelegate(delegate: LoggerDelegate?) {
            if (delegate == null)
                throw IllegalArgumentException("delegate MUST not be null!")

            synchronized(Logger::class.java) {
                SingletonHolder.instance.delegate = delegate
            }
        }

        @JvmStatic
        fun setLogLevel(level: LogLevel) {
            synchronized(Logger::class.java) {
                SingletonHolder.instance.logLevel = level
            }
        }

        @JvmStatic
        fun error(tag: String, message: String) {
            if (SingletonHolder.instance.logLevel <= LogLevel.ERROR) {
                SingletonHolder.instance.delegate.error(tag, message)
            }
        }

        @JvmStatic
        fun error(tag: String, message: String, exception: Throwable) {
            if (SingletonHolder.instance.logLevel <= LogLevel.ERROR) {
                SingletonHolder.instance.delegate.error(tag, message, exception)
            }
        }

        @JvmStatic
        fun info(tag: String, message: String) {
            if (SingletonHolder.instance.logLevel <= LogLevel.INFO) {
                SingletonHolder.instance.delegate.info(tag, message)
            }
        }

        @JvmStatic
        fun debug(tag: String, message: String) {
            if (SingletonHolder.instance.logLevel <= LogLevel.DEBUG) {
                SingletonHolder.instance.delegate.debug(tag, message)
            }
        }
    }
}
