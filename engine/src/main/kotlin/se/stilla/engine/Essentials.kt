package se.stilla.engine

/**
 * Apps Stilla must never prompt for, block or draw over: calls, messages,
 * alarms, calendar and BankID. Stilla must never trap you away from help.
 *
 * Phone and emergency apps are permanent. The rest are defaults you can
 * change; you can also add your own (e.g. 1177, your bank).
 */
object Essentials {

    /** Can never be removed from the essentials list. */
    val PERMANENT: Set<String> = setOf(
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.phone",
        "com.android.server.telecom",
        "com.samsung.android.incallui",
        "com.android.emergency",
        "com.google.android.apps.safetyhub",
        "com.samsung.android.emergency",
    )

    /** On by default; you can turn them off. */
    val DEFAULTS: Set<String> = setOf(
        // Messages
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
        // Clock and alarms
        "com.google.android.deskclock",
        "com.sec.android.app.clockpackage",
        "com.android.deskclock",
        // Calendar
        "com.samsung.android.calendar",
        "com.google.android.calendar",
        // BankID
        "com.bankid.bus",
    )

    /**
     * @param added packages you marked as essential yourself
     * @param removed default packages you turned off (permanent ones ignore this)
     * @param self Stilla's own package name
     */
    fun isEssential(
        packageName: String,
        added: Set<String> = emptySet(),
        removed: Set<String> = emptySet(),
        self: String? = null,
    ): Boolean = when {
        packageName == self -> true
        packageName in PERMANENT -> true
        packageName in added -> true
        packageName in DEFAULTS -> packageName !in removed
        else -> false
    }

    fun canRemove(packageName: String, self: String? = null): Boolean =
        packageName != self && packageName !in PERMANENT
}
