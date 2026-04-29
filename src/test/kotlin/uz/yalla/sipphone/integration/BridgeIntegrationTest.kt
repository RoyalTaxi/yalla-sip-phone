package uz.yalla.sipphone.integration

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import uz.yalla.sipphone.data.jcef.bridge.BridgeCallState
import uz.yalla.sipphone.data.jcef.bridge.BridgeCommand
import uz.yalla.sipphone.data.jcef.bridge.BridgeConnectionState
import uz.yalla.sipphone.data.jcef.bridge.BridgeInitPayload
import uz.yalla.sipphone.data.jcef.bridge.BridgeRouter
import uz.yalla.sipphone.data.jcef.bridge.BridgeSecurity
import uz.yalla.sipphone.data.jcef.bridge.BridgeState
import uz.yalla.sipphone.data.jcef.bridge.CommandResult
import uz.yalla.sipphone.data.jcef.bridge.bridgeJson
import uz.yalla.sipphone.data.jcef.events.BridgeEventEmitter
import uz.yalla.sipphone.data.jcef.keys.KeyShortcutRegistry
import uz.yalla.sipphone.data.workstation.agent.AgentStatusHolder
import uz.yalla.sipphone.domain.agent.AgentInfo
import uz.yalla.sipphone.domain.call.CallState
import uz.yalla.sipphone.domain.sip.SipAccountInfo
import uz.yalla.sipphone.domain.sip.SipCredentials
import uz.yalla.sipphone.testing.FakeSipAccountManager
import uz.yalla.sipphone.testing.engine.ScriptableCallEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BridgeIntegrationTest {

    private val callEngine = ScriptableCallEngine()
    private val sipAccountManager = FakeSipAccountManager()
    private val security = BridgeSecurity()
    private val eventEmitter = BridgeEventEmitter()
    private val agentStatusHolder = AgentStatusHolder()

    private var readyPayload: String = ""

    private val keyRegistry = KeyShortcutRegistry()

    private val router = BridgeRouter(
        callEngine = callEngine,
        sipAccountManager = sipAccountManager,
        security = security,
        keyRegistry = keyRegistry,
        agentStatusHolder = agentStatusHolder,
        onReady = {
            eventEmitter.agentInfo = AgentInfo("test-agent", "Test Operator")
            eventEmitter.completeHandshake()
        },
    )

    @Test
    fun `emitter buffers events before handshake`() {

        eventEmitter.emitIncomingCall("call-1", "998901234567")
        eventEmitter.emitCallConnected("call-1", "998901234567", "", "inbound")

        eventEmitter.agentInfo = AgentInfo("agent-1", "Alisher")
        val initJson = eventEmitter.completeHandshake()
        val init = bridgeJson.decodeFromString<BridgeInitPayload>(initJson)

        assertEquals(2, init.bufferedEvents.size, "Should have 2 buffered events")
        assertTrue(init.bufferedEvents[0].contains("incomingCall"), "First event should be incomingCall")
        assertTrue(init.bufferedEvents[1].contains("callConnected"), "Second event should be callConnected")
        assertEquals("agent-1", init.agent.id)
        assertEquals("Alisher", init.agent.name)
    }

    @Test
    fun `emitter clears buffer after handshake`() {
        eventEmitter.emitIncomingCall("call-1", "102")
        eventEmitter.completeHandshake()

        eventEmitter.emitCallEnded("call-1", "102", "inbound", 30, "normal")

    }

    @Test
    fun `emitter resetHandshake clears state`() {
        eventEmitter.completeHandshake()
        eventEmitter.resetHandshake()

        eventEmitter.emitIncomingCall("call-2", "103")
        val initJson = eventEmitter.completeHandshake()
        val init = bridgeJson.decodeFromString<BridgeInitPayload>(initJson)
        assertEquals(1, init.bufferedEvents.size)
    }

    @Test
    fun `emitter sequence numbers increment`() {
        val seq1 = eventEmitter.nextSeq()
        val seq2 = eventEmitter.nextSeq()
        val seq3 = eventEmitter.nextSeq()
        assertEquals(seq1 + 1, seq2)
        assertEquals(seq2 + 1, seq3)
    }

    @Test
    fun `emitIncomingCall produces correct JSON`() {
        eventEmitter.emitIncomingCall("call-1", "998901234567")
        val initJson = eventEmitter.completeHandshake()
        val init = bridgeJson.decodeFromString<BridgeInitPayload>(initJson)

        val eventJson = Json.parseToJsonElement(init.bufferedEvents[0]).jsonObject
        assertEquals("incomingCall", eventJson["event"]?.jsonPrimitive?.content)

        val data = eventJson["data"]?.jsonObject
        assertNotNull(data)
        assertEquals("call-1", data["callId"]?.jsonPrimitive?.content)
        assertEquals("998901234567", data["number"]?.jsonPrimitive?.content)
        assertEquals("inbound", data["direction"]?.jsonPrimitive?.content)
    }

    @Test
    fun `emitCallEnded includes duration and reason`() {
        eventEmitter.emitCallEnded("call-1", "102", "inbound", 45, "normal_hangup")
        val initJson = eventEmitter.completeHandshake()
        val init = bridgeJson.decodeFromString<BridgeInitPayload>(initJson)

        val eventJson = Json.parseToJsonElement(init.bufferedEvents[0]).jsonObject
        val data = eventJson["data"]?.jsonObject!!
        assertEquals("45", data["duration"]?.jsonPrimitive?.content)
        assertEquals("normal_hangup", data["reason"]?.jsonPrimitive?.content)
    }

    @Test
    fun `emitCallMuteChanged and emitCallHoldChanged produce correct events`() {
        eventEmitter.emitCallMuteChanged("call-1", true)
        eventEmitter.emitCallHoldChanged("call-1", true)
        eventEmitter.emitCallMuteChanged("call-1", false)
        val initJson = eventEmitter.completeHandshake()
        val init = bridgeJson.decodeFromString<BridgeInitPayload>(initJson)

        assertEquals(3, init.bufferedEvents.size)

        val mute1 = Json.parseToJsonElement(init.bufferedEvents[0]).jsonObject
        assertEquals("callMuteChanged", mute1["event"]?.jsonPrimitive?.content)
        assertEquals("true", mute1["data"]?.jsonObject?.get("isMuted")?.jsonPrimitive?.content)

        val hold = Json.parseToJsonElement(init.bufferedEvents[1]).jsonObject
        assertEquals("callHoldChanged", hold["event"]?.jsonPrimitive?.content)

        val mute2 = Json.parseToJsonElement(init.bufferedEvents[2]).jsonObject
        assertEquals("false", mute2["data"]?.jsonObject?.get("isMuted")?.jsonPrimitive?.content)
    }

    @Test
    fun `emitAgentStatusChanged includes previous status`() {
        eventEmitter.emitAgentStatusChanged("away", "ready")
        val initJson = eventEmitter.completeHandshake()
        val init = bridgeJson.decodeFromString<BridgeInitPayload>(initJson)

        val data = Json.parseToJsonElement(init.bufferedEvents[0]).jsonObject["data"]?.jsonObject!!
        assertEquals("away", data["status"]?.jsonPrimitive?.content)
        assertEquals("ready", data["previousStatus"]?.jsonPrimitive?.content)
    }

    @Test
    fun `emitConnectionChanged tracks state and attempt`() {
        eventEmitter.emitConnectionChanged("disconnected", 0)
        eventEmitter.emitConnectionChanged("reconnecting", 1)
        eventEmitter.emitConnectionChanged("connected", 0)
        val initJson = eventEmitter.completeHandshake()
        val init = bridgeJson.decodeFromString<BridgeInitPayload>(initJson)

        assertEquals(3, init.bufferedEvents.size)
        val reconnecting = Json.parseToJsonElement(init.bufferedEvents[1]).jsonObject["data"]?.jsonObject!!
        assertEquals("reconnecting", reconnecting["state"]?.jsonPrimitive?.content)
        assertEquals("1", reconnecting["attempt"]?.jsonPrimitive?.content)
    }

    @Test
    fun `BridgeCommand serialization round-trip`() {
        val cmd = BridgeCommand(
            command = "makeCall",
            params = mapOf("number" to "998901234567"),
        )
        val json = bridgeJson.encodeToString(BridgeCommand.serializer(), cmd)
        val decoded = bridgeJson.decodeFromString<BridgeCommand>(json)
        assertEquals("makeCall", decoded.command)
        assertEquals("998901234567", decoded.params["number"])
    }

    @Test
    fun `CommandResult success serialization`() {
        val result = CommandResult.success(
            kotlinx.serialization.json.buildJsonObject { put("callId", "call-1") }
        )
        val json = bridgeJson.encodeToString(CommandResult.serializer(), result)
        val parsed = Json.parseToJsonElement(json).jsonObject
        assertTrue(parsed["success"]?.jsonPrimitive?.boolean == true)
        assertEquals("call-1", parsed["data"]?.jsonObject?.get("callId")?.jsonPrimitive?.content)
        assertTrue(parsed["error"] is kotlinx.serialization.json.JsonNull)
    }

    @Test
    fun `CommandResult error serialization`() {
        val result = CommandResult.error("NO_ACTIVE_CALL", "No call", false)
        val json = bridgeJson.encodeToString(CommandResult.serializer(), result)
        val parsed = Json.parseToJsonElement(json).jsonObject
        assertFalse(parsed["success"]?.jsonPrimitive?.boolean == true)
        val error = parsed["error"]?.jsonObject!!
        assertEquals("NO_ACTIVE_CALL", error["code"]?.jsonPrimitive?.content)
        assertEquals("No call", error["message"]?.jsonPrimitive?.content)
        assertFalse(error["recoverable"]?.jsonPrimitive?.boolean == true)
    }

    @Test
    fun `BridgeState serialization with active call`() {
        val state = BridgeState(
            connection = BridgeConnectionState("connected", 0),
            agentStatus = "ready",
            call = BridgeCallState(
                callId = "call-1",
                number = "998901234567",
                direction = "inbound",
                state = "active",
                isMuted = true,
                isOnHold = false,
                duration = 45,
            ),
        )
        val json = bridgeJson.encodeToString(BridgeState.serializer(), state)
        val parsed = Json.parseToJsonElement(json).jsonObject
        val call = parsed["call"]?.jsonObject!!
        assertEquals("true", call["isMuted"]?.jsonPrimitive?.content)
        assertEquals("998901234567", call["number"]?.jsonPrimitive?.content)
    }

    @Test
    fun `BridgeState serialization without call`() {
        val state = BridgeState(
            connection = BridgeConnectionState("disconnected", 3),
            agentStatus = "break",
            call = null,
        )
        val json = bridgeJson.encodeToString(BridgeState.serializer(), state)
        val parsed = Json.parseToJsonElement(json).jsonObject
        assertEquals("disconnected", parsed["connection"]?.jsonObject?.get("state")?.jsonPrimitive?.content)
        assertEquals("3", parsed["connection"]?.jsonObject?.get("attempt")?.jsonPrimitive?.content)
    }

    @Test
    fun `full call lifecycle produces correct event sequence`() {

        val events = mutableListOf<String>()

        eventEmitter.emitIncomingCall("call-1", "998901234567")
        events.add("incomingCall")

        eventEmitter.emitCallConnected("call-1", "998901234567", "", "inbound")
        events.add("callConnected")

        eventEmitter.emitCallMuteChanged("call-1", true)
        events.add("callMuteChanged:true")

        eventEmitter.emitCallMuteChanged("call-1", false)
        events.add("callMuteChanged:false")

        eventEmitter.emitCallHoldChanged("call-1", true)
        events.add("callHoldChanged:true")

        eventEmitter.emitCallHoldChanged("call-1", false)
        events.add("callHoldChanged:false")

        eventEmitter.emitCallEnded("call-1", "998901234567", "inbound", 120, "normal")
        events.add("callEnded")

        val initJson = eventEmitter.completeHandshake()
        val init = bridgeJson.decodeFromString<BridgeInitPayload>(initJson)
        assertEquals(7, init.bufferedEvents.size, "Should have 7 events for full call lifecycle")

        val eventNames = init.bufferedEvents.map {
            Json.parseToJsonElement(it).jsonObject["event"]?.jsonPrimitive?.content
        }
        assertEquals(
            listOf("incomingCall", "callConnected", "callMuteChanged", "callMuteChanged", "callHoldChanged", "callHoldChanged", "callEnded"),
            eventNames,
        )
    }

    @Test
    fun `network disconnect and reconnect event sequence`() {
        eventEmitter.emitConnectionChanged("connected", 0)
        eventEmitter.emitConnectionChanged("disconnected", 0)
        eventEmitter.emitConnectionChanged("reconnecting", 1)
        eventEmitter.emitConnectionChanged("reconnecting", 2)
        eventEmitter.emitConnectionChanged("connected", 0)

        val initJson = eventEmitter.completeHandshake()
        val init = bridgeJson.decodeFromString<BridgeInitPayload>(initJson)
        assertEquals(5, init.bufferedEvents.size)

        val states = init.bufferedEvents.map {
            Json.parseToJsonElement(it).jsonObject["data"]?.jsonObject?.get("state")?.jsonPrimitive?.content
        }
        assertEquals(listOf("connected", "disconnected", "reconnecting", "reconnecting", "connected"), states)
    }

    @Test
    fun `busy operator shift produces many events without errors`() {

        repeat(10) { i ->
            val callId = "call-$i"
            val number = "99890${1000000 + i}"

            eventEmitter.emitIncomingCall(callId, number)
            eventEmitter.emitCallConnected(callId, number, "", "inbound")
            if (i % 3 == 0) eventEmitter.emitCallMuteChanged(callId, true)
            if (i % 3 == 0) eventEmitter.emitCallMuteChanged(callId, false)
            if (i % 5 == 0) eventEmitter.emitCallHoldChanged(callId, true)
            if (i % 5 == 0) eventEmitter.emitCallHoldChanged(callId, false)
            eventEmitter.emitCallEnded(callId, number, "inbound", 10 + i * 5, "normal")
        }

        val initJson = eventEmitter.completeHandshake()
        val init = bridgeJson.decodeFromString<BridgeInitPayload>(initJson)

        assertEquals(42, init.bufferedEvents.size, "Should have 42 events for 10 calls with mute/hold")

        val seqs = init.bufferedEvents.map {
            Json.parseToJsonElement(it).jsonObject["data"]?.jsonObject?.get("seq")?.jsonPrimitive?.content?.toInt() ?: 0
        }
        for (i in 1 until seqs.size) {
            check(seqs[i] > seqs[i - 1]) { "Sequence numbers must be strictly increasing: ${seqs[i-1]} < ${seqs[i]}" }
        }
    }

    @Test
    fun `ScriptableCallEngine records makeCall action`() = runTest {
        callEngine.makeCall("998901234567")
        assertEquals(1, callEngine.actions.size)
        val action = callEngine.actions[0]
        assertTrue(action is ScriptableCallEngine.Action.MakeCall)
        assertEquals("998901234567", (action as ScriptableCallEngine.Action.MakeCall).number)
    }

    @Test
    fun `ScriptableCallEngine records setMute and updates state`() = runTest {
        callEngine.emit(CallState.Active("c1", "102", null, false, false, false))
        callEngine.setMute("c1", true)

        val state = callEngine.callState.value
        assertTrue(state is CallState.Active)
        assertTrue((state as CallState.Active).isMuted)
        assertEquals(1, callEngine.actions.size)
    }

    @Test
    fun `ScriptableCallEngine records setHold and updates state`() = runTest {
        callEngine.emit(CallState.Active("c1", "102", null, false, false, false))
        callEngine.setHold("c1", true)

        val state = callEngine.callState.value
        assertTrue(state is CallState.Active)
        assertTrue((state as CallState.Active).isOnHold)
    }

    @Test
    fun `ScriptableCallEngine records sendDtmf`() = runTest {
        val result = callEngine.sendDtmf("c1", "1234#")
        assertTrue(result.isSuccess)
        assertEquals(1, callEngine.actions.size)
        val action = callEngine.actions[0] as ScriptableCallEngine.Action.SendDtmf
        assertEquals("c1", action.callId)
        assertEquals("1234#", action.digits)
    }

    @Test
    fun `ScriptableCallEngine records transferCall`() = runTest {
        val result = callEngine.transferCall("c1", "998907654321")
        assertTrue(result.isSuccess)
        assertEquals(1, callEngine.actions.size)
        val action = callEngine.actions[0] as ScriptableCallEngine.Action.TransferCall
        assertEquals("998907654321", action.destination)
    }

    @Test
    fun `FakeSipAccountManager tracks registerAll calls`() = runTest {
        val info = SipAccountInfo(
            extensionNumber = 101,
            serverUrl = "192.168.0.22",
            sipName = "Test",
            credentials = SipCredentials("192.168.0.22", 5060, "101", "pass"),
        )
        sipAccountManager.registerAll(listOf(info))

        assertEquals(1, sipAccountManager.registerAllCallCount)
        assertEquals(1, sipAccountManager.accounts.value.size)
    }
}
