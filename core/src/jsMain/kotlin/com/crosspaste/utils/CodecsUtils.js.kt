package com.crosspaste.utils

actual fun getCodecsUtils(): CodecsUtils = JsCodecsUtils

object JsCodecsUtils : CodecsUtils
