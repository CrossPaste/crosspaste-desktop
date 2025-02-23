package com.crosspaste.config

class TestReadWritePort : ReadWriteConfig<Int> {

    var port: Int = 0

    override fun getValue(): Int {
        return port
    }

    override fun setValue(value: Int) {
        port = value
    }
}
