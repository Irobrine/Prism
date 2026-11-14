package com.prism.music.ui.screens

/**
 * [sortedBy], with each item's key made once.
 *
 * The standard one builds a comparator that asks for the key again on every comparison: sorting a Library of thousands
 * of songs by an artist's or an album's name made a lowercase copy of the same name at each of the ~n·log n steps
 * (tens of thousands of strings, on the main thread, each time a list was sorted). This makes one key per item and sorts
 * on those; the order is exactly the same (both sorts are stable, and the keys compare the same way).
 */
internal fun <T, K : Comparable<K>> List<T>.sortedByKey(key: (T) -> K): List<T> {
    if (size < 2) return this
    val keyed = ArrayList<Pair<T, K>>(size)
    for (item in this) keyed += item to key(item)
    keyed.sortWith { a, b -> a.second.compareTo(b.second) }
    return keyed.map { it.first }
}

/** [sortedByKey] by two keys in turn, each made once: the first, then the second where it ties. */
internal fun <T, K : Comparable<K>, L : Comparable<L>> List<T>.sortedByKey(first: (T) -> K, second: (T) -> L): List<T> {
    if (size < 2) return this
    val keyed = ArrayList<Triple<T, K, L>>(size)
    for (item in this) keyed += Triple(item, first(item), second(item))
    keyed.sortWith { a, b -> a.second.compareTo(b.second).let { if (it != 0) it else a.third.compareTo(b.third) } }
    return keyed.map { it.first }
}

/** [sortedByKey] by three keys in turn, each made once. */
internal fun <T, K : Comparable<K>, L : Comparable<L>, M : Comparable<M>> List<T>.sortedByKey(
    first: (T) -> K, second: (T) -> L, third: (T) -> M,
): List<T> {
    if (size < 2) return this
    val keyed = ArrayList<Keyed3<T, K, L, M>>(size)
    for (item in this) keyed += Keyed3(item, first(item), second(item), third(item))
    keyed.sortWith { x, y ->
        var c = x.a.compareTo(y.a)
        if (c == 0) c = x.b.compareTo(y.b)
        if (c == 0) c = x.c.compareTo(y.c)
        c
    }
    return keyed.map { it.item }
}

private class Keyed3<T, K, L, M>(val item: T, val a: K, val b: L, val c: M)
