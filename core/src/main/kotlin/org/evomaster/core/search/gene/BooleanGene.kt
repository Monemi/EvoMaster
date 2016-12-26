package org.evomaster.core.search.gene

import org.evomaster.core.search.service.Randomness


class BooleanGene(name: String, var value: Boolean = true) : Gene(name){

    override fun copy(): Gene {
        return BooleanGene(name, value)
    }

    override fun randomize(randomness: Randomness, forceNewValue: Boolean) {

        val k: Boolean = if (forceNewValue) {
            ! value
        } else {
            randomness.nextBoolean()
        }

        value = k
    }
}