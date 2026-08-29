package com.guruswarupa.launch.models

import android.annotation.SuppressLint




object Constants {

    @SuppressLint("unused")
    object Prefs {
        const val PREFS_NAME = "com.guruswarupa.launch.PREFS"
        const val SELECTED_THEME = "selected_theme"
        const val VIEW_PREFERENCE = "view_preference"
        const val VIEW_PREFERENCE_LIST = "list"
        const val VIEW_PREFERENCE_GRID = "grid"
        const val SHAKE_TORCH_ENABLED = "shake_torch_enabled"
        const val SHAKE_SENSITIVITY = "shake_sensitivity"
        const val SCREEN_DIMMER_ENABLED = "screen_dimmer_enabled"
        const val SCREEN_DIMMER_LEVEL = "screen_dimmer_level"
        const val NIGHT_MODE_ENABLED = "night_mode_enabled"
        const val NIGHT_MODE_INTENSITY = "night_mode_intensity"
        const val FLIP_DND_ENABLED = "flip_dnd_enabled"
        const val BACK_TAP_ENABLED = "back_tap_enabled"
        const val BACK_TAP_ACTION = "back_tap_action"
        const val BACK_TAP_SENSITIVITY = "back_tap_sensitivity"
        const val BACK_TAP_MULTIPLIER = "back_tap_multiplier"
        const val BACK_TAP_DOUBLE_ACTION = "back_tap_double_action"
        const val WALK_DETECT_ENABLED = "walk_detect_enabled"
        const val WALK_DETECT_ACTION = "walk_detect_action"
        const val WALK_DETECT_STEP_THRESHOLD = "walk_detect_step_threshold"
        const val WALK_DETECT_TIME_WINDOW_SECONDS = "walk_detect_time_window_seconds"
        const val WALK_DETECT_COOLDOWN_MINUTES = "walk_detect_cooldown_minutes"
        const val WALK_DETECT_CUSTOM_APP_PACKAGE = "walk_detect_custom_app_package"
        const val WALLPAPER_BLUR_LEVEL = "wallpaper_blur_level"
        const val OPAQUE_SURFACES_ENABLED = "opaque_surfaces_enabled"
        const val SEARCH_ENGINE = "search_engine"
        const val ACCESSIBILITY_SHORTCUT_ENABLED = "accessibility_shortcut_enabled"
        const val CONTROL_CENTER_SHORTCUTS = "control_center_shortcuts"
        const val CONTROL_CENTER_TRIGGER_ENABLED = "control_center_trigger_enabled"
        const val CONTROL_CENTER_TRIGGER_SIDE = "control_center_trigger_side"
        const val CONTROL_CENTER_TRIGGER_Y = "control_center_trigger_y"
        const val CONTROL_CENTER_TRIGGER_LOCKED = "control_center_trigger_locked"
        const val CONTROL_CENTER_TRIGGER_ALPHA = "control_center_trigger_alpha"
        const val CONTROL_CENTER_TRIGGER_HEIGHT_DP = "control_center_trigger_height_dp"
        const val CONTROL_CENTER_TRIGGER_WIDTH_DP = "control_center_trigger_width_dp"
        const val EDGE_PANEL_ENABLED = "edge_panel_enabled"
        const val EDGE_PANEL_PINNED_APPS = "edge_panel_pinned_apps"
        const val EDGE_PANEL_HANDLE_SIDE = "edge_panel_handle_side"
        const val EDGE_PANEL_HANDLE_Y = "edge_panel_handle_y"
        const val EDGE_PANEL_HANDLE_LOCKED = "edge_panel_handle_locked"
        const val EDGE_PANEL_SHOW_RECENT = "edge_panel_show_recent"
        const val EDGE_PANEL_HANDLE_ALPHA = "edge_panel_handle_alpha"
        const val EDGE_PANEL_HANDLE_HEIGHT_DP = "edge_panel_handle_height_dp"
        const val EDGE_PANEL_HANDLE_WIDTH_DP = "edge_panel_handle_width_dp"
        const val TYPOGRAPHY_SCALE_PERCENT = "typography_scale_percent"
        const val TYPOGRAPHY_FONT_STYLE = "typography_font_style"
        const val TYPOGRAPHY_FONT_INTENSITY = "typography_font_intensity"
        const val TYPOGRAPHY_FONT_COLOR = "typography_font_color"
        const val CLOCK_24_HOUR_FORMAT = "clock_24_hour_format"
        const val LANDSCAPE_ORIENTATION_ENABLED = "landscape_orientation_enabled"
        const val SHOW_FAST_SCROLLER = "show_fast_scroller"
        const val GRID_COLUMNS = "grid_columns"
        const val SHOW_APP_NAME_IN_GRID = "show_app_name_in_grid"
        const val HIDE_APP_ICON_IN_LIST = "hide_app_icon_in_list"
        const val ICON_STYLE = "icon_style"
        const val ICON_SIZE = "icon_size"
        const val ICON_PACK_PACKAGE = "icon_pack_package"
        const val ICON_PACK_ENABLED = "icon_pack_enabled"
        const val BACKGROUND_TRANSLUCENCY = "background_translucency"
        const val DEFAULT_HOME_PAGE_INDEX = "default_home_page_index"
        const val DEFAULT_HOME_PAGE_TARGET = "default_home_page_target"
        const val WEB_APPS = "web_apps"
        const val GRAYSCALE_MODE_ENABLED = "grayscale_mode_enabled"
        const val RSS_PAGE_ENABLED = "rss_page_enabled"
        const val WIDGETS_PAGE_ENABLED = "widgets_page_enabled"
        const val TOP_WIDGET_ENABLED = "top_widget_enabled"
        const val RSS_FEED_URLS = "rss_feed_urls"
        const val RSS_CUSTOM_FEED_URLS = "rss_custom_feed_urls"
        const val RSS_FEED_CACHE = "rss_feed_cache"
        const val RSS_FEED_CACHE_TIME = "rss_feed_cache_time"
        const val RSS_REFRESH_INTERVAL_MINUTES = "rss_refresh_interval_minutes"
        const val REVIEW_FIRST_USE_AT = "review_first_use_at"
        const val REVIEW_NEXT_PROMPT_AT = "review_next_prompt_at"
        const val REVIEW_PROMPT_COUNT = "review_prompt_count"
        const val REVIEW_CTA_USED = "review_cta_used"
        const val DONATION_PROMPT_SHOWN = "donation_prompt_shown"
        const val SUPPORTER_BADGE_EARNED = "supporter_badge_earned"
        const val APP_DATA_CONSENT_GIVEN = "app_data_consent_given"
        const val INITIAL_PERMISSIONS_ASKED = "initial_permissions_asked"
        const val WAITING_FOR_USAGE_STATS_RETURN = "waiting_for_usage_stats_return"
        const val CONTACTS_PERMISSION_DENIED = "contacts_permission_denied"
        const val USAGE_STATS_PERMISSION_DENIED = "usage_stats_permission_denied"
        const val POWER_SAVER_MODE = "power_saver_mode"
        const val MINIMAL_MODE_ENABLED = "minimal_mode_enabled"


        const val VAULT_TIMEOUT_ENABLED = "vault_timeout_enabled"
        const val VAULT_TIMEOUT_DURATION = "vault_timeout_duration"
        const val VAULT_SETUP_COMPLETE = "vault_setup_complete"


        const val FOCUS_MODE_TYPE = "focus_mode_type"
        const val FOCUS_MODE_TYPE_STRICT = "strict"
        const val FOCUS_MODE_TYPE_CASUAL = "casual"


        const val DOCK_HIDE_WORK_PROFILE = "dock_hide_work_profile"
        const val DOCK_HIDE_FOCUS_MODE = "dock_hide_focus_mode"
        const val DOCK_HIDE_WORKSPACES = "dock_hide_workspaces"

    }

    object RequestCodes {
        const val ACTIVITY_RECOGNITION_PERMISSION = 105
    }

    const val TYPOGRAPHY_FONT_COLOR_DEFAULT = "#FFFFFF"


    const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"


    const val SHARED_APKS_DIR = "shared_apks"


    const val APK_EXTENSION = ".apk"


    const val MIME_TYPE_APK = "application/vnd.android.package-archive"
    const val MIME_TYPE_ALL = "*/*"

    const val APP_NAME_SANITIZE_REGEX = "[^a-zA-Z0-9.-]"
    const val APP_NAME_SANITIZE_REPLACEMENT = "_"

    object Dimensions {
        const val DOCK_ICON_SIZE_DP = 20
        const val FOCUS_TIMER_TEXT_SIZE_SP = 13f
        const val ICON_CACHE_MAX_SIZE = 100
        const val ICON_PRELOAD_BATCH_SIZE = 20
        const val ICON_IMMEDIATE_LOAD_COUNT = 30
        const val PENDING_TASKS_CLEANUP_THRESHOLD = 100
        const val MARGIN_START_DP = 12
    }

    object Timeouts {
        const val APP_LIST_CACHE_DURATION_MS = 300000L // 5 minutes
        const val WORK_PROFILE_EMPTY_RETRY_DELAY_MS = 350L
        const val MAX_WORK_PROFILE_EMPTY_RETRIES = 2
        const val WORK_PROFILE_RETRY_RESET_TIMEOUT_MS = 30000L // 30 seconds
        const val GENERAL_EMPTY_RETRY_DELAY_MS = 500L
        const val MAX_GENERAL_EMPTY_RETRIES = 3
        const val ICON_PRELOAD_DELAY_MS = 50L
        const val APP_LIST_REFRESH_INTERVAL_MS = 2 * 60 * 1000L // 2 minutes
        const val USAGE_REFRESH_INTERVAL_MS = 20 * 1000L // 20 seconds
    }

    object Limits {
        const val MAX_VISIBLE_PRELOAD_ITEMS = 8
        const val FLING_DISTANCE_THRESHOLD = 50
        const val FLING_VELOCITY_THRESHOLD = 100
    }
}
