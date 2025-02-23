package com.crosspaste.paste

abstract class PasteIDGeneratorFactory {

    abstract fun createIDGenerator(): PasteIDGenerator
}

abstract class PasteIDGenerator {

    abstract fun nextID(): Long
}
