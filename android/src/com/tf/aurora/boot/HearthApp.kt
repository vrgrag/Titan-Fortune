package com.tf.aurora.boot

import android.app.Application
import com.tf.aurora.keep.Coffer
import com.tf.aurora.net.OraclePost
import com.tf.aurora.track.BeaconHub

class HearthApp : Application() {
    lateinit var director: FateDirector
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        val hub = BeaconHub()
        hub.attach(this)
        director = FateDirector(Coffer(this), hub, OraclePost())
    }

    companion object {
        lateinit var instance: HearthApp
            private set
    }
}
