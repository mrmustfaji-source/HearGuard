package com.mustfa.heatguard

/**
 * Remote APK wale controls, ek list.
 * Controller inhe doosre phone par bhejta hai. Warna yahi phone chalata hai.
 */
object Pad {

    data class Key(val en: String, val hi: String, val command: String)

    data class Group(val en: String, val hi: String, val keys: List<Key>)

    val groups: List<Group> = listOf(
        Group("Navigation", "Navigation", listOf(
            Key("Back", "Back", "/key 4"),
            Key("Home", "Home", "/key 3"),
            Key("Recents", "Recents", "/key 187"),
            Key("Menu", "Menu", "/key 82"),
            Key("All apps", "Saari apps", "/key 284"),
            Key("Search", "Search", "/key 84"),
            Key("Assist", "Assist", "/key 231")
        )),
        Group("Power", "Power", listOf(
            Key("Wake", "Jagaao", "/key 224"),
            Key("Sleep", "Sulaao", "/key 223"),
            Key("Power", "Power", "/key 26"),
            Key("Screenshot", "Screenshot", "/key 318")
        )),
        Group("Volume", "Volume", listOf(
            Key("Vol +", "Vol +", "/key 24"),
            Key("Vol -", "Vol -", "/key 25"),
            Key("Mute", "Mute", "/key 164")
        )),
        Group("Media", "Media", listOf(
            Key("Prev", "Pichhla", "/key 88"),
            Key("Play", "Play", "/key 85"),
            Key("Next", "Agla", "/key 87"),
            Key("Stop", "Stop", "/key 86")
        )),
        Group("Brightness", "Roshni", listOf(
            Key("Darker", "Kam", "/bright down"),
            Key("Brighter", "Zyada", "/bright up"),
            Key("Key -", "Key -", "/key 220"),
            Key("Key +", "Key +", "/key 221")
        )),
        Group("D-pad", "D-pad", listOf(
            Key("Up", "Up", "/key 19"),
            Key("Down", "Down", "/key 20"),
            Key("Left", "Left", "/key 21"),
            Key("Right", "Right", "/key 22"),
            Key("OK", "OK", "/key 23")
        )),
        Group("Typing keys", "Typing keys", listOf(
            Key("Del", "Del", "/key 67"),
            Key("Enter", "Enter", "/key 66"),
            Key("Tab", "Tab", "/key 61"),
            Key("Esc", "Esc", "/key 111"),
            Key("Space", "Space", "/key 62"),
            Key("Copy", "Copy", "/key 278"),
            Key("Paste", "Paste", "/key 279"),
            Key("Cut", "Cut", "/key 277")
        )),
        Group("Page", "Page", listOf(
            Key("Page up", "Page up", "/key 92"),
            Key("Page down", "Page down", "/key 93"),
            Key("Top", "Top", "/key 122"),
            Key("Bottom", "Bottom", "/key 123"),
            Key("Zoom +", "Zoom +", "/key 168"),
            Key("Zoom -", "Zoom -", "/key 169"),
            Key("Refresh", "Refresh", "/key 285"),
            Key("Camera", "Camera", "/key 27")
        )),
        Group("Panels", "Panels", listOf(
            Key("Notifications", "Notifications", "/panel notifications"),
            Key("Quick settings", "Quick settings", "/panel settings"),
            Key("Close shade", "Band", "/panel collapse")
        )),
        Group("Toggles", "Toggles", listOf(
            Key("WiFi on", "WiFi on", "/wifi on"),
            Key("WiFi off", "WiFi off", "/wifi off"),
            Key("BT on", "BT on", "/bt on"),
            Key("BT off", "BT off", "/bt off"),
            Key("Data on", "Data on", "/data on"),
            Key("Data off", "Data off", "/data off"),
            Key("Rotate", "Rotate", "/rotate"),
            Key("Torch on", "Torch on", "/torch on"),
            Key("Torch off", "Torch off", "/torch off")
        )),
        Group("Phone", "Phone", listOf(
            Key("Status", "Status", "/status"),
            Key("CPU", "CPU", "/top"),
            Key("Deep sleep unused", "Unused sulana", "/sleep"),
            Key("App list", "App list", "/apps")
        ))
    )
}
