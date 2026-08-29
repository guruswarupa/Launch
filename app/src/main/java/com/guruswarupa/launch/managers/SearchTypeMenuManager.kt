package com.guruswarupa.launch.managers

import android.content.Context
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import com.guruswarupa.launch.R





class SearchTypeMenuManager(
    private val context: Context,
    private val searchTypeButton: ImageButton,
    private val appSearchManagerProvider: () -> AppSearchManager?
) {




    fun setup() {
        searchTypeButton.setOnClickListener { view ->
            showSearchTypeMenu(view)
        }
    }




    private fun showSearchTypeMenu(anchor: View) {

        val popup = createTranslucentPopupMenu(anchor)
        popup.menu.add(0, 0, 0, context.getString(R.string.search_mode_all))
        popup.menu.add(0, 1, 1, context.getString(R.string.search_mode_apps))
        popup.menu.add(0, 2, 2, context.getString(R.string.search_mode_contacts))
        popup.menu.add(0, 3, 3, context.getString(R.string.search_mode_files))

        popup.setOnMenuItemClickListener { item ->
            val mode = when (item.itemId) {
                0 -> AppSearchManager.SearchMode.ALL
                1 -> AppSearchManager.SearchMode.APPS
                2 -> AppSearchManager.SearchMode.CONTACTS
                3 -> AppSearchManager.SearchMode.FILES
                else -> AppSearchManager.SearchMode.ALL
            }

            val appSearchManager = appSearchManagerProvider()
            if (appSearchManager != null) {
                appSearchManager.setSearchMode(mode)
            }


            updateButtonIcon(mode)

            true
        }
        popup.show()
        applyThemeColorToPopupMenu(popup)
    }




    private fun updateButtonIcon(mode: AppSearchManager.SearchMode) {
        val iconRes = when (mode) {
            AppSearchManager.SearchMode.APPS -> R.drawable.ic_apps_grid_icon
            AppSearchManager.SearchMode.CONTACTS -> R.drawable.ic_person
            AppSearchManager.SearchMode.FILES -> R.drawable.ic_file
            AppSearchManager.SearchMode.MAPS -> R.drawable.ic_maps
            AppSearchManager.SearchMode.WEB -> R.drawable.ic_browser
            AppSearchManager.SearchMode.PLAYSTORE -> R.drawable.ic_play_store
            AppSearchManager.SearchMode.YOUTUBE -> R.drawable.ic_youtube
            else -> R.drawable.ic_search
        }
        searchTypeButton.setImageResource(iconRes)
    }




    private fun createTranslucentPopupMenu(anchor: View): PopupMenu {

        val wrapper = ContextThemeWrapper(context, R.style.Theme_Launch)
        val popup = PopupMenu(wrapper, anchor)


        try {

            val popupField = popup.javaClass.getDeclaredField("mPopup")
            popupField.isAccessible = true
            val menuPopupHelper = popupField.get(popup)
            val cls = menuPopupHelper.javaClass


            val backgroundDrawable = ContextCompat.getDrawable(context, R.drawable.menu_background)


            val methodsToTry = listOf(
                "setBackgroundDrawable" to arrayOf(android.graphics.drawable.Drawable::class.java),
                "setDropDownBackgroundDrawable" to arrayOf(android.graphics.drawable.Drawable::class.java)
            )

            var backgroundSet = false
            for ((methodName, paramTypes) in methodsToTry) {
                try {
                    val method = cls.getMethod(methodName, *paramTypes)
                    method.invoke(menuPopupHelper, backgroundDrawable)
                    backgroundSet = true
                    break
                } catch (_: Exception) {

                }
            }


            if (!backgroundSet) {
                try {
                    val popupWindowField = cls.getDeclaredField("mPopup")
                    popupWindowField.isAccessible = true
                    val popupWindow = popupWindowField.get(menuPopupHelper)
                    val popupWindowClass = popupWindow?.javaClass

                    popupWindowClass?.getMethod("setBackgroundDrawable", android.graphics.drawable.Drawable::class.java)
                        ?.invoke(popupWindow, backgroundDrawable)
                } catch (_: Exception) {}
            }


            try {
                cls.getMethod("setPopupStyle", Int::class.java)
                    .invoke(menuPopupHelper, R.style.PopupMenuStyle)
            } catch (_: Exception) {}

        } catch (_: Exception) {


        }

        return popup
    }




    private fun applyThemeColorToPopupMenu(popup: PopupMenu) {
        try {
            val themeColor = TypographyManager.getConfiguredFontColor(context) ?: ContextCompat.getColor(context, R.color.white)
            val popupField = popup.javaClass.getDeclaredField("mPopup")
            popupField.isAccessible = true
            val menuPopupHelper = popupField.get(popup)
            val cls = menuPopupHelper.javaClass


            val listViewFieldNames = arrayOf("mDropDownList", "mPopup", "mListView")
            var listView: android.widget.ListView? = null

            for (fieldName in listViewFieldNames) {
                try {
                    val listViewField = cls.getDeclaredField(fieldName)
                    listViewField.isAccessible = true
                    val result = listViewField.get(menuPopupHelper)
                    if (result is android.widget.ListView) {
                        listView = result
                        break
                    }
                } catch (_: NoSuchFieldException) {}
            }


            listView?.let { lv ->

                for (i in 0 until lv.childCount) {
                    val itemView = lv.getChildAt(i)
                    if (itemView is android.widget.TextView) {
                        itemView.setTextColor(themeColor)
                    }
                }

                lv.post {
                    for (i in 0 until lv.childCount) {
                        val itemView = lv.getChildAt(i)
                        if (itemView is android.widget.TextView) {
                            itemView.setTextColor(themeColor)
                        } else if (itemView is android.view.ViewGroup) {
                            findTextViewsAndSetColor(itemView, themeColor)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun findTextViewsAndSetColor(viewGroup: android.view.ViewGroup, color: Int) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is android.widget.TextView) {
                child.setTextColor(color)
            } else if (child is android.view.ViewGroup) {
                findTextViewsAndSetColor(child, color)
            }
        }
    }
}
