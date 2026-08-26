package com.tf.aurora.boot

import android.app.Application
import android.os.Build
import com.tf.aurora.keep.Coffer
import com.tf.aurora.net.OraclePost
import com.tf.aurora.note.PulseCatcher
import com.tf.aurora.track.BeaconHub

class HearthApp : Application() {
    lateinit var director: FateDirector
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (Build.VERSION.SDK_INT >= 26) {
            PulseCatcher.ensureChannel(this)
        }
        val hub = BeaconHub()
        hub.attach(this)
        director = FateDirector(Coffer(this), hub, OraclePost())
    }

    companion object {
        lateinit var instance: HearthApp
            private set
    }
}
