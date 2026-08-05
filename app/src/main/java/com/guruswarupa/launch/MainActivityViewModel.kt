package com.guruswarupa.launch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.guruswarupa.launch.managers.ScreenPagerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MainActivityUiState(
    val searchQuery: String = "",
    val currentPage: ScreenPagerManager.Page? = null,
    val deferredWidgetsInitialized: Boolean = false,
    val hasAskedDefaultLauncherThisOpen: Boolean = false,
)

class MainActivityViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val uiStateFlow = MutableStateFlow(
        MainActivityUiState(
            searchQuery = savedStateHandle[KEY_SEARCH_QUERY] ?: "",
            deferredWidgetsInitialized = savedStateHandle[KEY_DEFERRED_WIDGETS_INITIALIZED] ?: false,
            hasAskedDefaultLauncherThisOpen = savedStateHandle[KEY_DEFAULT_LAUNCHER_ASKED] ?: false
        )
    )

    val uiState: StateFlow<MainActivityUiState> = uiStateFlow.asStateFlow()

    fun updateSearchQuery(query: String) {
        updateState { it.copy(searchQuery = query) }
    }

    fun updateCurrentPage(page: ScreenPagerManager.Page) {
        updateState { it.copy(currentPage = page) }
    }

    fun markDeferredWidgetsInitialized() {
        updateState { it.copy(deferredWidgetsInitialized = true) }
    }

    fun markDefaultLauncherAsked() {
        updateState { it.copy(hasAskedDefaultLauncherThisOpen = true) }
    }

    private fun updateState(transform: (MainActivityUiState) -> MainActivityUiState) {
        val updated = transform(uiStateFlow.value)
        uiStateFlow.value = updated
        savedStateHandle[KEY_SEARCH_QUERY] = updated.searchQuery
        savedStateHandle[KEY_DEFERRED_WIDGETS_INITIALIZED] = updated.deferredWidgetsInitialized
        savedStateHandle[KEY_DEFAULT_LAUNCHER_ASKED] = updated.hasAskedDefaultLauncherThisOpen
    }

    companion object {
        private const val KEY_SEARCH_QUERY = "search_query"
        private const val KEY_DEFERRED_WIDGETS_INITIALIZED = "deferred_widgets_initialized"
        private const val KEY_DEFAULT_LAUNCHER_ASKED = "default_launcher_asked"
    }
}
