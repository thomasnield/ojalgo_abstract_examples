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
val numberCount = 18
val minContiguousBlocks = 2
val maxContiguousBlocks = 4

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
    Letter.all.map { it.slotsNeeded }.joinToString(prefix = "   ", separator = "   ").run(::println)

    Number.all.forEach { n ->
        Letter.all.asSequence().map { l -> l.slots.first { it.number == n }.occupied.value.toInt() }
                .joinToString(prefix = "$n  ", separator = "   ").run { println(this) }
    }
}

class Letter(val value: String, val slotsNeeded: Int = 1) {

    val slots by lazy {
        Slot.all.filter { it.letter == this }.sortedBy { it.number.value }
    }

    // R_x
    val cumulativeState = variable().lower(0)

    fun addConstraints() {

        // Letter must be assigned once
        addExpression().level(1).apply {
            slots.forEach { set(it.occupied,  1) }
        }

        // R_x = 1_A + 2_A + ...
        addExpression().level(0).apply {

            set(cumulativeState, -1)

            slots.forEach {
                set(it.occupied, slotsNeeded)
            }
        }

        // contiguous groupings

        /*slots.rollingBatches(slotsNeeded).forEach { batch ->
            val start = batch.first()

            addExpression().lower(0).apply {
                batch.forEach {
                    set(it.number.cumulativeState, 1)
                }

                set(start.occupied,-1 * slotsNeeded)
            }
        }*/
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

    // R_x
    val cumulativeState = variable().lower(0).upper(1)


    fun addConstraints() {

        // R_x >= A_x + B_x + ...
        addExpression().upper(0).apply {

            slots.forEach {
                set(it.occupied, 1)
            }

            set(cumulativeState, -1)
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