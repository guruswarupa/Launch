package com.guruswarupa.launch

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import java.lang.ref.WeakReference
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.content.edit
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.guruswarupa.launch.ai.llm.OnDeviceAssistant
import com.guruswarupa.launch.ai.prediction.LaunchEventStore
import com.guruswarupa.launch.ai.prediction.SuggestionEngine
import com.guruswarupa.launch.di.BackgroundExecutor
import com.guruswarupa.launch.di.ResourceLoader
import com.guruswarupa.launch.core.*
import com.guruswarupa.launch.managers.*
import com.guruswarupa.launch.handlers.*
import com.guruswarupa.launch.models.MainActivityViews
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.ui.RssFeedPage
import com.guruswarupa.launch.widgets.*
import com.guruswarupa.launch.utils.*
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ExecutorService
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun MainActivity.initializeDeferredWidgets(staggered: Boolean = false) {
    if (!sharedPreferences.getBoolean(Constants.Prefs.WIDGETS_PAGE_ENABLED, true)) {
        return
    }

    if (deferredWidgetsInitialized && deferredWidgetsReadyForThisInstance) {

        widgetVisibilityManager.update(
            if (widgetLifecycleCoordinator.isYearProgressWidgetInitialized()) widgetLifecycleCoordinator.yearProgressWidget else null,
            if (widgetLifecycleCoordinator.isGithubContributionWidgetInitialized()) widgetLifecycleCoordinator.githubContributionWidget else null
        )
        return
    }

    pendingDeferredInitializer?.let {
        it.finishNow()
        return
    }

    val initializer = DeferredWidgetInitializer(
        widgetSetupManager = widgetSetupManager,
        sharedPreferences = sharedPreferences,
        lifecycleManager = lifecycleManager,
        widgetLifecycleCoordinator = widgetLifecycleCoordinator,
        onComplete = {
            initializeFinanceWidgetIfNeeded()
            todoAlarmManager = TodoAlarmManager(this)
            todoManager = TodoManager(this, sharedPreferences, views.todoRecyclerView, views.addTodoButton, todoAlarmManager)
            todoManager.initialize()
            updateLifecycleManagerWithDeferredWidgets()

            TypographyManager.applyToActivity(this)

            widgetVisibilityManager.update(
                if (widgetLifecycleCoordinator.isYearProgressWidgetInitialized()) widgetLifecycleCoordinator.yearProgressWidget else null,
                if (widgetLifecycleCoordinator.isGithubContributionWidgetInitialized()) widgetLifecycleCoordinator.githubContributionWidget else null
            )
        },
        onFinished = {
            pendingDeferredInitializer = null
            viewModel.markDeferredWidgetsInitialized()
            deferredWidgetsReadyForThisInstance = true
            updateRegistryDependencies()
        }
    )

    if (staggered) {
        pendingDeferredInitializer = initializer
        initializer.startStaggered { !isFinishing && !isDestroyed }
    } else {
        initializer.finishNow()
    }
}

internal fun MainActivity.scheduleDeferredWidgetPrewarm() {
    if (widgetPrewarmScheduled ||
        deferredWidgetsReadyForThisInstance ||
        !sharedPreferences.getBoolean(Constants.Prefs.WIDGETS_PAGE_ENABLED, true)
    ) {
        return
    }

    widgetPrewarmScheduled = true
    lifecycleScope.launch {
        try {
            delay(1200L)
            widgetPrewarmScheduled = false
            if (!isFinishing && !isDestroyed && !deferredWidgetsReadyForThisInstance) {
                initializeDeferredWidgets(staggered = true)
            }
        } finally {
            widgetPrewarmScheduled = false
        }
    }
}

internal fun MainActivity.initializeFinanceWidgetIfNeeded() {
    FinanceWidgetInitializer(this, secureStorageManager, 0)
        .onInitialized { manager ->
            financeWidgetManager = manager
        }
        .initialize(handler)
}

internal fun MainActivity.updateLifecycleManagerWithDeferredWidgets() {
    lifecycleManager.updateDependencies {
        copy(
            todoManager = todoManager
        )
    }
}
