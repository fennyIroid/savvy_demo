package com.iroid.savvy.rd

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import com.iroid.savvy.rd.service.Alerts
import com.iroid.savvy.rd.ui.Routes
import com.iroid.savvy.rd.ui.SavvyRoot

/** Single activity for the app; screens are Compose destinations (ui/SavvyRoot.kt). */
class MainActivity : AppCompatActivity() {
    /** Screen requested by a notification tap, e.g. Permissions after the blocking service was turned off. */
    private val openRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readRoute(intent)
        setContent { SavvyRoot(openRoute = openRoute.value, onRouteOpened = { openRoute.value = null }) }
        // Android 13+: without this the "focus is on" FGS notification is hidden, and Play's
        // monitoring rules expect a visible notification in parent mode.
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readRoute(intent)
    }

    private fun readRoute(i: Intent?) {
        if (i?.getStringExtra(Alerts.EXTRA_OPEN) == Alerts.OPEN_PERMISSIONS) openRoute.value = Routes.PERMISSIONS
    }
}
