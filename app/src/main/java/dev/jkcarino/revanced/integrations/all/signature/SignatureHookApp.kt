package dev.jkcarino.revanced.integrations.all.signature

import android.app.Application
import android.content.Context

class SignatureHookApp : Application() {

    override fun attachBaseContext(base: Context) {
        val pmsHookApplication = PmsHookApplication("<signature>")
        pmsHookApplication.hook(base)
        super.attachBaseContext(base)
    }
}
