package uz.yalla.sipphone.testing.scenario

import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

enum class TrafficPhase(val weight: Int) {

    BURST(40),

    BREATHE(45),

    LULL(15),
}

enum class ScenarioType(val weight: Int) {

    NORMAL_AGENT_HANGUP(40),

    NORMAL_CALLER_HANGUP(15),

    CALLER_ABANDON(12),

    HOLD_RESUME(8),

    MUTE_UNMUTE(5),

    TRANSFER(5),

    SHORT_CALL(4),

    LONG_CALL(3),

    BUSY_CALLEE(2),

    NO_ANSWER_TIMEOUT(2),

    DTMF_NAVIGATION(2),

    RAPID_FIRE(1),

    NETWORK_DROP(1),
}

class TrafficPattern(val random: Random = Random.Default) {

    private var currentPhase: TrafficPhase = pickPhase()

    private fun pickPhase(): TrafficPhase =
        weightedPick(TrafficPhase.entries, TrafficPhase::weight)

    fun nextInterCallGap(): Duration {

        if (random.nextInt(100) < 20) {
            currentPhase = pickPhase()
        }
        return when (currentPhase) {
            TrafficPhase.BURST -> randomDuration(200.milliseconds, 2.seconds)
            TrafficPhase.BREATHE -> randomDuration(3.seconds, 10.seconds)
            TrafficPhase.LULL -> randomDuration(15.seconds, 45.seconds)
        }
    }

    fun nextTalkDuration(): Duration {
        val bucket = random.nextInt(100)
        return when {
            bucket < 10 -> randomDuration(2.seconds, 8.seconds)
            bucket < 60 -> randomDuration(15.seconds, 60.seconds)
            bucket < 85 -> randomDuration(60.seconds, 120.seconds)
            else -> randomDuration(120.seconds, 300.seconds)
        }
    }

    fun nextRingDuration(): Duration {
        val bucket = random.nextInt(100)
        return when {
            bucket < 30 -> randomDuration(1.seconds, 3.seconds)
            bucket < 70 -> randomDuration(3.seconds, 8.seconds)
            bucket < 90 -> randomDuration(8.seconds, 15.seconds)
            else -> randomDuration(15.seconds, 30.seconds)
        }
    }

    fun nextScenarioType(): ScenarioType =
        weightedPick(ScenarioType.entries, ScenarioType::weight)

    private fun randomDuration(min: Duration, max: Duration): Duration {
        val minMs = min.inWholeMilliseconds
        val maxMs = max.inWholeMilliseconds
        return random.nextLong(minMs, maxMs + 1).milliseconds
    }

    private fun <T> weightedPick(items: List<T>, weight: (T) -> Int): T {
        val totalWeight = items.sumOf { weight(it) }
        var roll = random.nextInt(totalWeight)
        for (item in items) {
            roll -= weight(item)
            if (roll < 0) return item
        }
        return items.last()
    }
}
