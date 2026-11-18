package ir.irandetector.app.data

import ir.irandetector.app.core.Json
import ir.irandetector.app.core.Json.boolOrNull
import ir.irandetector.app.core.Json.doubleOrNull
import ir.irandetector.app.core.Json.intOrNull
import ir.irandetector.app.core.Json.longOrNull
import ir.irandetector.app.core.Json.string
import ir.irandetector.app.core.Json.stringList
import ir.irandetector.app.core.Json.stringOrNull
import ir.irandetector.app.core.PackageNames

/**
 * One known Iranian application in the detection database.
 *
 * [verification] expresses how much the project trusts the record itself
 * (NOT how sure we are that the installed app matches it — that is the
 * runtime confidence score produced by [ir.irandetector.app.detection]).
 */
data class KnownApp(
    val id: Long = 0,
    val packageName: String,
    val alternativePackages: List<String> = emptyList(),
    val nameEn: String,
    val nameFa: String,
    val developer: String,
    val developerAliases: List<String> = emptyList(),
    val website: String? = null,
    val domains: List<String> = emptyList(),
    val certificateSha256: List<String> = emptyList(),
    val certificateSha1: List<String> = emptyList(),
    val knownInstallers: List<String> = emptyList(),
    val category: String = "OTHER",
    val verification: Verification = Verification.PROBABLE,
    /** Optional 64-bit average-hash of the launcher icon, hex encoded. */
    val iconHash: String? = null,
    val iconReference: String? = null,
    val nameAliases: List<String> = emptyList(),
    val notes: String? = null,
    val updatedAt: Long = 0L,
    /** true when the row came from the bundled asset or a remote update rather than the user. */
    val fromDatabase: Boolean = true,
) {
    val allPackages: List<String> get() = (listOf(packageName) + alternativePackages).distinct()

    fun toJson(): String = buildString {
        append('{')
        append("\"id\":").append(id).append(',')
        append("\"packageName\":\"").append(Json.escape(packageName)).append("\",")
        append("\"alternativePackages\":").append(Json.stringArray(alternativePackages)).append(',')
        append("\"nameEn\":\"").append(Json.escape(nameEn)).append("\",")
        append("\"nameFa\":\"").append(Json.escape(nameFa)).append("\",")
        append("\"developer\":\"").append(Json.escape(developer)).append("\",")
        append("\"developerAliases\":").append(Json.stringArray(developerAliases)).append(',')
        website?.let { append("\"website\":\"").append(Json.escape(it)).append("\",") }
        append("\"domains\":").append(Json.stringArray(domains)).append(',')
        append("\"certificateSha256\":").append(Json.stringArray(certificateSha256)).append(',')
        append("\"certificateSha1\":").append(Json.stringArray(certificateSha1)).append(',')
        append("\"knownInstallers\":").append(Json.stringArray(knownInstallers)).append(',')
        append("\"category\":\"").append(Json.escape(category)).append("\",")
        append("\"verification\":\"").append(verification.name).append("\",")
        iconHash?.let { append("\"iconHash\":\"").append(Json.escape(it)).append("\",") }
        iconReference?.let { append("\"iconReference\":\"").append(Json.escape(it)).append("\",") }
        append("\"nameAliases\":").append(Json.stringArray(nameAliases)).append(',')
        notes?.let { append("\"notes\":\"").append(Json.escape(it)).append("\",") }
        append("\"updatedAt\":").append(updatedAt)
        append('}')
    }

    companion object {
        /** Returns null for rows that cannot be trusted (invalid package id). */
        fun fromJson(value: Json.Value, defaultFromDatabase: Boolean = true): KnownApp? {
            val packageName = value.stringOrNull("packageName")?.trim().orEmpty()
            if (!PackageNames.isValid(packageName)) return null
            val verification = Verification.fromWire(value.string("verification", "PROBABLE"))
            return KnownApp(
                id = value.longOrNull("id") ?: 0L,
                packageName = packageName,
                alternativePackages = value.stringList("alternativePackages")
                    .filter(PackageNames::isValid)
                    .distinct(),
                nameEn = value.string("nameEn").ifBlank { packageName },
                nameFa = value.string("nameFa"),
                developer = value.string("developer"),
                developerAliases = value.stringList("developerAliases"),
                website = value.stringOrNull("website")?.takeIf { it.isNotBlank() },
                domains = value.stringList("domains"),
                certificateSha256 = value.stringList("certificateSha256").map(String::uppercase),
                certificateSha1 = value.stringList("certificateSha1").map(String::uppercase),
                knownInstallers = value.stringList("knownInstallers"),
                category = value.string("category", "OTHER"),
                verification = verification,
                iconHash = value.stringOrNull("iconHash")?.takeIf { it.isNotBlank() },
                iconReference = value.stringOrNull("iconReference")?.takeIf { it.isNotBlank() },
                nameAliases = value.stringList("nameAliases"),
                notes = value.stringOrNull("notes"),
                updatedAt = value.longOrNull("updatedAt") ?: 0L,
                fromDatabase = value.boolOrNull("fromDatabase") ?: defaultFromDatabase,
            )
        }
    }
}

enum class Verification {
    /** Package id, developer and (usually) certificate confirmed from primary sources. */
    VERIFIED,
    /** Strong evidence but one signal (e.g. certificate) is not independently confirmed. */
    PROBABLE,
    /** Weak/supplementary evidence; shown to the user as "uncertain". */
    UNCERTAIN;

    companion object {
        fun fromWire(raw: String?): Verification = when (raw?.trim()?.uppercase()) {
            "VERIFIED", "CONFIRMED", "TRUSTED" -> VERIFIED
            "UNCERTAIN", "UNKNOWN", "WEAK" -> UNCERTAIN
            else -> PROBABLE
        }
    }
}

/** Known Iranian app marketplaces — used by the installer-source signal. */
object IranianStores {
    const val BAZAAR = "com.farsitel.bazaar"
    const val MYKET = "ir.mservices.market"
    const val SIBAPP = "com.sibapp" // SibApp (سیب‌اپ) client package id
    const val MYIRANAPPS = "com.myiranapps.android"
    const val CHARKHONEH = "ir.charkhoneh"

    val all: Set<String> = setOf(BAZAAR, MYKET, SIBAPP, MYIRANAPPS, CHARKHONEH)

    fun isIranianStore(installer: String?): Boolean =
        installer != null && installer.lowercase() in all

    fun displayName(installer: String?): String? = when (installer?.lowercase()) {
        BAZAAR -> "Cafe Bazaar"
        MYKET -> "Myket"
        SIBAPP -> "SibApp"
        MYIRANAPPS -> "MyIranApps"
        CHARKHONEH -> "Charkhoneh"
        else -> null
    }
}

/**
 * Developer / corporate identity signals. A hit here alone is a *supporting*
 * signal (weighted below package matches) because developer names appear in
 * many third-party apps and store listings.
 */
object IranianCompanies {
    data class Company(
        val name: String,
        val aliases: List<String>,
        val domains: List<String>,
    )

    val all: List<Company> = listOf(
        Company("Digikala Group", listOf("digikala", "دیجی‌کالا", "دیجی کالا", "دیجیکالا"), listOf("digikala.com", "digikala.ir")),
        Company("SabaIdea", listOf("sabaidea", "صبا ایده", "صباایده", "saba idea"), listOf("sabaidea.com", "filimo.com", "aparat.com")),
        Company("Cafe Bazaar (Farsitel)", listOf("cafebazaar", "cafe bazaar", "farsitel", "کافه بازار", "بازار"), listOf("cafebazaar.ir", "farsitel.com")),
        Company("Myket (MServices)", listOf("myket", "mservices", "مایکت"), listOf("myket.ir")),
        Company("Snapp Group", listOf("snapp", "اسنپ", "snapp cab", "snapp! group"), listOf("snapp.ir", "snapp.cab")),
        Company("Tapsi (Tap30)", listOf("tapsi", "tap30", "تپسی", "تپ سی"), listOf("tapsi.ir", "tapsi.cab")),
        Company("Divar", listOf("divar", "دیوار", "congenial"), listOf("divar.ir")),
        Company("Sheypoor (Ahoura Net Tejarat)", listOf("sheypoor", "شیپور", "net tejarat ahoura", "نت تجارت اهورا"), listOf("sheypoor.com")),
        Company("Neshan (Rajman)", listOf("neshan", "rajman", "نشان", "راجمان", "rajman information structures"), listOf("neshan.org", "rajman.co")),
        Company("Balad", listOf("balad", "بلد"), listOf("balad.ir")),
        Company("Bale (Sadad Informatics)", listOf("bale", "sadad", "بله", "بانک ملی"), listOf("bale.ai", "sadad.co.ir")),
        Company("Rubika", listOf("rubika", "روبیکا", "resaneh"), listOf("rubika.ir")),
        Company("Eitaa", listOf("eitaa", "ایتا"), listOf("eitaa.com")),
        Company("iGap (Kian Iranian)", listOf("igap", "kian iranian", "آی‌گپ", "کیان ایرانی"), listOf("igap.net", "igap.ir")),
        Company("Namava", listOf("namava", "نماوا"), listOf("namava.ir")),
        Company("Alibaba Travels", listOf("alibaba", "علی‌بابا", "alibaba travels"), listOf("alibaba.ir")),
        Company("Snappfood", listOf("snappfood", "اسنپ‌فود", "اسنپ فود"), listOf("snappfood.ir")),
        Company("SibApp", listOf("sibapp", "سیب‌اپ"), listOf("sibapp.com")),
        Company("Aparat", listOf("aparat", "آپارات"), listOf("aparat.com")),
        Company("Zarinpal", listOf("zarinpal", "زرین‌پال"), listOf("zarinpal.com")),
    )

    fun lookup(text: String): Company? {
        val normalized = text.lowercase()
        if (normalized.isBlank()) return null
        return all.firstOrNull { company ->
            company.aliases.any { alias -> normalized.contains(alias) }
        }
    }
}

/** Public suffixes we treat as developer owned domains. */
object Domains {
    fun commonSuffix(a: String, b: String): Boolean = a.endsWith(b) || b.endsWith(a)
}
