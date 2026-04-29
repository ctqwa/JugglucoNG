package tk.glucodata

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object UiRefreshBus {
    sealed interface Event {
        data object DataChanged : Event
        data object StatusOnly : Event
    }

    private val _events = MutableSharedFlow<Event>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val _revision = MutableStateFlow(0L)

    val events = _events.asSharedFlow()
    val revision = _revision.asStateFlow()

    private fun bumpRevision() {
        _revision.value = _revision.value + 1L
    }

    @JvmStatic
    fun requestDataRefresh() {
        bumpRevision()
        _events.tryEmit(Event.DataChanged)
        GlucoseUpdateBroadcaster.send(Applic.app)
    }

    @JvmStatic
    fun requestStatusRefresh() {
        _events.tryEmit(Event.StatusOnly)
        runCatching { Floating.invalidatefloat() }
    }
}
