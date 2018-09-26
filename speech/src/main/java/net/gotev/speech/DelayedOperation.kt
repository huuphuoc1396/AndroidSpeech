package net.gotev.speech

import android.content.Context
import android.os.Handler

import net.gotev.speech.logger.Logger

import java.util.Timer
import java.util.TimerTask

/**
 * @author huuphuoc1396
 */
class DelayedOperation(private val context: Context, private val delay: Long, private val tag: String) {
    private var operation: Operation? = null
    private var timer: Timer? = null
    private var started: Boolean = false

    interface Operation {
        fun onDelayedOperation()
        fun shouldExecuteDelayedOperation(): Boolean
    }

    init {
        if (delay <= 0) {
            throw IllegalArgumentException("The delay in milliseconds must be > 0")
        }
        Logger.debug(LOG_TAG, "created delayed operation with tag: $tag")
    }

    fun start(operation: Operation?) {
        if (operation == null) {
            throw IllegalArgumentException("The operation must be defined!")
        }

        Logger.debug(LOG_TAG, "starting delayed operation with tag: $tag")
        this.operation = operation
        cancel()
        started = true
        resetTimer()
    }

    fun resetTimer() {
        if (!started) return

        timer?.cancel()

        Logger.debug(LOG_TAG, "resetting delayed operation with tag: $tag")
        timer = Timer()
        timer?.schedule(object : TimerTask() {
            override fun run() {
                if (operation?.shouldExecuteDelayedOperation() == true) {
                    Logger.debug(LOG_TAG, "executing delayed operation with tag: $tag")
                    Handler(context.mainLooper).post { operation?.onDelayedOperation() }
                }
                cancel()
            }
        }, delay)
    }

    fun cancel() {
        timer?.let {
            Logger.debug(LOG_TAG, "cancelled delayed operation with tag: $tag")
            it.cancel()
            timer = null
        }

        started = false
    }

    companion object {

        private val LOG_TAG = DelayedOperation::class.java.simpleName
    }
}
