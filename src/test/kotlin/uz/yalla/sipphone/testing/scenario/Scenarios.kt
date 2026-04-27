package uz.yalla.sipphone.testing.scenario

import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

suspend fun ScenarioRunner.ScenarioContext.singleCall(
    number: String = "1001",
    name: String? = "Test Caller",
) {
    incomingCall {
        ring(number, name = name, holdFor = 3.seconds)
        active(holdFor = 30.seconds)
        ending(holdFor = 1.seconds)
        idle()
    }
}

suspend fun ScenarioRunner.ScenarioContext.busyOperatorDay(
    random: Random = Random.Default,
) {
    val traffic = TrafficPattern(random)
    val callCount = random.nextInt(10, 16)

    register()

    repeat(callCount) { i ->
        val scenarioType = traffic.nextScenarioType()
        val callerNumber = "${1000 + i}"
        val ringDuration = traffic.nextRingDuration()
        val talkDuration = traffic.nextTalkDuration()

        playScenarioType(scenarioType, callerNumber, ringDuration, talkDuration, traffic)

        if (i < callCount - 1) {
            pause(traffic.nextInterCallGap())
        }
    }
}

suspend fun ScenarioRunner.ScenarioContext.stressTest(
    count: Int = 50,
    random: Random = Random.Default,
) {
    val traffic = TrafficPattern(random)

    register()

    repeat(count) { i ->
        val scenarioType = traffic.nextScenarioType()
        val callerNumber = "${2000 + i}"
        val ringDuration = traffic.nextRingDuration()
        val talkDuration = traffic.nextTalkDuration()

        playScenarioType(scenarioType, callerNumber, ringDuration, talkDuration, traffic)

        pause(traffic.nextInterCallGap().coerceAtMost(500.milliseconds))
    }
}

suspend fun ScenarioRunner.ScenarioContext.networkDisruption(
    random: Random = Random.Default,
) {
    val traffic = TrafficPattern(random)

    register()
    repeat(3) { i ->
        incomingCall {
            ring("${3000 + i}", name = "Pre-disruption $i", holdFor = traffic.nextRingDuration())
            active(holdFor = traffic.nextTalkDuration())
            ending(holdFor = 500.milliseconds)
            idle()
        }
        pause(traffic.nextInterCallGap())
    }

    incomingCall {
        ring("3100", name = "During Disruption", holdFor = 2.seconds)
        active(holdFor = 5.seconds)

        idle()
    }
    disconnect("Network timeout")
    pause(3.seconds)

    register()
    repeat(3) { i ->
        incomingCall {
            ring("${3200 + i}", name = "Post-recovery $i", holdFor = traffic.nextRingDuration())
            active(holdFor = traffic.nextTalkDuration())
            ending(holdFor = 500.milliseconds)
            idle()
        }
        if (i < 2) pause(traffic.nextInterCallGap())
    }
}

object Scenarios {

    fun simpleInboundCall(
        number: String = "998901234567",
        name: String? = "Alisher",
        ringDuration: kotlin.time.Duration = 3.seconds,
        talkDuration: kotlin.time.Duration = 30.seconds,
    ): List<ScenarioStep> = callScenario {
        ring(number, name = name, holdFor = ringDuration)
        active(holdFor = talkDuration)
        ending(holdFor = 1.seconds)
        idle()
    }

    fun busyOperatorDay(): List<ScenarioStep> = callScenario {

        ring("998901234567", name = "Alisher", holdFor = 3.seconds)
        active(holdFor = 30.seconds)
        ending(holdFor = 1.seconds)
        idle(holdFor = 3.seconds)

        ring("998907654321", holdFor = 8.seconds)
        idle(holdFor = 4.seconds)

        ring("998935551234", name = "Dilshod", holdFor = 3.seconds)
        active(holdFor = 10.seconds)
        mute(holdFor = 5.seconds)
        unmute(holdFor = 10.seconds)
        ending(holdFor = 1.seconds)
        idle(holdFor = 5.seconds)

        idle(holdFor = 6.seconds)

        ring("998909876543", name = "Sardor", outbound = true, holdFor = 4.seconds)
        active(holdFor = 15.seconds)
        hold(holdFor = 8.seconds)
        unhold(holdFor = 10.seconds)
        ending(holdFor = 1.seconds)
        idle(holdFor = 3.seconds)

        ring("998712223344", name = "Bekzod", holdFor = 2.seconds)
        active(holdFor = 7.seconds)
        ending(holdFor = 1.seconds)
        idle(holdFor = 3.seconds)

        ring("998946667788", name = "Rustam", holdFor = 3.seconds)
        active(holdFor = 12.seconds)
        mute(holdFor = 4.seconds)
        unmute(holdFor = 8.seconds)
        hold(holdFor = 6.seconds)
        unhold(holdFor = 15.seconds)
        ending(holdFor = 1.seconds)
        idle()
    }
}

private suspend fun ScenarioRunner.ScenarioContext.playScenarioType(
    type: ScenarioType,
    callerNumber: String,
    ringDuration: kotlin.time.Duration,
    talkDuration: kotlin.time.Duration,
    traffic: TrafficPattern,
) {
    when (type) {
        ScenarioType.NORMAL_AGENT_HANGUP -> incomingCall {
            ring(callerNumber, holdFor = ringDuration)
            active(holdFor = talkDuration)
            ending(holdFor = 500.milliseconds)
            idle()
        }

        ScenarioType.NORMAL_CALLER_HANGUP -> incomingCall {
            ring(callerNumber, holdFor = ringDuration)
            active(holdFor = talkDuration)
            ending(holdFor = 200.milliseconds)
            idle()
        }

        ScenarioType.CALLER_ABANDON -> incomingCall {
            ring(callerNumber, holdFor = ringDuration)

            ending(holdFor = 200.milliseconds)
            idle()
        }

        ScenarioType.HOLD_RESUME -> incomingCall {
            ring(callerNumber, holdFor = ringDuration)
            active(holdFor = talkDuration.div(3))
            hold(holdFor = talkDuration.div(3))
            unhold(holdFor = talkDuration.div(3))
            ending(holdFor = 500.milliseconds)
            idle()
        }

        ScenarioType.MUTE_UNMUTE -> incomingCall {
            ring(callerNumber, holdFor = ringDuration)
            active(holdFor = talkDuration.div(3))
            mute(holdFor = talkDuration.div(3))
            unmute(holdFor = talkDuration.div(3))
            ending(holdFor = 500.milliseconds)
            idle()
        }

        ScenarioType.TRANSFER -> incomingCall {
            ring(callerNumber, holdFor = ringDuration)
            active(holdFor = talkDuration.div(2))

            ending(holdFor = 500.milliseconds)
            idle()
        }

        ScenarioType.SHORT_CALL -> incomingCall {
            ring(callerNumber, holdFor = 1.seconds)
            active(holdFor = talkDuration.coerceAtMost(8.seconds))
            ending(holdFor = 200.milliseconds)
            idle()
        }

        ScenarioType.LONG_CALL -> incomingCall {
            ring(callerNumber, holdFor = ringDuration)
            active(holdFor = talkDuration.coerceAtLeast(120.seconds))
            ending(holdFor = 500.milliseconds)
            idle()
        }

        ScenarioType.BUSY_CALLEE -> outboundCall(callerNumber) {
            ring(callerNumber, outbound = true, holdFor = 1.seconds)

            ending(holdFor = 200.milliseconds)
            idle()
        }

        ScenarioType.NO_ANSWER_TIMEOUT -> incomingCall {
            ring(callerNumber, holdFor = 30.seconds)

            idle()
        }

        ScenarioType.DTMF_NAVIGATION -> outboundCall(callerNumber) {
            ring(callerNumber, outbound = true, holdFor = ringDuration)
            active(holdFor = talkDuration)
            ending(holdFor = 500.milliseconds)
            idle()
        }

        ScenarioType.RAPID_FIRE -> {

            incomingCall {
                ring(callerNumber, holdFor = 1.seconds)
                active(holdFor = 5.seconds)
                ending(holdFor = 200.milliseconds)
                idle()
            }
            pause(200.milliseconds)
            incomingCall {
                ring("${callerNumber}b", holdFor = 1.seconds)
                active(holdFor = 5.seconds)
                ending(holdFor = 200.milliseconds)
                idle()
            }
        }

        ScenarioType.NETWORK_DROP -> incomingCall {
            ring(callerNumber, holdFor = ringDuration)
            active(holdFor = talkDuration.div(2))

            idle()
        }
    }
}
