package com.guruswarupa.launch.di

import android.content.Context
import android.content.SharedPreferences
import com.guruswarupa.launch.models.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences = context.getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
}

@Module
@InstallIn(SingletonComponent::class)
object ExecutorsModule {
    @Provides
    @Singleton
    @BackgroundExecutor
    fun provideBackgroundExecutor(): ExecutorService = Executors.newFixedThreadPool(4)

    @Provides
    @Singleton
    @ResourceLoader
    fun provideResourceLoaderExecutor(): ExecutorService = Executors.newSingleThreadExecutor()
}
