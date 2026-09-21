package dev.srsatt.keyboard.layout.runtime

data class LegacyRepeatedReleaseTrigger(
    val id: String,
    val normalActionId: String,
    val alternateActionId: String,
) {
    init {
        require(id.isNotBlank()) { "Repeated-release trigger ID must not be blank" }
        require(normalActionId.isNotBlank()) { "Normal action ID must not be blank" }
        require(alternateActionId.isNotBlank()) { "Alternate action ID must not be blank" }
    }
}

sealed interface LegacyRepeatedReleaseDelivery {
    data class Normal(val actionId: String, val pressed: Boolean) : LegacyRepeatedReleaseDelivery
    data class Alternate(val actionId: String) : LegacyRepeatedReleaseDelivery
}

/** Preserves the legacy no-timeout policy: the third matching release invokes the alternate action. */
class LegacyRepeatedReleaseRuntime {
    private var previousTriggerId: String? = null
    private var releaseCount = 0

    fun onTrigger(
        trigger: LegacyRepeatedReleaseTrigger,
        pressed: Boolean,
    ): List<LegacyRepeatedReleaseDelivery> = buildList {
        add(LegacyRepeatedReleaseDelivery.Normal(trigger.normalActionId, pressed))
        if (pressed) return@buildList

        if (trigger.id == previousTriggerId) {
            releaseCount += 1
        } else {
            previousTriggerId = trigger.id
            releaseCount = 1
        }

        if (releaseCount == 3) {
            add(LegacyRepeatedReleaseDelivery.Alternate(trigger.alternateActionId))
            cancel()
        }
    }

    /** Call for an intervening event that is outside the repeated-release policy. */
    fun cancel() {
        previousTriggerId = null
        releaseCount = 0
    }
}

data class FirmwareRepeatDelivery(
    val ownerId: String,
    val actionId: String,
    val deliveredAtMillis: Long,
)

class FirmwareRepeatCapacityException(capacity: Int, ownerId: String) :
    IllegalStateException("Firmware repeat capacity $capacity exceeded by '$ownerId'")

/** A bounded, timer-driven repeat service matching the legacy immediate/300 ms/5 ms defaults. */
class FirmwareTimedRepeatRuntime(
    private val capacity: Int = 4,
    private val initialDelayMillis: Long = 300,
    private val intervalMillis: Long = 5,
) {
    init {
        require(capacity > 0) { "Firmware repeat capacity must be positive" }
        require(initialDelayMillis > 0) { "Firmware repeat initial delay must be positive" }
        require(intervalMillis > 0) { "Firmware repeat interval must be positive" }
    }

    private data class ActiveRepeat(
        val actionId: String,
        var nextAtMillis: Long,
    )

    private val active = linkedMapOf<String, ActiveRepeat>()
    private var currentTimeMillis = 0L

    fun press(ownerId: String, actionId: String, nowMillis: Long): FirmwareRepeatDelivery {
        require(ownerId.isNotBlank()) { "Firmware repeat owner ID must not be blank" }
        require(actionId.isNotBlank()) { "Firmware repeat action ID must not be blank" }
        requireTime(nowMillis)
        require(ownerId !in active) { "Firmware repeat owner '$ownerId' is already active" }
        if (active.size == capacity) throw FirmwareRepeatCapacityException(capacity, ownerId)

        currentTimeMillis = nowMillis
        active[ownerId] = ActiveRepeat(actionId, Math.addExact(nowMillis, initialDelayMillis))
        return FirmwareRepeatDelivery(ownerId, actionId, nowMillis)
    }

    fun release(ownerId: String) {
        requireNotNull(active.remove(ownerId)) { "Firmware repeat owner '$ownerId' is not active" }
    }

    fun onTimer(nowMillis: Long): List<FirmwareRepeatDelivery> {
        requireTime(nowMillis)
        currentTimeMillis = nowMillis
        return active.mapNotNull { (ownerId, repeat) ->
            if (nowMillis < repeat.nextAtMillis) return@mapNotNull null
            repeat.nextAtMillis = Math.addExact(nowMillis, intervalMillis)
            FirmwareRepeatDelivery(ownerId, repeat.actionId, nowMillis)
        }
    }

    private fun requireTime(nowMillis: Long) {
        require(nowMillis >= currentTimeMillis) {
            "Firmware repeat time cannot move backwards: $nowMillis < $currentTimeMillis"
        }
    }
}
