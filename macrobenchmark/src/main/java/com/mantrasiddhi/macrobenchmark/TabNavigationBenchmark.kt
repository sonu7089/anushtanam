package com.mantrasiddhi.macrobenchmark

import android.content.Intent
import android.os.SystemClock
import androidx.benchmark.ExperimentalBenchmarkConfigApi
import androidx.benchmark.ExperimentalConfig
import androidx.benchmark.perfetto.ExperimentalPerfettoCaptureApi
import androidx.benchmark.perfetto.PerfettoConfig
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.Until
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in, reproducible layout comparison. Fixture results must be labeled separately from real accounts. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalBenchmarkConfigApi::class, ExperimentalPerfettoCaptureApi::class)
class TabNavigationBenchmark {
    @get:Rule val benchmark = MacrobenchmarkRule()

    @Test fun fixtureTabs() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Pass navigationFixture=true", args.getString("navigationFixture") == "true")
        val iterations = (args.getString("iterations") ?: "20").toInt().also { require(it > 0) }
        val runId = args.getString("runId") ?: "manual"
        require(runId.matches(Regex("[A-Za-z0-9-]+")))
        val traceCompositions = args.getString("traceCompositions") == "true"
        val samples = JSONArray()
        val context = InstrumentationRegistry.getInstrumentation().context
        val config = context.assets.open("baseline-perfetto.pbtxt").bufferedReader().use { it.readText() }
        Configurator.getInstance().waitForIdleTimeout = 0
        benchmark.measureRepeated(
            packageName = PACKAGE, metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.None(), iterations = iterations,
            experimentalConfig = ExperimentalConfig(perfettoConfig = PerfettoConfig.Text(config)),
            setupBlock = {
                startActivityAndWait(Intent().setClassName(PACKAGE, "com.mantrasiddhi.perf.NavigationFixtureActivity")
                    .putExtra("traceCompositions", traceCompositions))
                check(device.wait(Until.hasObject(By.res("perf_home_ready")), 15_000))
                SystemClock.sleep(500)
            }
        ) {
            // Include both directions of Home/Practice and the original settings/progress journey.
            for (route in listOf("practice", "home", "progress", "settings", "home")) {
                val tab = checkNotNull(device.wait(Until.findObject(By.res("perf_tab_$route")), 5000))
                val start = SystemClock.elapsedRealtimeNanos()
                tab.click()
                check(device.wait(Until.hasObject(By.res("perf_${route}_ready")), 15_000)) { "Missing $route content" }
                if (iteration != null) samples.put(JSONObject().put("iteration", iteration).put("route", route)
                    .put("observedTapToUsableMs", (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0))
                SystemClock.sleep(600) // Full transition and immediate entry work, including interrupted-frame tails.
            }
        }
        val dir = File(context.getExternalFilesDir(null), "tab-navigation/$runId").apply { mkdirs() }
        File(dir, "navigation.json").writeText(JSONObject().put("fixture", true)
            .put("traceCompositions", traceCompositions).put("compilation", "None")
            .put("iterations", iterations).put("samples", samples).toString(2))
    }

    private companion object { const val PACKAGE = "shastralife.anushtanam" }
}
