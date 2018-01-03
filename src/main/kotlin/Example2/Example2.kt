package Example2

import org.ojalgo.optimisation.ExpressionsBasedModel
import org.ojalgo.optimisation.Variable
import java.util.concurrent.atomic.AtomicInteger

// declare ojAlgo Model
val model = ExpressionsBasedModel()

// custom DSL for Example3.getModel expression inputs, eliminate naming and adding
val funcId = AtomicInteger(0)
val variableId = AtomicInteger(0)
fun variable() = Variable(variableId.incrementAndGet().toString().let { "Variable$it" }).apply(model::addVariable)
fun addExpression() = funcId.incrementAndGet().let { "Func$it"}.let { model.addExpression(it) }


fun main(args: Array<String>) {

    Letter.values().forEach { it.addConstraints() }
    Number.values().forEach { it.addConstraints() }

    // C must be greater than or equal to THREE
    addExpression().level(1).apply {
        Letter.C.slots.asSequence().filter { it.number.value >= 3 }.forEach {
            set(it.occupied, 1)
        }
    }

    // D must be less than or equal to TWO
    addExpression().level(1).apply {
        Letter.D.slots.asSequence().filter { it.number.value <= 2 }.forEach {
            set(it.occupied, 1)
        }
    }


    model.minimise().run(::println)

    Letter.values().joinToString(prefix = "   ", separator = "   ").run(::println)

    Number.values().forEach { n ->
        Letter.values().asSequence().map { l -> l.slots.first { it.number == n }.occupied.value.toInt() }
                .joinToString(prefix = "$n  ", separator = "   ").run { println(this) }
    }
}

enum class Letter {
    A,
    B,
    C,
    D,
    E;

    val slots by lazy {
        Slot.all.filter { it.letter == this }.sortedBy { it.number.value }
    }

    fun addConstraints() {

        // constrain each letter to only be assigned once
        addExpression().upper(1).apply {
            slots.forEach {
                set(it.occupied, 1)
            }
        }
    }
}

enum class Number(val value: Int)  {
    ONE(1),
    TWO(2),
    THREE(3),
    FOUR(4),
    FIVE(5);

    val slots by lazy {
        Slot.all.filter { it.number == this }.sortedBy { it.letter }
    }

    fun addConstraints() {

        // constrain each NUMBER to only be assigned one slot
        addExpression().upper(1).apply {
            slots.forEach {
                set(it.occupied, 1)
            }
        }
    }

    override fun toString() = value.toString()
}

data class Slot(val letter: Letter, val number: Number) {

    val occupied = variable().binary()

    companion object {
        val all = Letter.values().asSequence().flatMap { letter ->
            Number.values().asSequence().map { number -> Slot(letter, number) }
        }.toList()
    }
    override fun toString() = "$letter$number: ${occupied?.value?.toInt()}"
}
