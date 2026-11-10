package io.github.ryr05.noctil.parse

import io.github.ryr05.noctil.cloud.CloudSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CloudFingerprintTest {
    @Test fun sameContentFromPhoneOrCloudMatches() {
        // Firestore hands numbers back as Longs; the phone has Ints.
        val local = mapOf<String, Any?>("title" to "Essay", "priority" to 3, "done" to false, "list" to null, "dueTime" to 1020)
        val cloud = mapOf<String, Any?>("dueTime" to 1020L, "list" to null, "done" to false, "priority" to 3L, "title" to "Essay",
            "updatedAt" to 123456L, "deleted" to false)
        assertEquals(CloudSync.fingerprint(local), CloudSync.fingerprint(cloud))
    }

    @Test fun changedContentDiffers() {
        val a = mapOf<String, Any?>("title" to "Essay", "done" to false)
        val b = mapOf<String, Any?>("title" to "Essay", "done" to true)
        assertNotEquals(CloudSync.fingerprint(a), CloudSync.fingerprint(b))
    }

    @Test fun syncOrderPutsParentsFirst() {
        val order = CloudSync.TABLES
        assert(order.indexOf("lists") < order.indexOf("tasks"))
        assert(order.indexOf("tasks") < order.indexOf("checklist"))
        assert(order.indexOf("tags") < order.indexOf("taskTags"))
        assert(order.indexOf("habits") < order.indexOf("habitChecks"))
    }
}
