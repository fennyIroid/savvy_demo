package com.iroid.savvy.rd

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.iroid.savvy.rd.ui.SavvyRoot

/** Single activity for the app; screens are Compose destinations (ui/SavvyRoot.kt). */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { SavvyRoot() }
        // Android 13+: without this the "focus is on" FGS notification is hidden, and Play's
        // monitoring rules expect a visible notification in parent mode.
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}
