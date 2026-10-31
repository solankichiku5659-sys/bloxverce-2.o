package com.example

import android.app.Application
import android.content.res.Resources
import android.view.View

/**
 * Custom Application class for BloxVerse.
 * Protects resource resolution against invalid (NO_ID / 0xffffffff) queries by third-party
 * SDKs or Chromium accessibility engines, preventing native AssetManager.cpp error spam.
 */
class BloxVerseApp : Application() {

    private var wrappedResources: Resources? = null

    override fun getResources(): Resources {
        val base = super.getResources()
        if (wrappedResources == null) {
            wrappedResources = object : Resources(base.assets, base.displayMetrics, base.configuration) {
                override fun getResourceName(resid: Int): String {
                    if (resid <= 0 || resid == View.NO_ID || (resid ushr 24) == 0xff) {
                        throw NotFoundException("NO_ID")
                    }
                    return super.getResourceName(resid)
                }

                override fun getResourceEntryName(resid: Int): String {
                    if (resid <= 0 || resid == View.NO_ID || (resid ushr 24) == 0xff) {
                        throw NotFoundException("NO_ID")
                    }
                    return super.getResourceEntryName(resid)
                }

                override fun getResourcePackageName(resid: Int): String {
                    if (resid <= 0 || resid == View.NO_ID || (resid ushr 24) == 0xff) {
                        throw NotFoundException("NO_ID")
                    }
                    return super.getResourcePackageName(resid)
                }

                override fun getResourceTypeName(resid: Int): String {
                    if (resid <= 0 || resid == View.NO_ID || (resid ushr 24) == 0xff) {
                        throw NotFoundException("NO_ID")
                    }
                    return super.getResourceTypeName(resid)
                }
            }
        }
        return wrappedResources ?: base
    }
}
