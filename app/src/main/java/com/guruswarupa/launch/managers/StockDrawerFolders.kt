package com.guruswarupa.launch.managers

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.AppAdapter
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.utils.DialogStyler
import com.guruswarupa.launch.utils.LayoutMode

internal fun StockDrawerManager.performMerge(
    adapter: AppAdapter,
    orderKey: String,
    foldersKey: String,
    draggedPosition: Int,
    targetPosition: Int
) {
    val list = adapter.currentList
    val draggedApp = list.getOrNull(draggedPosition) ?: return
    val targetApp = list.getOrNull(targetPosition) ?: return
    val draggedKey = appOrderManager.keyOf(draggedApp)

    if (appOrderManager.isFolderEntry(targetApp)) {
        val folderId = appOrderManager.folderIdOf(targetApp)
        folderManager.addAppToFolder(foldersKey, folderId, draggedKey)
        val refreshedFolder = folderManager.findFolder(foldersKey, folderId)
        val updated = list.toMutableList()
        updated.removeAt(draggedPosition)
        if (refreshedFolder != null) {

            val newTargetIndex = (if (draggedPosition < targetPosition) targetPosition - 1 else targetPosition)
                .coerceIn(updated.indices)
            updated[newTargetIndex] = appOrderManager.createFolderInfo(refreshedFolder)
        }
        adapter.updateAppList(updated)
        appOrderManager.saveOrder(updated, orderKey)
    } else {
        val targetKey = appOrderManager.keyOf(targetApp)
        val defaultName = activity.getString(R.string.stock_folder_default_name)
        val folder = folderManager.createFolder(foldersKey, defaultName, listOf(targetKey, draggedKey))
        val folderInfo = appOrderManager.createFolderInfo(folder)
        val updated = list.toMutableList()
        updated.removeAt(draggedPosition)
        val adjustedTargetIndex = if (draggedPosition < targetPosition) targetPosition - 1 else targetPosition
        if (adjustedTargetIndex in updated.indices) {
            updated[adjustedTargetIndex] = folderInfo
        } else {
            updated.add(folderInfo)
        }
        adapter.updateAppList(updated)
        appOrderManager.saveOrder(updated, orderKey)

        showFolderContents(
            orderKey = orderKey,
            foldersKey = foldersKey,
            folderId = folder.id,
            refresh = { onOrderChangedForFolder(orderKey, foldersKey) },
            focusNameForEditing = true
        )
    }
}

internal fun StockDrawerManager.onOrderChangedForFolder(orderKey: String, foldersKey: String) {
    if (orderKey == com.guruswarupa.launch.models.Constants.Prefs.STOCK_DRAWER_APP_ORDER) {
        recomputeOrderedApps(activity.fullAppList)
        applyCurrentFilter()
    } else {
        activity.appListLoader.loadApps(forceRefresh = false)
    }
}

internal fun StockDrawerManager.showFolderContents(
    orderKey: String,
    foldersKey: String,
    folderId: String,
    refresh: () -> Unit,
    focusNameForEditing: Boolean = false
) {
    val folder = folderManager.findFolder(foldersKey, folderId) ?: return
    val appsInFolder = resolveFolderApps(foldersKey, folderId)
    if (appsInFolder.isEmpty()) return

    val dialogView = activity.layoutInflater.inflate(R.layout.dialog_stock_folder_contents, null)
    val nameInput = dialogView.findViewById<EditText>(R.id.folder_name_input)
    val contentsRecyclerView = dialogView.findViewById<RecyclerView>(R.id.folder_contents_recycler_view)
    val deleteButton = dialogView.findViewById<View>(R.id.folder_delete_button)

    DialogStyler.styleInput(activity, nameInput)
    nameInput.background = null
    nameInput.setText(folder.name)
    nameInput.hint = activity.getString(R.string.stock_folder_name_hint)
    var lastSavedName = folder.name

    fun commitNameIfChanged() {
        val newName = nameInput.text.toString().trim()
            .ifEmpty { activity.getString(R.string.stock_folder_default_name) }
        if (newName != lastSavedName) {
            folderManager.renameFolder(foldersKey, folder.id, newName)
            lastSavedName = newName
            refresh()
        }
    }

    nameInput.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) commitNameIfChanged() }
    nameInput.setOnEditorActionListener { _, actionId, _ ->
        if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
            commitNameIfChanged()
            hideKeyboard(nameInput)
            nameInput.clearFocus()
            true
        } else {
            false
        }
    }

    contentsRecyclerView.layoutManager = GridLayoutManager(activity, activity.getPreferredGridColumns().coerceAtMost(4))
    val contentsAdapter = AppAdapter(activity, appsInFolder.toMutableList(), searchBox, true, activity, sharedPreferences)
    contentsRecyclerView.adapter = contentsAdapter

    lateinit var dialog: AlertDialog

    val removeZone = dialogView.findViewById<View>(R.id.folder_remove_zone)
    val addToHomeZone = dialogView.findViewById<View>(R.id.folder_add_to_home_zone)

    val showAddToHome = foldersKey == com.guruswarupa.launch.models.Constants.Prefs.STOCK_DRAWER_FOLDERS

    val dragHelper = attachFolderContentsDrag(
        contentsRecyclerView = contentsRecyclerView,
        contentsAdapter = contentsAdapter,
        foldersKey = foldersKey,
        folderId = folder.id,
        removeZone = removeZone,
        addToHomeZone = if (showAddToHome) addToHomeZone else null
    ) { folderStillExists ->
        if (!folderStillExists) {
            dialog.dismiss()
        }
        refresh()
    }
    contentsAdapter.onItemLongPress = { holder ->
        dragHelper.startDrag(holder)
        true
    }
    contentsAdapter.onAppClicked = {
        if (dialog.isShowing) dialog.dismiss()
    }

    deleteButton.setOnClickListener {
        AlertDialog.Builder(activity, R.style.CustomDialogTheme)
            .setTitle(activity.getString(R.string.stock_folder_delete_confirm_title))
            .setMessage(activity.getString(R.string.stock_folder_delete_confirm_message, lastSavedName))
            .setPositiveButton(activity.getString(R.string.delete_button)) { _, _ ->
                folderManager.deleteFolder(foldersKey, folder.id)
                dialog.dismiss()
                refresh()
            }
            .setNegativeButton(activity.getString(R.string.cancel_button), null)
            .show()
    }

    dialog = AlertDialog.Builder(activity, R.style.CustomDialogTheme)
        .setView(dialogView)
        .create()
    dialog.setOnDismissListener { commitNameIfChanged() }
    dialog.show()
    dialog.window?.setLayout(
        (activity.resources.displayMetrics.widthPixels * 0.92f).toInt(),
        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
    )

    if (focusNameForEditing) {
        nameInput.requestFocus()
        nameInput.selectAll()
        showKeyboard(nameInput)
    }
}

internal fun StockDrawerManager.showKeyboard(view: View) {
    view.post {
        val imm = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }
}

internal fun StockDrawerManager.hideKeyboard(view: View) {
    val imm = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
    imm?.hideSoftInputFromWindow(view.windowToken, 0)
}

internal fun StockDrawerManager.attachFolderContentsDrag(
    contentsRecyclerView: RecyclerView,
    contentsAdapter: AppAdapter,
    foldersKey: String,
    folderId: String,
    removeZone: View,
    addToHomeZone: View?,
    onChanged: (folderStillExists: Boolean) -> Unit
): ItemTouchHelper {
    val zones = listOfNotNull(removeZone, addToHomeZone)
    var hoveredZone: View? = null

    fun setZoneHover(zone: View?) {
        if (hoveredZone === zone) return
        hoveredZone?.let {
            it.alpha = 0.65f
            it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
        }
        hoveredZone = zone
        zone?.let {
            it.alpha = 1f
            it.animate().scaleX(1.15f).scaleY(1.15f).setDuration(100).start()
        }
    }

    val callback = object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
        0
    ) {
        override fun isLongPressDragEnabled(): Boolean = false

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean = false

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                setZoneHover(null)
                zones.forEach { zone ->
                    zone.animate().cancel()
                    zone.isVisible = true
                    zone.scaleX = 0.7f
                    zone.scaleY = 0.7f
                    zone.alpha = 0f
                    zone.animate().scaleX(1f).scaleY(1f).alpha(0.65f).setDuration(150).start()
                }
                viewHolder?.itemView?.apply {
                    alpha = 0.85f
                    scaleX = 1.08f
                    scaleY = 1.08f
                }
            }
        }

        override fun onChildDraw(
            c: android.graphics.Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean
        ) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && isCurrentlyActive) {
                val zoneUnderDrag = zones.firstOrNull { isDraggedOverZone(recyclerView, viewHolder, dX, dY, it) }
                setZoneHover(zoneUnderDrag)
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            viewHolder.itemView.apply {
                alpha = 1f
                scaleX = 1f
                scaleY = 1f
            }
            val droppedOnZone = hoveredZone
            setZoneHover(null)
            zones.forEach { zone ->
                zone.animate().cancel()
                zone.isVisible = false
                zone.scaleX = 1f
                zone.scaleY = 1f
            }

            val position = viewHolder.bindingAdapterPosition
            val app = if (position != RecyclerView.NO_POSITION) contentsAdapter.currentList.getOrNull(position) else null
            if (droppedOnZone == null || app == null) {
                if (droppedOnZone == null && position != RecyclerView.NO_POSITION) {
                    (viewHolder as? AppAdapter.ViewHolder)?.let { contentsAdapter.showContextMenuForHolder(it) }
                }
                return
            }

            val key = appOrderManager.keyOf(app)
            folderManager.removeAppFromFolder(foldersKey, folderId, key)
            if (droppedOnZone === addToHomeZone) {
                favoriteAppManager.addFavoriteApp(app.activityInfo.packageName)
            }

            val updated = contentsAdapter.currentList.toMutableList()
            updated.removeAt(position)
            contentsAdapter.updateAppList(updated)

            val folderStillExists = folderManager.findFolder(foldersKey, folderId) != null
            onChanged(folderStillExists)
        }
    }

    val helper = ItemTouchHelper(callback)
    helper.attachToRecyclerView(contentsRecyclerView)
    return helper
}

internal fun StockDrawerManager.resolveFolderApps(foldersKey: String, folderId: String): List<ResolveInfo> {
    val folder = folderManager.findFolder(foldersKey, folderId) ?: return emptyList()
    return folder.appKeys.mapNotNull { key ->
        activity.fullAppList.find { appOrderManager.keyOf(it) == key }
    }.filter { !appDockManager.isAppHiddenInFocusMode(it.activityInfo.packageName) }
}
