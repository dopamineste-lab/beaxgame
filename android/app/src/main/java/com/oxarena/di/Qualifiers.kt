package com.oxarena.di

import javax.inject.Qualifier

/** Injects the backend base URL (from BuildConfig.BACKEND_URL). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BackendUrl
