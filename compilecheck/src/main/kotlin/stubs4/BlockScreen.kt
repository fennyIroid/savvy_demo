@file:Suppress("unused", "UNUSED_PARAMETER")

package com.iroid.savvy.rd.block

// The real BlockScreen is Jetpack Compose (app/src/main/ui), which this JVM-only module
// cannot compile. The Robolectric suite tests BlockActivity's logic, so rendering is a no-op.
class BlockScreen(activity: BlockActivity) {
    fun render(state: BlockUiState) {}
    fun onSuccess(done: () -> Unit) = done()
}
