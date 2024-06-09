package com.shio.saikyo.util

import kotlin.math.abs

fun Float.isApprox(value: Float, eps: Float = 0.01f): Boolean {
    return abs(this - value) < eps
}

fun Int.isDivisible(divisor: Int): Boolean {
    return this.rem(divisor) == 0
}

fun Float.isDivisible(divisor: Int): Boolean {
    return this.rem(divisor) == 0f
}