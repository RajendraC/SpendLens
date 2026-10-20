package com.spendlens.app

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendlens.core.MerchantRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Guards the privacy promises in the merged manifest, including anything libraries add. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class ManifestSecurityTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Suppress("DEPRECATION")
    private val info get() = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES)

    @Test
    fun noInternetPermission() {
        val perms = info.requestedPermissions.orEmpty().toList()
        assertFalse("INTERNET must never be requested: $perms", "android.permission.INTERNET" in perms)
    }

    @Test
    fun onlyOneSensitivePermission() {
        val perms = info.requestedPermissions.orEmpty().toSet()
        assertTrue("android.permission.READ_SMS" in perms)
        val forbidden = setOf(
            "android.permission.RECEIVE_SMS", "android.permission.SEND_SMS", "android.permission.READ_CONTACTS",
            "android.permission.READ_CALL_LOG", "android.permission.ACCESS_FINE_LOCATION", "android.permission.QUERY_ALL_PACKAGES",
            "android.permission.READ_EXTERNAL_STORAGE", "android.permission.READ_MEDIA_IMAGES",
        )
        assertEquals(emptySet<String>(), perms intersect forbidden)
    }

    @Test
    fun backupIsOff() {
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun onlyTheLauncherActivityIsExported() {
        val exported = info.activities.orEmpty().filter { it.exported }.map { it.name }
        assertEquals(listOf("com.spendlens.app.ui.MainActivity"), exported.filter { it.startsWith("com.spendlens") })
    }

    /** Android 11+ hides other apps unless they are listed in <queries>; a missing entry means no icon. */
    @Test
    fun everyMerchantAppIsDeclaredForIconLookup() {
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        val missing = MerchantRegistry.packageNames.filter { "<package android:name=\"$it\" />" !in manifest }
        assertEquals("Add these to <queries> in AndroidManifest.xml", emptyList<String>(), missing)
    }
}
