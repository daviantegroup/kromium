package dev.daviante.kromium.core.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture

/**
 * Internal bridge providing zero-overhead, cancellation-cooperative conversion
 * between Kotlin suspending operations and Java [CompletableFuture].
 */
internal object FutureBridge {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun <T> toCompletableFuture(block: suspend () -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        val job = scope.launch {
            try {
                future.complete(block())
            } catch (e: CancellationException) {
                future.cancel(true)
            } catch (t: Throwable) {
                future.completeExceptionally(t)
            }
        }
        future.whenComplete { _, _ ->
            if (future.isCancelled) {
                job.cancel()
            }
        }
        return future
    }
}
