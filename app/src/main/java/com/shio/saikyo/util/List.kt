package com.shio.saikyo.util

fun <T> List<T>.forEach(from: Int, to: Int = this.size, action: (T) -> Unit) {
    for (i in from until to) {
        action(get(i))
    }
}

fun <T, R> List<T>.map(from: Int, to: Int = this.size, action: (T) -> R): List<R> {
    val res = mutableListOf<R>()

    for (i in from until to) {
        res.add(action(this[i]))
    }

    return res
}

fun <T> List<T>.forEachIndexed(from: Int, to: Int = this.size, action: (Int, T) -> Unit) {
    for (i in from until to) {
        action(i, get(i))
    }
}

fun <T> List<T>.forEachJoin(from: Int = 0, to: Int = this.size, onJoin: (T, T) -> Unit, onLoop: (T) -> Unit) {
    onLoop(this[from])
    for (i in from + 1 until to) {
        onJoin(this[i - 1], this[i])
        onLoop(this[i])
    }
}

// filter and transform a list of elements all in one step.
// action should return either the transformed element, or null if it should be filtered
fun <T, R> List<T>.filterMap(fromIx: Int = 0, untilIx: Int = this.size, action: (Int, T) -> R?): List<R> {
    val ret = mutableListOf<R>()
    for (i in fromIx until untilIx) {
        val res = action(i, this[i])
        if (res != null) {
            ret.add(res)
        }
    }

    return ret
}