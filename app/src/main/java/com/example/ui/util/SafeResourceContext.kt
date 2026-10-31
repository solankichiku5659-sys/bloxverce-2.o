package com.example.ui.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.view.View

/**
 * Context wrapper that prevents invalid resource ID (0xffffffff / View.NO_ID) queries from
 * reaching native AssetManager.cpp, eliminating "No package ID ff found for resource ID 0xffffffff"
 * logs when WebViews and Chromium accessibility/autofill traverse the view tree.
 */
class SafeResourceContext(base: Context) : ContextWrapper(base) {
    private val safeResources by lazy {
        object : Resources(base.resources.assets, base.resources.displayMetrics, base.resources.configuration) {
            override fun getResourceName(resid: Int): String {
                if (resid <= 0 || resid == View.NO_ID || (resid ushr 24) == 0xff) {
                    throw NotFoundException("NO_ID: 0x${Integer.toHexString(resid)}")
                }
                return super.getResourceName(resid)
            }

            override fun getResourceEntryName(resid: Int): String {
                if (resid <= 0 || resid == View.NO_ID || (resid ushr 24) == 0xff) {
                    throw NotFoundException("NO_ID: 0x${Integer.toHexString(resid)}")
                }
                return super.getResourceEntryName(resid)
            }

            override fun getResourcePackageName(resid: Int): String {
                if (resid <= 0 || resid == View.NO_ID || (resid ushr 24) == 0xff) {
                    throw NotFoundException("NO_ID: 0x${Integer.toHexString(resid)}")
                }
                return super.getResourcePackageName(resid)
            }

            override fun getResourceTypeName(resid: Int): String {
                if (resid <= 0 || resid == View.NO_ID || (resid ushr 24) == 0xff) {
                    throw NotFoundException("NO_ID: 0x${Integer.toHexString(resid)}")
                }
                return super.getResourceTypeName(resid)
            }
        }
    }

    override fun getResources(): Resources = safeResources
}
