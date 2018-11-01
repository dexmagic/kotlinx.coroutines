/*
 * Copyright 2016-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines

import org.junit.*
import org.junit.Test
import java.io.*
import kotlin.coroutines.*
import kotlin.test.*

@Suppress("SUSPENSION_POINT_INSIDE_MONITOR")
class CoroutinesDumpTest {

    private val monitor = Any()

    @Before
    fun setUp() {
        DebugProbes.install()
    }

    @After
    fun tearDown() {
        DebugProbes.uninstall()
    }

    @Test
    fun testSuspendedCoroutine() = synchronized(monitor) {
        val deferred = GlobalScope.async {
            sleepingOuterMethod()
        }

        awaitCoroutineStarted()
        Thread.sleep(100)  // Let delay be invoked
        verifyDump(
            "Coroutine \"coroutine#1\":DeferredCoroutine{Active}@1e4a7dd4, state: SUSPENDED\n" +
                "\tat kotlinx/coroutines/CoroutinesDumpTest.sleepingNestedMethod(CoroutinesDumpTest.kt:95)\n" +
                "\tat kotlinx/coroutines/CoroutinesDumpTest.sleepingOuterMethod(CoroutinesDumpTest.kt:88)\n" +
                "\tat kotlinx/coroutines/CoroutinesDumpTest\$testSuspendedCoroutine\$1\$deferred\$1.invokeSuspend(CoroutinesDumpTest.kt:29)\n" +
            "\t(Coroutine creation stacktrace)\n" +
                "\tat kotlin.coroutines.intrinsics.IntrinsicsKt__IntrinsicsJvmKt.createCoroutineUnintercepted(IntrinsicsJvm.kt:116)\n" +
                "\tat kotlinx.coroutines.intrinsics.CancellableKt.startCoroutineCancellable(Cancellable.kt:23)\n" +
                "\tat kotlinx.coroutines.CoroutineStart.invoke(CoroutineStart.kt:99)\n")
        deferred.cancel()
        runBlocking { deferred.join() }
    }

    @Test
    fun testRunningCoroutine() = synchronized(monitor) {
        val deferred = GlobalScope.async {
            activeMethod(shouldSuspend = false)
        }

        awaitCoroutineStarted()
        verifyDump(
            "Coroutine \"coroutine#1\":DeferredCoroutine{Active}@1e4a7dd4, state: RUNNING (Last suspension stacktrace, not an actual stacktrace)\n" +
                    "\tat kotlinx/coroutines/CoroutinesDumpTest\$testRunningCoroutine\$1\$deferred\$1.invokeSuspend(CoroutinesDumpTest.kt:49)\n" +
             "\t(Coroutine creation stacktrace)\n" +
                    "\tat kotlin.coroutines.intrinsics.IntrinsicsKt__IntrinsicsJvmKt.createCoroutineUnintercepted(IntrinsicsJvm.kt:116)\n" +
                    "\tat kotlinx.coroutines.intrinsics.CancellableKt.startCoroutineCancellable(Cancellable.kt:23)\n" +
                    "\tat kotlinx.coroutines.CoroutineStart.invoke(CoroutineStart.kt:99)\n" +
                    "\tat kotlinx.coroutines.AbstractCoroutine.start(AbstractCoroutine.kt:148)\n" +
                    "\tat kotlinx.coroutines.BuildersKt__Builders_commonKt.async(Builders.common.kt)\n" +
                    "\tat kotlinx.coroutines.BuildersKt.async(Unknown Source)\n" +
                    "\tat kotlinx.coroutines.BuildersKt__Builders_commonKt.async\$default(Builders.common.kt)\n" +
                    "\tat kotlinx.coroutines.BuildersKt.async\$default(Unknown Source)\n" +
                    "\tat kotlinx.coroutines.CoroutinesDumpTest.testRunningCoroutine(CoroutinesDumpTest.kt:49)")
        deferred.cancel()
        runBlocking { deferred.join() }
    }

    @Test
    fun testRunningCoroutineWithSuspensionPoint() = synchronized(monitor) {
        val deferred = GlobalScope.async {
            activeMethod(shouldSuspend = true)
        }

        awaitCoroutineStarted()
        verifyDump(
           "Coroutine \"coroutine#1\":DeferredCoroutine{Active}@1e4a7dd4, state: RUNNING (Last suspension stacktrace, not an actual stacktrace)\n" +
                   "\tat kotlinx/coroutines/CoroutinesDumpTest.nestedActiveMethod(CoroutinesDumpTest.kt:111)\n" +
                   "\tat kotlinx/coroutines/CoroutinesDumpTest.activeMethod(CoroutinesDumpTest.kt:106)\n" +
                   "\tat kotlinx/coroutines/CoroutinesDumpTest\$testRunningCoroutineWithSuspensionPoint\$1\$deferred\$1.invokeSuspend(CoroutinesDumpTest.kt:71)\n" +
           "\t(Coroutine creation stacktrace)\n" +
                   "\tat kotlin.coroutines.intrinsics.IntrinsicsKt__IntrinsicsJvmKt.createCoroutineUnintercepted(IntrinsicsJvm.kt:116)\n" +
                   "\tat kotlinx.coroutines.intrinsics.CancellableKt.startCoroutineCancellable(Cancellable.kt:23)\n" +
                   "\tat kotlinx.coroutines.CoroutineStart.invoke(CoroutineStart.kt:99)\n" +
                   "\tat kotlinx.coroutines.AbstractCoroutine.start(AbstractCoroutine.kt:148)\n" +
                   "\tat kotlinx.coroutines.BuildersKt__Builders_commonKt.async(Builders.common.kt)\n" +
                   "\tat kotlinx.coroutines.BuildersKt.async(Unknown Source)\n" +
                   "\tat kotlinx.coroutines.BuildersKt__Builders_commonKt.async\$default(Builders.common.kt)\n" +
                   "\tat kotlinx.coroutines.BuildersKt.async\$default(Unknown Source)\n" +
                   "\tat kotlinx.coroutines.CoroutinesDumpTest.testRunningCoroutineWithSuspensionPoint(CoroutinesDumpTest.kt:71)")
        deferred.cancel()
        runBlocking { deferred.join() }
    }

    @Test
    fun testFinishedCoroutineRemoved() = synchronized(monitor) {
        val deferred = GlobalScope.async {
            activeMethod(shouldSuspend = true)
        }

        awaitCoroutineStarted()
        deferred.cancel()
        runBlocking { deferred.join() }
        verifyDump()
    }

    private suspend fun activeMethod(shouldSuspend: Boolean) {
        nestedActiveMethod(shouldSuspend)
        delay(1)
    }

    private suspend fun nestedActiveMethod(shouldSuspend: Boolean) {
        if (shouldSuspend) delay(1)
        notifyTest()
        while (coroutineContext[Job]!!.isActive) {
            Thread.sleep(100)
        }
    }

    private suspend fun sleepingOuterMethod() {
        sleepingNestedMethod()
        delay(1)
    }

    private suspend fun sleepingNestedMethod() {
        delay(1)
        notifyTest()
        delay(Long.MAX_VALUE)
    }

    private fun awaitCoroutineStarted() {
        (monitor as Object).wait()
    }

    private fun notifyTest() {
        synchronized(monitor) {
            (monitor as Object).notify()
        }
    }

    private fun verifyDump(vararg traces: String) {
        val baos = ByteArrayOutputStream()
        DebugProbes.dumpCoroutines(PrintStream(baos))
        val trace = baos.toString().split("\n\n")
        if (traces.isEmpty()) {
            assertEquals(1, trace.size)
            assertTrue(trace[0].startsWith("Coroutines dump"))
            return
        }

        trace.withIndex().drop(1).forEach { (index, value) ->
            val expected = traces[index - 1].applyBackspace().split("\n\t(Coroutine creation stacktrace)\n", limit = 2)
            val actual = value.applyBackspace().split("\n\t(Coroutine creation stacktrace)\n", limit = 2)
            assertEquals(expected.size, actual.size)

            expected.withIndex().forEach { (index, trace) ->
                val actualTrace = actual[index].trimStackTrace().sanitizeAddresses()
                val expectedTrace = trace.trimStackTrace().sanitizeAddresses()
                val actualLines = actualTrace.split("\n")
                val expectedLines = expectedTrace.split("\n")
                for (i in 0 until expectedLines.size) {
                    assertEquals(expectedLines[i], actualLines[i])
                }
            }
        }
    }

    private fun String.sanitizeAddresses(): String {
        val index = indexOf("coroutine#")
        val next = indexOf(',', index)
        if (index == -1 || next == -1) return this
        return substring(0, index) + substring(next, length)
    }
}
