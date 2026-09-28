@file:Suppress("unused", "UNUSED_PARAMETER")

package androidx.activity

import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.lifecycle.LifecycleOwner

abstract class OnBackPressedCallback(enabled: Boolean) {
    abstract fun handleOnBackPressed()
}

class OnBackPressedDispatcher {
    fun addCallback(owner: LifecycleOwner, onBackPressedCallback: OnBackPressedCallback) {}
}

open class ComponentActivity : android.app.Activity(), LifecycleOwner {
    val onBackPressedDispatcher: OnBackPressedDispatcher = OnBackPressedDispatcher()
    fun <I, O> registerForActivityResult(contract: ActivityResultContract<I, O>, callback: ActivityResultCallback<O>): ActivityResultLauncher<I> =
        object : ActivityResultLauncher<I>() { override fun launch(input: I) {} }
}
