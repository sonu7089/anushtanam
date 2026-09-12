package com.mantrasiddhi.di

import javax.inject.Qualifier

/** Process lifetime for identity resolution and shared account queries. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AccountScope
