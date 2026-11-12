package com.bk.mmovies.app

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

object AppCheckProviderFactoryProvider {

    fun create(): AppCheckProviderFactory {
        return PlayIntegrityAppCheckProviderFactory.getInstance()
    }
}
