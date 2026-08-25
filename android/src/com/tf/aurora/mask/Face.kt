package com.tf.aurora.mask

import android.os.Build

/** Shared Chrome-on-Android UA for config, GCD and WebView. */
object Face {
    fun line(): String {
        val release = Build.VERSION.RELEASE ?: "14"
        val vendor = (Build.MANUFACTURER ?: "Google").replaceFirstChar { it.uppercaseChar() }
        val model = Build.MODEL ?: "Pixel 8"
        val stamp = (Build.ID ?: "AP3A").take(18)
        return "Mozilla/5.0 (Linux; Android $release; $vendor $model Build/$stamp) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/148.0.7725.125 Mobile Safari/537.36"
    }
}
