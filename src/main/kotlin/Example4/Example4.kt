package Example4

import org.ojalgo.optimisation.ExpressionsBasedModel
import org.ojalgo.optimisation.Variable
import org.ojalgo.optimisation.integer.IntegerSolver
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

val minContiguousBlocks = 2
val maxContiguousBlocks = 4

val numberCount = 36


fun main(args: Array<String>) {

    Letter.all.forEach { it.addConstraints() }
    Number.all.forEach { it.addConstraints() }

    model.countVariables().run { println("$this variables") }

    model.options.debug(IntegerSolver::class.java)

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

    val batches by lazy {
        slots.rollingBatches(slotsNeeded)
    }

    fun slotsFor(number: Number) = batches.asSequence()
            .filter { it.any { it.number == number } }
            .map { it.first() }

    fun addConstraints() {

        // Letter must be assigned once
        addExpression().level(1).apply {
            slots.forEach { set(it.occupied,  1) }
        }

        //prevent scheduling at end of window
        // all slots must sum to 0 in region smaller than slots needed
        slots.takeLast(slotsNeeded - 1)
                .forEach {
                    it.occupied.level(0)
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

    fun addConstraints() {

        addExpression().lower(0).upper(1).apply {
            Letter.all.asSequence().flatMap { it.slotsFor(this@Number) }
                    .toList().apply {
                        //println("${this@Number.value}-$this")
                    }
                    .forEach {
                        set(it.occupied, 1)
                    }

        }
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