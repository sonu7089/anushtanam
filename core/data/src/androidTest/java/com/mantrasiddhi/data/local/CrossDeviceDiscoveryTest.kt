package com.mantrasiddhi.data.local

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Host starts receiver first, waits for READY, then runs sender on a different Android device. */
class CrossDeviceDiscoveryTest {
    @Test fun receive() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("firebaseEmulator") == "true" && args.getString("crossDeviceRole") == "receive")
        RemoteDiscoveryTest().receiveFromOtherDevice(requireNotNull(args.getString("crossDeviceEmail")))
    }
    @Test fun send() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("firebaseEmulator") == "true" && args.getString("crossDeviceRole") == "send")
        RemoteDiscoveryTest().sendToOtherDevice(requireNotNull(args.getString("crossDeviceEmail")))
    }
}
