package com.runline.app.core

import kotlin.random.Random

/** Shuffle bags vary phrasing without repeating a line until its alternatives are used. */
class PhraseRotation(seed: Int = Random.nextInt()) {
    private val random = Random(seed)
    private val bags = mutableMapOf<String, MutableList<Int>>()
    private val previous = mutableMapOf<String, Int>()
    fun choose(key: String, phrases: List<String>): String {
        require(phrases.isNotEmpty())
        var bag = bags.getOrPut(key) { mutableListOf() }
        if (bag.isEmpty() || bag.any { it !in phrases.indices }) {
            bag = phrases.indices.shuffled(random).toMutableList()
            if (bag.size > 1 && bag.last() == previous[key]) {
                val first = bag[0]; bag[0] = bag.last(); bag[bag.lastIndex] = first
            }
            bags[key] = bag
        }
        val index = bag.removeAt(bag.lastIndex)
        previous[key] = index
        return phrases[index]
    }
}
