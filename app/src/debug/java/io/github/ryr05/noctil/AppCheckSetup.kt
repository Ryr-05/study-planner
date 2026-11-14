package io.github.ryr05.noctil

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds (emulators, test phones) can't pass Play Integrity, so they use App Check's
 * debug provider. It prints a debug secret to Logcat (tag DebugAppCheckProvider) that can be
 * allow-listed in Firebase console > App Check > Apps > Noctil > Manage debug tokens.
 * Never shipped: this file only exists in debug builds.
 */
object AppCheckSetup {
    fun install(context: Context) {
        Firebase.appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }
}
