package com.tf.aurora.mask

import android.util.Log

object Trace {
    const val TAG = "TF.AF"

    fun line(msg: String) {
        Log.i(TAG, msg)
    }

    fun conv(src: String, map: Map<String, Any>, afId: String) {
        val status = map["af_status"]?.toString().orEmpty().ifBlank { "EMPTY" }
        val media = map["media_source"]?.toString().orEmpty()
        val campaign = map["campaign"]?.toString().orEmpty()
        line("$src af_status=$status af_id=$afId media_source=$media campaign=$campaign")
    }
}
