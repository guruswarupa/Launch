package com.guruswarupa.launch.core

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

object BackgroundWork {
    private val counter = AtomicInteger(0)

    private val executor: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "launch-bg-${counter.incrementAndGet()}")
    }

    fun execute(block: () -> Unit) {
        executor.execute(block)
    }
}
