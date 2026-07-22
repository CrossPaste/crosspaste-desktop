package com.crosspaste.utils

actual fun getCodecsUtils(): CodecsUtils = NativeCoreCodecsUtils

object NativeCoreCodecsUtils : CodecsUtils
