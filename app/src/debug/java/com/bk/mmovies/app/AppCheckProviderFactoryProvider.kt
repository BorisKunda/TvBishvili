package com.bk.mmovies.app

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

object AppCheckProviderFactoryProvider {

    fun create(): AppCheckProviderFactory {
        return DebugAppCheckProviderFactory.getInstance()
    }
}
