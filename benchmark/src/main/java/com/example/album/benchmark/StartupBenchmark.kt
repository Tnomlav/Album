package com.example.album.benchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold-start benchmark for the release build.
 *
 * Run on a device that is not in low-power mode:
 *
 *   ./gradlew :benchmark:connectedDebugAndroidTest
 *
 * The app must be installed first (the module targets `:app`). Results are
 * written to `benchmark/build/outputs/connected_android_test_additional_output/`
 * and printed as P50/P90/P95 in the instrumentation output, so they can replace
 * the historical cold-start numbers in `docs/产品优化与调整指南-2026-11.md` §10.1.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = rule.measureRepeated(
        packageName = "com.example.album",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
    ) {
        pressHome()
        startActivityAndWait()
    }
}
