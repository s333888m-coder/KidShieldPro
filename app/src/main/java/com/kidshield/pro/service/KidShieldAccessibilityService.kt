package com.kidshield.pro.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.kidshield.pro.data.PrefsManager
import com.kidshield.pro.ui.BiometricUnlockActivity

class KidShieldAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: KidShieldAccessibilityService? = null
            private set

        @Volatile
        var parentSettingsBypassUntilMs: Long = 0L
    }

    private val prefs: PrefsManager by lazy { PrefsManager(this) }
    private val settingsPackages = setOf(
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.miui.securitycenter",
        "com.samsung.android.packageinstaller"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    fun performHomeAction() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        // Ignore our own app & SystemUI biometric dialog
        if (pkg == packageName || pkg == "com.android.systemui") return

        // 1. If After-Timer Protection Lock is currently enforced -> always keep Fake PIN Overlay on top!
        if (prefs.isProtectionCurrentlyActive()) {
            LockOverlayManager.showFakePinOverlay(this)
            return
        }

        // 2. Anti-Uninstall & Settings Guard:
        // If someone tries to open Settings / Package Installer to uninstall KidShield or disable Device Admin
        if (prefs.isAntiUninstallGuardEnabled &&
            settingsPackages.contains(pkg) &&
            System.currentTimeMillis() > parentSettingsBypassUntilMs
        ) {
            val windowText = buildString {
                event.text?.forEach { append(it).append(" ") }
                rootInActiveWindow?.let { root ->
                    val nodes = root.findAccessibilityNodeInfosByText("KidShield")
                    if (!nodes.isNullOrEmpty()) append("KidShield ")
                }
            }

            val isTargetingKidShield = windowText.contains("KidShield", ignoreCase = true) ||
                windowText.contains("Device admin", ignoreCase = true) ||
                (windowText.contains("Uninstall", ignoreCase = true) &&
                    windowText.contains("Pro", ignoreCase = true))

            if (isTargetingKidShield) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                performGlobalAction(GLOBAL_ACTION_HOME)
                val gateIntent = Intent(this, BiometricUnlockActivity::class.java).apply {
                    putExtra(BiometricUnlockActivity.EXTRA_MODE, BiometricUnlockActivity.MODE_ANTI_UNINSTALL)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(gateIntent)
            }
        }
    }

    override fun onInterrupt() {
        // Required override
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
