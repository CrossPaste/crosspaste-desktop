package com.crosspaste.ui.base

interface DialogService {

    var dialogs: MutableList<PasteDialog>

    fun pushDialog(dialog: PasteDialog)

    fun popDialog()
}
