package sgnv.anubis.app.data

/**
 * Packages that must never end up frozen, because doing so breaks text input,
 * phone calls, or 2FA flows — which typically requires a factory reset or ADB
 * recovery for non-technical users.
 *
 * Enforced in two places:
 *  - [sgnv.anubis.app.data.repository.AppRepository.autoSelectRestricted] filters
 *    these out so Auto-Select never proposes them.
 *  - [sgnv.anubis.app.ui.screens.AddAppSheet] shows a confirmation dialog before
 *    assigning any of these to a group via manual multi-select.
 *
 * List curated from incidents in the issue tracker and Habr feedback
 * (Yandex.Клавиатура auto-selected by the `ru.yandex.` prefix heuristic in
 * [DefaultRestrictedApps] being the canonical case).
 */
object NeverRestrictApps {

    val packageNames = setOf(
        // Keyboards — without them text input stops working
        "com.google.android.inputmethod.latin",       // Gboard
        "com.samsung.android.honeyboard",             // Samsung Keyboard
        "org.futo.inputmethod.latin",                 // FUTO (privacy-focused)
        "ru.yandex.androidkeyboard",                  // Yandex.Клавиатура
        "com.touchtype.swiftkey",                     // SwiftKey

        // OEM IMS — calls/SMS break without these on Honor/Huawei
        "com.hihonor.ims",
        "com.huawei.ims",

        // 2FA — loss of access to any service whose key lives here
        "ru.yandex.key",
    )

    fun isNeverRestrict(packageName: String): Boolean = packageName in packageNames
}
