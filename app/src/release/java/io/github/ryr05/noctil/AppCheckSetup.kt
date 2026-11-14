package io.github.ryr05.noctil

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Release builds prove to Firebase (App Check) that requests come from the genuine Noctil
 * on a genuine Android phone, using Google Play Integrity. Noctil isn't on Google Play, so
 * the Firebase console is set to "outside Google Play": device integrity only.
 */
object AppCheckSetup {
    fun install(context: Context) {
        Firebase.appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    }
}
