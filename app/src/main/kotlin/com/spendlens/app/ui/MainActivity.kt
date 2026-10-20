package com.spendlens.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spendlens.app.SpendLensApp
import com.spendlens.app.ui.theme.SpendLensTheme
import kotlinx.coroutines.launch

/**
 * The only activity. FragmentActivity (not just ComponentActivity) because BiometricPrompt needs it.
 */
class MainActivity : FragmentActivity() {

    private lateinit var vm: AppViewModel

    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) = vm.onAppForeground()
        override fun onStop(owner: LifecycleOwner) = vm.onAppBackground()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as SpendLensApp).container
        vm = ViewModelProvider(this, viewModelFactory { initializer { AppViewModel(container) } })[AppViewModel::class.java]

        lifecycleScope.launch {
            vm.settings.collect { s -> applyScreenPrivacy(s?.screenPrivacy ?: true) }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)

        setContent {
            SpendLensTheme {
                SpendLensRoot(vm, platform)
            }
        }
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processObserver)
        super.onDestroy()
    }

    /** Blocks screenshots, screen recording and the Recents thumbnail while on. */
    private fun applyScreenPrivacy(on: Boolean) {
        if (on) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!on)
    }

    private val authenticators: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    private val platform = object : Platform {
        override fun canUseDeviceLock(): Boolean =
            BiometricManager.from(this@MainActivity).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

        override fun authenticate(title: String, subtitle: String, onSuccess: () -> Unit, onFail: (String) -> Unit) {
            if (!canUseDeviceLock()) { onFail("Set a screen lock on your phone first"); return }
            val prompt = BiometricPrompt(this@MainActivity, ContextCompat.getMainExecutor(this@MainActivity), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail(errString.toString())
            })
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(authenticators)
                .build()
            prompt.authenticate(info)
        }

        override fun openAppSettings() {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
    }
}

/** What the UI needs from the activity, behind an interface so tests can run without a fingerprint. */
interface Platform {
    fun canUseDeviceLock(): Boolean
    fun authenticate(title: String, subtitle: String, onSuccess: () -> Unit, onFail: (String) -> Unit)
    fun openAppSettings()
}
