package ir.irandetector.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Hand-authored icon set.
 *
 * Why not `material-icons-extended`? That artifact adds ~6 000 vector icons
 * (~8 MB of dex + resources) for the ~14 glyphs this app actually uses. Shipping
 * these 14 paths by hand keeps the APK small and the dex step fast — a deliberate
 * size decision, documented in BUILD_REPORT.md.
 *
 * All glyphs use a 24x24 viewport and tint with the current content colour.
 */
object AppIcons {

    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(pathData),
            fill = SolidColor(Color.White),
        ).build()

    /** Thin-stroke alternative for hero/empty states (stroke rendered as outline fill). */
    val Search: ImageVector by lazy {
        icon(
            "Search",
            "M10,4a6,6 0 1,0 0,12a6,6 0 0,0 0,-12zM10,6a4,4 0 1,1 0,8a4,4 0 0,1 0,-8z" +
                "M15.3,16.7l1.4,-1.4l4,4l-1.4,1.4z",
        )
    }

    val Settings: ImageVector by lazy {
        icon(
            "Settings",
            "M12,8a4,4 0 1,0 0,8a4,4 0 0,0 0,-8zM12,10a2,2 0 1,1 0,4a2,2 0 0,1 0,-4z" +
                "M11,2h2l0.3,2.1a7.6,7.6 0 0,1 1.9,0.8l1.7,-1.3l1.4,1.4l-1.3,1.7a7.6,7.6 0 0,1 0.8,1.9" +
                "l2.2,0.3v2l-2.1,0.3a7.6,7.6 0 0,1 -0.8,1.9l1.3,1.7l-1.4,1.4l-1.7,-1.3a7.6,7.6 0 0,1 -1.9,0.8" +
                "L13,22h-2l-0.3,-2.1a7.6,7.6 0 0,1 -1.9,-0.8l-1.7,1.3l-1.4,-1.4l1.3,-1.7a7.6,7.6 0 0,1 -0.8,-1.9" +
                "L4,13v-2l2.1,-0.3a7.6,7.6 0 0,1 0.8,-1.9L5.6,7.1l1.4,-1.4l1.7,1.3a7.6,7.6 0 0,1 1.9,-0.8L11,2z",
        )
    }

    val ChevronRight: ImageVector by lazy {
        icon("ChevronRight", "M9.3,6l1.4,-1.4l7.4,7.4l-7.4,7.4l-1.4,-1.4l6,-6z")
    }

    val ArrowBack: ImageVector by lazy {
        icon("ArrowBack", "M11,5.6l1.4,1.4l-4,4h11.6v2h-11.6l4,4l-1.4,1.4l-6.4,-6.4z")
    }

    val Close: ImageVector by lazy {
        icon(
            "Close",
            "M6.4,5l5.6,5.6l5.6,-5.6l1.4,1.4l-5.6,5.6l5.6,5.6l-1.4,1.4l-5.6,-5.6l-5.6,5.6l-1.4,-1.4" +
                "l5.6,-5.6l-5.6,-5.6z",
        )
    }

    val Check: ImageVector by lazy {
        icon("Check", "M9.5,16.2l-4.3,-4.3l1.4,-1.4l2.9,2.9l6.9,-6.9l1.4,1.4z")
    }

    val Clear: ImageVector by lazy {
        icon(
            "Clear",
            "M12,3a9,9 0 1,0 0,18a9,9 0 0,0 0,-18zM12,5a7,7 0 1,1 0,14a7,7 0 0,1 0,-14z" +
                "M15.3,7.3l1.4,1.4l-2.3,2.3l2.3,2.3l-1.4,1.4l-2.3,-2.3l-2.3,2.3l-1.4,-1.4l2.3,-2.3l-2.3,-2.3" +
                "l1.4,-1.4l2.3,2.3z",
        )
    }

    val DeleteSweep: ImageVector by lazy {
        icon(
            "DeleteSweep",
            "M9,3h6v2h5v2h-16v-2h5zM6,9h12l-1,12h-10zM8.1,11l0.7,8h6.4l0.7,-8z" +
                "M2,9h2l0.3,3h-2zM2,14h2v2h-2z",
        )
    }

    val Security: ImageVector by lazy {
        icon(
            "Security",
            "M12,2l8,3v7c0,5 -3.4,8.5 -8,10c-4.6,-1.5 -8,-5 -8,-10v-7zM12,4.2l-6,2.2v5.6" +
                "c0,3.9 2.5,6.6 6,7.9c3.5,-1.3 6,-4 6,-7.9v-5.6zM11,8h2v5h-2zM11,14.5h2v2h-2z",
        )
    }

    val CloudDownload: ImageVector by lazy {
        icon(
            "CloudDownload",
            "M7,18a4.5,4.5 0 0,1 -0.4,-9a6,6 0 0,1 11.4,1.2a4,4 0 0,1 -0.5,7.8h-3.5v-2h3.4" +
                "a2,2 0 0,0 0.1,-4l-1,-0.1l-0.2,-1a4,4 0 0,0 -7.7,-0.6l-0.3,0.9l-0.9,0.1" +
                "a2.5,2.5 0 0,0 0.3,4.9h2.3v2zM11,12.5l-3,3l-1.4,-1.4l1.6,-1.6h-3.7v-2h3.7" +
                "l-1.6,-1.6l1.4,-1.4l3,3l1.4,1z",
        )
    }

    val Palette: ImageVector by lazy {
        icon(
            "Palette",
            "M12,3a9,9 0 1,0 0,18c1.1,0 1.9,-0.9 1.9,-2c0,-0.5 -0.2,-1 -0.5,-1.3" +
                "c-0.3,-0.4 -0.5,-0.8 -0.5,-1.2c0,-1.1 0.9,-2 2,-2h1.6c2.5,0 4.5,-2 4.5,-4.5" +
                "C21,5.6 17,3 12,3zM12,5c4,0 7,1.9 7,4c0,1.4 -1.1,2.5 -2.5,2.5h-1.6" +
                "c-2.2,0 -4,1.8 -4,4c0,0.8 0.2,1.5 0.6,2.1c-1.7,-0.6 -3,-2.1 -3,-4.1" +
                "c0,-2.8 1.6,-5 4,-6c0,-1.4 -1,-2.5 -0.5,-2.5zM7.5,9a1.3,1.3 0 1,1 0,2.6a1.3,1.3 0 0,1 0,-2.6z" +
                "M10.5,6.5a1.3,1.3 0 1,1 0,2.6a1.3,1.3 0 0,1 0,-2.6z" +
                "M14.5,6.5a1.3,1.3 0 1,1 0,2.6a1.3,1.3 0 0,1 0,-2.6z",
        )
    }

    val Tune: ImageVector by lazy {
        icon(
            "Tune",
            "M4,5h9v2h-9zM17,4a3,3 0 0,1 3,3a3,3 0 0,1 -3,3a3,3 0 0,1 -3,-3a3,3 0 0,1 3,-3z" +
                "M17,6a1,1 0 1,0 0,2a1,1 0 0,0 0,-2zM4,11h3v2h-3zM9,10a3,3 0 0,1 3,3a3,3 0 0,1 -3,3" +
                "a3,3 0 0,1 -3,-3a3,3 0 0,1 3,-3zM9,12a1,1 0 1,0 0,2a1,1 0 0,0 0,-2zM16,11h4v2h-4z" +
                "M4,17h9v2h-9zM17,16a3,3 0 0,1 3,3a3,3 0 0,1 -3,3a3,3 0 0,1 -3,-3a3,3 0 0,1 3,-3z" +
                "M17,18a1,1 0 1,0 0,2a1,1 0 0,0 0,-2z",
        )
    }

    val Lock: ImageVector by lazy {
        icon(
            "Lock",
            "M12,2a5,5 0 0,0 -5,5v3h-1v12h12v-12h-1v-3a5,5 0 0,0 -5,-5zM12,4a3,3 0 0,1 3,3v3h-6v-3" +
                "a3,3 0 0,1 3,-3zM8,10h8v10h-8z",
        )
    }

    val Info: ImageVector by lazy {
        icon(
            "Info",
            "M12,2a10,10 0 1,0 0,20a10,10 0 0,0 0,-20zM12,4a8,8 0 1,1 0,16a8,8 0 0,1 0,-16z" +
                "M11,10h2v7h-2zM11,7h2v2h-2z",
        )
    }

    val DoneAll: ImageVector by lazy {
        icon(
            "DoneAll",
            "M1,13.4l1.4,-1.4l3.2,3.2l-1.4,1.4zM6.6,14.2l7.1,-7.1l1.4,1.4l-7.1,7.1z" +
                "M12,16.6l1.4,-1.4l1.4,1.4l6.2,-6.2l-1.4,-1.4l-4.8,4.8l-1.4,-1.4l-1.4,1.4z",
        )
    }

    val HourglassEmpty: ImageVector by lazy {
        icon(
            "Hourglass",
            "M6,2h12v2h-1v3.5c0,1.6 -0.8,3 -2,3.9c1.2,0.9 2,2.3 2,3.9v3.7h1v2h-12v-2h1v-3.7" +
                "c0,-1.6 0.8,-3 2,-3.9c-1.2,-0.9 -2,-2.3 -2,-3.9v-3.5h-1zM9,4v3.5c0,1.4 1.1,2.5 3,3.4" +
                "c1.9,-0.9 3,-2 3,-3.4v-3.5zM9,15.6v3.4h6v-3.4c0,-1.4 -1.1,-2.5 -3,-3.4" +
                "c-1.9,0.9 -3,2 -3,3.4z",
        )
    }

    val ErrorOutline: ImageVector by lazy {
        icon(
            "Error",
            "M12,2a10,10 0 1,0 0,20a10,10 0 0,0 0,-20zM12,4a8,8 0 1,1 0,16a8,8 0 0,1 0,-16z" +
                "M11,6.5h2v7h-2zM11,15h2v2h-2z",
        )
    }

    val DarkMode: ImageVector by lazy {
        icon(
            "DarkMode",
            "M12.3,2.2a9,9 0 1,0 9.5,10.4a7.5,7.5 0 0,1 -9.5,-10.4z",
        )
    }

    val LightMode: ImageVector by lazy {
        icon(
            "LightMode",
            "M12,7a5,5 0 1,0 0,10a5,5 0 0,0 0,-10zM12,9a3,3 0 1,1 0,6a3,3 0 0,1 0,-6z" +
                "M11,1h2v3h-2zM11,20h2v3h-2zM1,11h3v2h-3zM20,11h3v2h-3z" +
                "M4.2,5.6l1.4,-1.4l2.1,2.1l-1.4,1.4zM16.3,17.7l1.4,-1.4l2.1,2.1l-1.4,1.4z" +
                "M4.2,18.4l2.1,-2.1l1.4,1.4l-2.1,2.1zM16.3,6.3l2.1,-2.1l1.4,1.4l-2.1,2.1z",
        )
    }

    val ExpandMore: ImageVector by lazy {
        icon("ExpandMore", "M12,15.4l-6.4,-6.4l1.4,-1.4l5,5l5,-5l1.4,1.4z")
    }

    val ExpandLess: ImageVector by lazy {
        icon("ExpandLess", "M12,8.6l6.4,6.4l-1.4,1.4l-5,-5l-5,5l-1.4,-1.4z")
    }

    val Delete: ImageVector by lazy {
        icon(
            "Delete",
            "M9,3h6v2h5v2h-16v-2h5zM6,8.5h12l-0.9,12.5h-10.2z",
        )
    }

    /** Wifi + shield: the VPN Hunter glyph. */
    val Vpn: ImageVector by lazy {
        icon(
            "Vpn",
            "M12,2l7.5,2.8v6.4c0,4.7 -3.2,8 -7.5,9.3c-4.3,-1.3 -7.5,-4.6 -7.5,-9.3v-6.4z" +
                "M12,4.2l-5.5,2.1v5.2c0,3.5 2.2,6 5.5,7.2c3.3,-1.2 5.5,-3.7 5.5,-7.2v-5.2z" +
                "M12,7.6a3.1,3.1 0 0,1 3.1,3.1h-1.6a1.5,1.5 0 1,0 -1.5,1.5v1.6a3.1,3.1 0 0,1 0,-6.2z" +
                "M11.2,13.4h1.6v2.6h-1.6z",
        )
    }

    /** Arrow up out of a tray: "open the installer". */
    val OpenInNew: ImageVector by lazy {
        icon(
            "OpenInNew",
            "M14,3h7v7h-2v-3.6l-6.3,6.3l-1.4,-1.4l6.3,-6.3h-3.6z" +
                "M5,5h6v2h-4v10h10v-4h2v4a2,2 0 0,1 -2,2h-10a2,2 0 0,1 -2,-2v-10a2,2 0 0,1 2,-2z",
        )
    }

    /** Phone with a sweep arrow: "app settings / device section". */
    val PhoneSettings: ImageVector by lazy {
        icon(
            "PhoneSettings",
            "M7,2h10a2,2 0 0,1 2,2v6h-2v-4h-10v12h4v2h-4a2,2 0 0,1 -2,-2v-14a2,2 0 0,1 2,-2z" +
                "M17.5,14a3.5,3.5 0 1,0 0,7a3.5,3.5 0 0,0 0,-7zM17.5,16h1.2l0.3,0.9" +
                "a2.6,2.6 0 0,1 0.8,0.5l0.9,-0.3l0.6,1l-0.7,0.7v0.9l0.7,0.7l-0.6,1l-0.9,-0.3" +
                "a2.6,2.6 0 0,1 -0.8,0.5l-0.3,0.9h-1.2l-0.3,-0.9a2.6,2.6 0 0,1 -0.8,-0.5l-0.9,0.3" +
                "l-0.6,-1l0.7,-0.7v-0.9l-0.7,-0.7l0.6,-1l0.9,0.3a2.6,2.6 0 0,1 0.8,-0.5z" +
                "M17.5,17.6a0.9,0.9 0 1,1 0,1.8a0.9,0.9 0 0,1 0,-1.8z",
        )
    }

    /** Question mark in a circle: the help / walkthrough entry. */
    val Help: ImageVector by lazy {
        icon(
            "Help",
            "M12,2a10,10 0 1,0 0,20a10,10 0 0,0 0,-20zM12,4a8,8 0 1,1 0,16a8,8 0 0,1 0,-16z" +
                "M12,7a2.6,2.6 0 1,1 -0.1,5.2h-1v1.3h1.9v-1.1a1.2,1.2 0 0,0 0.6,-0.2" +
                "a4.4,4.4 0 1,0 -6.1,-4h1.9a2.5,2.5 0 0,1 2.8,-1.2zM11,15.4h2v2h-2z",
        )
    }

    val Shield: ImageVector by lazy { Security }


    val List: ImageVector by lazy {
        icon(
            "List",
            "M3,5h3v3h-3zM3,10.5h3v3h-3zM3,16h3v3h-3zM8,6h13v2h-13zM8,11.5h13v2h-13zM8,17h13v2h-13z",
        )
    }
}
