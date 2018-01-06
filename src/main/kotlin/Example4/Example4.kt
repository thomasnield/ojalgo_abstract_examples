package Example4

import org.ojalgo.optimisation.ExpressionsBasedModel
import org.ojalgo.optimisation.Variable
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger

// declare ojAlgo Model
val model = ExpressionsBasedModel()

// custom DSL for expression inputs, eliminate naming and adding
val funcId = AtomicInteger(0)
val variableId = AtomicInteger(0)
fun variable() = Variable(variableId.incrementAndGet().toString().let { "Variable$it" }).apply(model::addVariable)
fun addExpression() = funcId.incrementAndGet().let { "Func$it"}.let { model.addExpression(it) }


val letterCount = 20
val numberCount = 100
val maxContiguousBlocks = 2

fun main(args: Array<String>) {

    Letter.all.forEach { it.addConstraints() }
    Number.all.forEach { it.addConstraints() }

    model.countVariables().run { println("$this variables") }

    model.options.run {
        iterations_suffice = 1
        mip_gap = 0.0
    }

    model.minimise().run(::println)

    Letter.all.joinToString(prefix = "   ", separator = "   ").run(::println)

    Number.all.forEach { n ->
        Letter.all.asSequence().map { l -> l.slots.first { it.number == n }.occupied.value.toInt() }
                .joinToString(prefix = "$n  ", separator = "   ").run { println(this) }
    }
}

class Letter(val value: String, val slotsNeeded: Int = 1) {

    val slots by lazy {
        Slot.all.filter { it.letter == this }.sortedBy { it.number.value }
    }

    fun addConstraints() {

        // constrain each LETTER to number of slots needed
        addExpression().level(slotsNeeded).apply {
            slots.forEach {
                set(it.occupied, 1)
            }
        }

        // constrain slots to be consecutive if more than one are needed
        // this is tricky because slots need to be "batched" in rolling groups of needed slot size
        // e.g. for a slot size three, we need (x1,x2,x3), (x2,x3,x4), (x3,x4,x5) and so on. A binary is attached to each group
        // and another binary needs to be shared across all the batches
        // x1 + x2 + .. xi - Sb >= 0
        // Where xi is slot binaries, S is number of slots needed, and b is shared binary across all groups
        if (slotsNeeded > 1) {

            val allGroupSlots = addExpression().level(1)

            slots.rollingBatches(slotsNeeded).forEach { group ->

                val slotForGroup = variable().binary()

                allGroupSlots.set(slotForGroup, 1)

                addExpression().lower(0).apply {
                    group.forEach {
                        set(it.occupied,1)
                    }
                    set(slotForGroup, -1 * slotsNeeded)
                }
            }
        }
    }

    override fun toString() = value

    companion object {

        val all = ('A'..'Z').asSequence()
                .take(letterCount)
                .map { it.toString() }
                .map { Letter(it, ThreadLocalRandom.current().nextInt(1,maxContiguousBlocks+1)) }
                .toList()


    }
}

class Number(val value: Int)  {

    val slots by lazy {
        Slot.all.filter { it.number == this }
    }

    fun addConstraints() {

        // constrain each NUMBER to only be assigned one slot
        addExpression().upper(1).apply {
            slots.forEach {
                set(it.occupied, 1)
            }
        }
    }

    companion object {
        val all = (1..numberCount).asSequence()
                .map { Number(it) }
                .toList()
    }

    override fun toString() = value.toString()
}

data class Slot(val letter: Letter, val number: Number) {

    val occupied = variable().binary()

    companion object {
        val all = Letter.all.asSequence().flatMap { letter ->
            Number.all.asSequence().map { number -> Slot(letter, number) }
        }.toList()
    }
    override fun toString() = "$letter$number: ${occupied?.value?.toInt()}"
}

fun <T> List<T>.rollingBatches(batchSize: Int) = (0..size).asSequence().map { i ->
    subList(i, (i + batchSize).let { if (it > size) size else it })
}.filter { it.size == batchSize }