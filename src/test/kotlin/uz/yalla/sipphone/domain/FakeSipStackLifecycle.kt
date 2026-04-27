package uz.yalla.sipphone.domain

import uz.yalla.sipphone.domain.sip.SipStackLifecycle

class FakeSipStackLifecycle : SipStackLifecycle {
    var initializeCalled = false
    var shutdownCalled = false

    override suspend fun initialize(): Result<Unit> {
        initializeCalled = true
        return Result.success(Unit)
    }

    override suspend fun shutdown() {
        shutdownCalled = true
    }
}
