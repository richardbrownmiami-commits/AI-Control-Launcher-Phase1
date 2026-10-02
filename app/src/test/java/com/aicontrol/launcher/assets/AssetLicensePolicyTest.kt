package com.aicontrol.launcher.assets

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetLicensePolicyTest {
    @Test fun permitsAttributionAndShareAlikeLicenses() {
        assertTrue(AssetLicensePolicy.isReusable("CC BY 4.0"))
        assertTrue(AssetLicensePolicy.isReusable("CC BY-SA 3.0"))
        assertTrue(AssetLicensePolicy.isReusable("CC0"))
        assertTrue(AssetLicensePolicy.isReusable("Public domain"))
    }

    @Test fun rejectsCommerciallyRestrictedAndUnknownLicenses() {
        assertFalse(AssetLicensePolicy.isReusable("CC BY-NC 4.0"))
        assertFalse(AssetLicensePolicy.isReusable("CC BY-ND 4.0"))
        assertFalse(AssetLicensePolicy.isReusable(""))
        assertFalse(AssetLicensePolicy.isReusable("All rights reserved"))
    }
}
