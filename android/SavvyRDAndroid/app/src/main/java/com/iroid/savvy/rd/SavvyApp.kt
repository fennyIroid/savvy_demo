package com.iroid.savvy.rd

import android.app.Application
import com.iroid.savvy.rd.data.BackendClient
import com.iroid.savvy.rd.data.CommitmentRepository
import com.iroid.savvy.rd.data.SavvyLog
import com.iroid.savvy.rd.service.BlockingEngine
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class SavvyApp : Application() {
    lateinit var repo: CommitmentRepository; private set
    lateinit var backend: BackendClient; private set
    lateinit var engine: BlockingEngine; private set

    override fun onCreate() {
        super.onCreate()
        // Android ships a stripped "BC" provider without Ed25519; replace it so
        // core's KeyFactory.getInstance("Ed25519") works below API 33. To verify: A-CRYPTO-1.
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)
        SavvyLog.init(this)
        repo = CommitmentRepository(this)
        // The system property exists for JVM tests (Robolectric) that simulate going offline.
        backend = BackendClient({ System.getProperty("savvy.backendUrl") ?: BuildConfig.BACKEND_URL }) { repo.deviceToken }
        engine = BlockingEngine(this, repo)
        SavvyLog.event("App", "onCreate")
    }
}

val android.content.Context.savvy: SavvyApp get() = applicationContext as SavvyApp
