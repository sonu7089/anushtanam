package com.mantrasiddhi.macrobenchmark

import android.os.SystemClock
import androidx.benchmark.ExperimentalBenchmarkConfigApi
import androidx.benchmark.ExperimentalConfig
import androidx.benchmark.perfetto.ExperimentalPerfettoCaptureApi
import androidx.benchmark.perfetto.PerfettoConfig
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Actual launcher and destinations; requires an explicitly prepared account state. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalBenchmarkConfigApi::class, ExperimentalPerfettoCaptureApi::class)
class AppBaseline {
    @get:Rule val benchmark = MacrobenchmarkRule()
    private val args get() = InstrumentationRegistry.getArguments()
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val iterations get() = (args.getString("iterations") ?: "20").toInt().also { require(it >= 1) }
    private val signedIn get() = args.getString("accountState") == "home"
    private val readyTag get() = if (signedIn) "perf_home_ready" else "perf_welcome_ready"
    private val traceConfig by lazy {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        ExperimentalConfig(perfettoConfig = PerfettoConfig.Text(
            assets.open("baseline-perfetto.pbtxt").bufferedReader().use { it.readText() }))
    }

    @Before fun useExplicitReadiness() {
        // Animated/refreshing Compose screens need not become accessibility-idle.
        // Avoid adding UiAutomator's implicit 10-second idle waits to each selector;
        // our bounded ready-tag waits define exactly what the measurement requires.
        Configurator.getInstance().waitForIdleTimeout = 0
    }

    @Test fun coldStartup() = startup(StartupMode.COLD)
    @Test fun warmStartup() = startup(StartupMode.WARM)
    @Test fun hotStartup() = startup(StartupMode.HOT)

    private fun startup(mode: StartupMode) {
        val samples = JSONArray()
        benchmark.measureRepeated(
            packageName = PACKAGE, metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            compilationMode = CompilationMode.None(), startupMode = mode, iterations = iterations,
            experimentalConfig = traceConfig,
            setupBlock = { pressHome() }
        ) {
            val start = SystemClock.elapsedRealtimeNanos()
            launchApp(clearTask = mode != StartupMode.HOT)
            awaitTag(readyTag)
            // Macrobenchmark also invokes this block for an unmeasured warmup.
            if (iteration != null) samples.put((SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0)
        }
        save("startup-${mode.name.lowercase()}", JSONObject().put("observedLaunchToUsableMs", samples)
            .put("accountState", if (signedIn) "home" else "welcome"))
    }

    @Test fun navigation() {
        assumeTrue("Sign into a disposable account and pass accountState=home", signedIn)
        val samples = JSONArray()
        benchmark.measureRepeated(
            packageName = PACKAGE, metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.None(), iterations = iterations,
            experimentalConfig = traceConfig,
            setupBlock = { launchApp(); awaitTag("perf_home_ready"); tap("home") }
        ) {
            for (route in listOf("progress", "settings", "home")) {
                val start = SystemClock.elapsedRealtimeNanos()
                tap(route)
                awaitTag("perf_${route}_ready")
                samples.put(JSONObject().put("route", route)
                    .put("observedTapToUsableMs", (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0))
                // Keep the full 320 ms transition inside the frame capture.
                SystemClock.sleep(500)
            }
        }
        save("navigation", JSONObject().put("samples", samples))
    }

    @Test fun idleWork() {
        // No process restart between snapshots: counts are monotonic for one process.
        launchApp()
        awaitTag(readyTag)
        if (signedIn) { tap("progress"); awaitTag("perf_progress_ready"); tap("settings"); awaitTag("perf_settings_ready") }
        SystemClock.sleep(10_000) // Allow WhileSubscribed timeouts and first-entry work to settle.
        val before = snapshot()
        SystemClock.sleep(30_000)
        val after = snapshot()
        check(before.getInt("pid") == after.getInt("pid")) { "Process restarted during idle sample" }
        val elapsed = after.getLong("elapsedMs") - before.getLong("elapsedMs")
        save("idle", JSONObject().put("before", before).put("after", after)
            .put("cpuPercentOfOneCore", 100.0 * (after.getLong("cpuMs") - before.getLong("cpuMs")) / elapsed))
    }

    private fun tap(route: String) {
        val node = device.wait(Until.findObject(By.res("perf_tab_$route")), 15_000)
        checkNotNull(node) { "Missing navigation tab $route" }.click()
    }

    private fun launchApp(clearTask: Boolean = false) {
        // The launcher is a short-lived SplashActivity. Macrobenchmark's gfxinfo
        // waiter can lose that window after Main replaces it; wait for real content
        // instead. Use the same MAIN/LAUNCHER/new-task/reset-task intent as a launcher.
        // Recreate activities for WARM while keeping the process alive. This is
        // normally supplied by MacrobenchmarkScope.startActivityAndWait.
        val flags = if (clearTask) "0x10208000" else "0x10200000"
        val output = device.executeShellCommand("am start -W -a android.intent.action.MAIN " +
            "-c android.intent.category.LAUNCHER -f $flags " +
            "-n $PACKAGE/com.mantrasiddhi.presentation.SplashActivity")
        check(output.contains("Status: ok")) { "Launcher failed: $output" }
    }

    private fun awaitTag(tag: String) {
        check(device.wait(Until.hasObject(By.res(tag)), 30_000)) {
            "Timed out waiting for $tag; check account state, setup gates and permission dialogs"
        }
    }

    private fun snapshot(): JSONObject {
        val output = device.executeShellCommand("am broadcast -n $PACKAGE/com.mantrasiddhi.perf.PerfSnapshotReceiver")
        val data = output.substringAfter("data=\"", "").substringBeforeLast('"')
        check(data.isNotBlank()) { "No benchmark probe response: $output" }
        return JSONObject(data)
    }

    private fun save(name: String, data: JSONObject) {
        val context = InstrumentationRegistry.getInstrumentation().context
        val runId = args.getString("runId") ?: "manual"
        require(runId.matches(Regex("[A-Za-z0-9-]+")))
        val dir = File(context.getExternalFilesDir(null), "perf-baseline/$runId").apply { mkdirs() }
        data.put("compilation", if (name == "idle") "inherited from preceding tests" else "None")
            .put("traceConfig", "baseline-perfetto-v1")
            .put("iterations", iterations).put("device", android.os.Build.MODEL)
            .put("sdk", android.os.Build.VERSION.SDK_INT)
        File(dir, "$name.json").writeText(data.toString(2))
    }

    private companion object { const val PACKAGE = "shastralife.anushtanam" }
}
