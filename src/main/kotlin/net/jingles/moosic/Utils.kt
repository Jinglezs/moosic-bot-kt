package net.jingles.moosic

import java.text.NumberFormat

fun Int.format() = "%,d".format(this)

fun Double.format(digits: Int) = "%,.${digits}f".format(this)

fun Float.toPercent(): String = NumberFormat.getPercentInstance().format(this.toDouble())