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


val letterCount = 9
val numberCount = 45

val minContiguousBlocks = 2
val maxContiguousBlocks = 6

fun main(args: Array<String>) {

    Letter.all.forEach { it.addConstraints() }
    Number.all.forEach { it.addConstraints() }

    model.countVariables().run { println("$this variables") }


    model.minimise().run(::println)

    Letter.all.joinToString(prefix = "\t", separator = "\t").run(::println)
    Letter.all.map { it.slotsNeeded }.joinToString(prefix = "\t", separator = "\t").run(::println)

    Number.all.forEach { n ->
        Letter.all.asSequence().map { l -> l.slots.first { it.number == n }.occupied.value.toInt() }
                .joinToString(prefix = "$n  ", separator = "\t").run { println(this) }
    }
}

class Letter(val value: String, val slotsNeeded: Int = 1) {

    val slots by lazy {
        Slot.all.filter { it.letter == this }.sortedBy { it.number.value }
    }

    fun addConstraints() {

        // Letter must be assigned once
        addExpression().level(1).apply {
            slots.forEach { set(it.occupied,  1) }
        }

        //handle recurrences
        if (slotsNeeded > 1) {
            slots.rollingBatches(slotsNeeded).forEach { batch ->

                val first = batch.first()

                addExpression().upper(0).apply {

                    batch.asSequence().flatMap { it.number.slots.asSequence() }
                            .forEach {
                                set(it.occupied, 1)
                            }

                    set(first.number.cumulativeState, -1)
                }
            }
        }
    }

    override fun toString() = value

    companion object {

        val all = ('A'..'Z').asSequence()
                .take(letterCount)
                .map { it.toString() }
                .map { Letter(it, ThreadLocalRandom.current().nextInt(minContiguousBlocks, maxContiguousBlocks + 1)) }
                .toList()


    }
}

class Number(val value: Int)  {

    val slots by lazy {
        Slot.all.filter { it.number == this }
    }

    // b_x
    val cumulativeState = variable().lower(0).upper(1)


    fun addConstraints() {

        /*
        // b_x = A_x + B_x + ...
        addExpression().level(0).apply {

            slots.forEach {
                set(it.occupied, 1)
            }

            set(cumulativeState, -1)
        }
        */
    }

    companion object {
        val all = (1..numberCount).asSequence()
                .map { Number(it) }
                .toList()
    }

    override fun toString() = value.toString().let { if (it.length == 1) "$it " else it }
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