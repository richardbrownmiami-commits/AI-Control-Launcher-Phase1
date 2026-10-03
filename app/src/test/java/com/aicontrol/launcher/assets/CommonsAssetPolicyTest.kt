package com.aicontrol.launcher.assets

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommonsAssetPolicyTest {
    @Test
    fun acceptsOnlyHttpsCommonsImageDeliveryHosts() {
        assertTrue(CommonsAssetPolicy.isImageUrl("https://upload.wikimedia.org/wikipedia/commons/a/ab/example.jpg"))
        assertTrue(CommonsAssetPolicy.isImageUrl("https://thumb.wikimedia.org/wikipedia/commons/thumb/a/ab/example.jpg/1920px-example.jpg?x=1"))
        assertFalse(CommonsAssetPolicy.isImageUrl("http://upload.wikimedia.org/wikipedia/commons/example.jpg"))
        assertFalse(CommonsAssetPolicy.isImageUrl("https://upload.wikimedia.org.attacker.example/wikipedia/commons/example.jpg"))
        assertFalse(CommonsAssetPolicy.isImageUrl("https://example.org/wikipedia/commons/example.jpg"))
        assertFalse(CommonsAssetPolicy.isImageUrl("https://thumb.wikimedia.org/other/example.jpg"))
    }
}
