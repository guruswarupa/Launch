package com.guruswarupa.launch.di

import android.content.SharedPreferences
import androidx.fragment.app.FragmentActivity
import com.guruswarupa.launch.AppLauncher
import com.guruswarupa.launch.ai.prediction.LaunchEventStore
import com.guruswarupa.launch.core.PermissionManager
import com.guruswarupa.launch.core.ShareManager
import com.guruswarupa.launch.core.SystemBarManager
import com.guruswarupa.launch.handlers.ActivityInitializer
import com.guruswarupa.launch.handlers.MainActivityResultRegistry
import com.guruswarupa.launch.managers.AppLockManager
import com.guruswarupa.launch.managers.AppTimerManager
import com.guruswarupa.launch.managers.AppUsageStatsManager
import com.guruswarupa.launch.widgets.WidgetLifecycleCoordinator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped

@Module
@InstallIn(ActivityComponent::class)
object ActivityManagersModule {
    @Provides
    @ActivityScoped
    fun provideShareManager(activity: FragmentActivity): ShareManager = ShareManager(activity)

    @Provides
    @ActivityScoped
    fun providePermissionManager(
        activity: FragmentActivity,
        sharedPreferences: SharedPreferences
    ): PermissionManager = PermissionManager(activity, sharedPreferences)

    @Provides
    @ActivityScoped
    fun provideSystemBarManager(
        activity: FragmentActivity,
        sharedPreferences: SharedPreferences
    ): SystemBarManager = SystemBarManager(activity, sharedPreferences)

    @Provides
    @ActivityScoped
    fun provideAppLockManager(activity: FragmentActivity): AppLockManager = AppLockManager(activity)

    @Provides
    @ActivityScoped
    fun provideAppTimerManager(activity: FragmentActivity): AppTimerManager = AppTimerManager(activity)

    @Provides
    @ActivityScoped
    fun provideAppUsageStatsManager(activity: FragmentActivity): AppUsageStatsManager = AppUsageStatsManager(activity)

    @Provides
    @ActivityScoped
    fun provideAppLauncher(
        activity: FragmentActivity,
        appLockManager: AppLockManager,
        launchEventStore: LaunchEventStore
    ): AppLauncher = AppLauncher(activity, activity.packageManager, appLockManager, launchEventStore)

    @Provides
    @ActivityScoped
    fun provideActivityInitializer(
        activity: FragmentActivity,
        sharedPreferences: SharedPreferences,
        appLauncher: AppLauncher
    ): ActivityInitializer = ActivityInitializer(activity, sharedPreferences, appLauncher)

    @Provides
    @ActivityScoped
    fun provideResultRegistry(activity: FragmentActivity): MainActivityResultRegistry =
        MainActivityResultRegistry(activity)

    @Provides
    @ActivityScoped
    fun provideWidgetLifecycleCoordinator(): WidgetLifecycleCoordinator = WidgetLifecycleCoordinator()
}
