package com.oxarena

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. `@HiltAndroidApp` triggers Hilt's code generation and
 * creates the singleton dependency graph that lives for the app's lifetime.
 */
@HiltAndroidApp
class OxArenaApp : Application()
