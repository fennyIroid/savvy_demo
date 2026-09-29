package com.iroid.savvy.rd.ui

import com.iroid.savvy.core.Mode
import com.iroid.savvy.core.UnlockPolicy

/** Turns SavvyActions results and backend / core reason codes into plain sentences. */
object Friendly {
    private val codes = linkedMapOf(
        "not_a_savvy_card" to "That isn't a Savvy card.",
        "not a Savvy card" to "That isn't a Savvy card.",
        "card_bound_to_another_account" to "This card belongs to someone else.",
        "account_already_has_card" to "Your account already has a different card.",
        "card_not_owned_by_user" to "That's not your card. Use the card linked to your account.",
        "card_not_owned" to "That's not your card. Use the card linked to your account.",
        "card_revoked" to "This card has been turned off.",
        "bad_signature" to "This card couldn't be verified.",
        "format_mismatch" to "This card couldn't be verified.",
        "offline_card_check_failed" to "Offline, only your own linked card can unlock.",
        "offline_unlock_disabled" to "Card unlock needs the internet right now.",
        "sun_card_needs_internet" to "This card needs the internet to unlock.",
        "live_proof_needs_internet" to "This card needs the internet to unlock.",
        "card_or_server_required" to "Hold your card to finish this task.",
        "early_end_requires_card" to "This session needs your card to end early.",
        "needs the card to end early" to "This session needs your card to end early.",
        "card_required" to "This needs your Savvy card.",
        "locked_commitment_no_early_unlock" to "This session is locked until the timer runs out.",
        "no_card_linked" to "Link your Savvy card first to unblock apps.",
        "not_your_card" to "That isn't the card linked to your account.",
        "cannot_block_app" to "This app can't be blocked.",
        "no_active_commitment" to "There's no focus session running.",
        "no commitment" to "There's no focus session running.",
        "not_this_task" to "That card doesn't match this task.",
        "commitment_already_active" to "A focus session is already running.",
        "emergency_exit_limit_reached" to "You've used all your emergency exits for this week.",
        "already used this week" to "You've used all your emergency exits for this week.",
        "invalid_or_expired_code" to "That code is wrong or has expired.",
        "cannot_link_to_self" to "Use the code on another phone.",
        "parent_already_has_child" to "That parent already has a linked phone.",
        "choose apps first" to "Choose at least one app to block first.",
        "no linked child" to "No child phone is linked yet.",
        "not registered" to "Set up your account first.",
        "no usage data" to "Allow Usage access to see screen time.",
        "grant_" to "The unlock couldn't be verified. Try again.",
        "unauthorized" to "Your session has expired. Sign in again.",
        "managed_by_parent" to "Your parent manages this phone.",
        "no_leave_pin" to "Your parent hasn't set a PIN for leaving parent mode.",
        "wrong_pin" to "That PIN isn't right.",
    )

    fun text(raw: String): String {
        val r = raw.trim()
        when {
            r.startsWith("error:") && (r.contains("ConnectException") || r.contains("SocketTimeout") || r.contains("UnknownHost") || r.contains("IOException")) ->
                return "Can't reach Savvy right now. Check your connection."
            r.startsWith("started offline") -> return "Focus started offline. It will sync when you're back online."
            r.startsWith("started ") -> return "Focus started. Stay on track."
            r.startsWith("registered device") -> return "You're all set."
            r.startsWith("card ") && r.contains("registered") -> return "Your Savvy card is linked."
            r.startsWith("ended") || r.contains("Released") -> return "Session ended."
            r.startsWith("task done") || r.startsWith("task: Released") -> return "Task done. Nice work."
            r.startsWith("pending until") -> return "Emergency exit requested. It unlocks at ${r.removePrefix("pending until ").take(16).replace('T', ' ')}."
            r.startsWith("emergency exit offline") -> return "Emergency exit used. It will sync when you're back online."
            r.contains("Paused(") -> return "Unlocked for a short break."
            r.startsWith("always blocked ") -> return "Blocked. Only your Savvy card can unblock it."
            r.startsWith("unblocked ") -> return "Unblocked."
            r == "task added" -> return "Task added."
            r.startsWith("linked as child") -> return "This phone is now linked to your parent."
            r == "left parent mode" -> return "Parent mode is off. This phone is yours to manage now."
            r.startsWith("synced") -> return "Synced."
            r == "nothing to sync" -> return "Everything is already synced."
            r.startsWith("rules v") && r.endsWith("sent") -> return "Rules sent. They apply at the child's next sync."
        }
        codes.entries.firstOrNull { r.contains(it.key) }?.let { return it.value }
        if (r.startsWith("error:")) return "Something went wrong. ${r.removePrefix("error:").trim().substringAfterLast('.').take(80)}"
        return r
    }

    fun mode(m: Mode) = when (m) {
        Mode.STUDY -> "Study"; Mode.WORK -> "Work"; Mode.SLEEP -> "Sleep"; Mode.CUSTOM -> "Focus"; Mode.TASK -> "Task"
    }

    fun policy(p: UnlockPolicy) = when (p) {
        UnlockPolicy.CARD_REQUIRED -> "Card to unlock"
        UnlockPolicy.LOCKED -> "Locked"
        UnlockPolicy.FREE -> "Free"
    }

    fun policyLong(p: UnlockPolicy) = when (p) {
        UnlockPolicy.CARD_REQUIRED -> "End early only by tapping your Savvy card."
        UnlockPolicy.LOCKED -> "No early end. Only the emergency exit works."
        UnlockPolicy.FREE -> "End any time from the app. Good for gentle habits."
    }

    fun duration(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }

    /** "1h 05m" style clock for countdowns. */
    fun clock(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) "%dh %02dm".format(h, m) else "%dm %02ds".format(m, sec)
    }

    /** Local wall-clock time, e.g. "14:20". */
    fun time(epochMs: Long): String = java.time.Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))

    fun hm(seconds: Long): Pair<String, String> = "${seconds / 3600}h" to "${(seconds % 3600) / 60}m"
}
