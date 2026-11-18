package ir.irandetector.app.detection

import ir.irandetector.app.core.PackageNames
import ir.irandetector.app.core.TextNormalizer
import ir.irandetector.app.data.IranianCompanies
import ir.irandetector.app.data.IranianStores
import ir.irandetector.app.data.KnownApp
import ir.irandetector.app.data.Verification
import ir.irandetector.app.domain.ConfidenceTier
import ir.irandetector.app.domain.DetectionResult
import ir.irandetector.app.domain.DetectionSignal
import ir.irandetector.app.domain.InstalledApp
import ir.irandetector.app.domain.SignalType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Multi-signal Iranian-application detection engine.
 *
 * Design rules (deliberately conservative):
 *  - A package id prefix such as `ir.` is *never* used as evidence on its own.
 *  - Persian text inside the app is never used as evidence on its own.
 *  - Every point in the final score is backed by a [DetectionSignal] that names
 *    the actual data it came from, so the UI can always explain itself.
 *  - Scores are additive and capped; the cap keeps a single weak signal from
 *    being inflated into a "verified" classification.
 */
@Singleton
class DetectionEngine @Inject constructor() {

    /** Result of scoring: score + the evidence that produced it. */
    data class Assessment(
        val score: Int,
        val signals: List<DetectionSignal>,
        val matchedRecord: KnownApp?,
        val recordVerification: Verification?,
    ) {
        val tier: ConfidenceTier get() = ConfidenceTier.of(score)
    }

    private data class Candidate(
        val record: KnownApp,
        val signals: MutableList<DetectionSignal> = mutableListOf(),
        var packageExact: Boolean = false,
        var packageAlternative: Boolean = false,
        var certificate: Boolean = false,
    ) {
        val score: Int get() = signals.sumOf { it.points }.coerceAtMost(100)
    }

    /**
     * Detect one installed application against the whole database.
     * Cheap by construction: the package index is an O(1) hit, everything else
     * only runs when the fast path missed.
     */
    fun detect(
        app: InstalledApp,
        database: List<KnownApp>,
        index: DetectionIndex = DetectionIndex.build(database),
        /** aHash of the installed icon, when the caller was able to read it. */
        iconHash: String? = null,
    ): Assessment {
        // ---- 1. Fast path: package id (exact) ------------------------------
        // Note: an icon hash can corroborate a record whose package id does not
        // match, so when one is available the exhaustive pass below runs and picks
        // the highest scoring record (ties are impossible because the exact-package
        // signal dominates).
        if (iconHash == null) {
            index.byPackage[app.packageName]?.let { return score(app, it, index) }
        }

        // ---- 2. Slow path: score every plausible record --------------------
        var best: Assessment? = null
        for (record in database) {
            val assessment = score(app, record, index, iconHash)
            if (assessment.signals.isEmpty()) continue
            if (best == null || assessment.score > best!!.score) best = assessment
        }
        return best ?: Assessment(0, emptyList(), null, null)
    }

    /** Convenience for the scan pipeline. */
    fun toResult(app: InstalledApp, assessment: Assessment): DetectionResult = DetectionResult(
        app = app,
        confidence = assessment.score,
        tier = confidenceTierOf(assessment),
        signals = assessment.signals,
        matchedRecord = assessment.matchedRecord,
        recordVerification = assessment.recordVerification,
    )

    private fun confidenceTierOf(assessment: Assessment): ConfidenceTier {
        val tier = ConfidenceTier.of(assessment.score)
        // A record that is only "uncertain" in the database can never produce a
        // "verified" classification, no matter how many weak signals line up.
        return if (assessment.recordVerification == Verification.UNCERTAIN &&
            tier == ConfidenceTier.VERIFIED
        ) {
            ConfidenceTier.PROBABLE
        } else {
            tier
        }
    }

    // ------------------------------------------------------------------ core

    fun score(
        app: InstalledApp,
        record: KnownApp,
        index: DetectionIndex,
        iconHash: String? = null,
    ): Assessment {
        val candidate = Candidate(record)

        addPackageSignals(app, candidate)
        addCertificateSignals(app, candidate, index)
        addNameSignals(app, candidate)
        addDeveloperSignals(app, candidate, index)
        addInstallerSignals(app, candidate)
        addDomainSignals(app, candidate)
        addIconSignal(iconHash, candidate)

        // Nothing matched: no signals, therefore no claim.
        if (candidate.signals.isEmpty()) {
            return Assessment(0, emptyList(), null, null)
        }

        val adjusted = applyCorroboration(candidate)
        return Assessment(
            score = adjusted.score,
            signals = adjusted.signals.toList(),
            matchedRecord = record,
            recordVerification = record.verification,
        )
    }

    // --------------------------------------------------------------- signals

    private fun addPackageSignals(app: InstalledApp, c: Candidate) {
        val pkg = app.packageName.lowercase()
        val record = c.record
        if (pkg == record.packageName.lowercase()) {
            c.packageExact = true
            c.signals += DetectionSignal(
                type = SignalType.PACKAGE_EXACT,
                points = if (record.verification == Verification.VERIFIED) 62 else 55,
                explanation = "Package id ${record.packageName} is the documented id of ${record.nameEn}.",
            )
            return
        }
        val alt = record.alternativePackages.firstOrNull { it.equals(pkg, ignoreCase = true) }
        if (alt != null) {
            c.packageAlternative = true
            c.signals += DetectionSignal(
                type = SignalType.PACKAGE_ALTERNATIVE,
                points = 50,
                explanation = "Package id $alt is a historical/alternative id of ${record.nameEn}.",
            )
        }
    }

    private fun addCertificateSignals(app: InstalledApp, c: Candidate, index: DetectionIndex) {
        val record = c.record
        val sha256Hit = app.signatureSha256.firstOrNull { digest ->
            record.certificateSha256.any { it.equals(digest, ignoreCase = true) }
        }
        val sha1Hit = app.signatureSha1.firstOrNull { digest ->
            record.certificateSha1.any { it.equals(digest, ignoreCase = true) }
        }
        if (sha256Hit != null || sha1Hit != null) {
            c.certificate = true
            val shown = (sha256Hit ?: sha1Hit)!!.take(23)
            c.signals += DetectionSignal(
                type = SignalType.CERTIFICATE,
                points = 60,
                explanation = "Signing certificate $shown… matches the certificate recorded for ${record.nameEn}.",
            )
            return
        }
        // Same signing key as *another* database record: the developer identity is
        // shared (e.g. two apps signed by the same Iranian studio).
        val shared = app.signatureSha256.firstNotNullOfOrNull { digest ->
            index.byCertificate[digest.uppercase()]
        }
        if (shared != null) {
            c.signals += DetectionSignal(
                type = SignalType.CERTIFICATE,
                points = 34,
                explanation = "Signed with the same key as ${shared.nameEn} (${shared.developer}), a known Iranian publisher.",
            )
        }
    }

    private fun addNameSignals(app: InstalledApp, c: Candidate) {
        val record = c.record
        val label = TextNormalizer.normalize(app.label)
        val labelStripped = TextNormalizer.stripNoiseWords(app.label)
        if (label.isEmpty()) return

        // English / transliterated names (also covers the "Snapp!" vs "snapp" case).
        val englishNames = buildList {
            add(record.nameEn)
            addAll(record.nameAliases.filter { !TextNormalizer.containsPersianLetters(it) })
            addAll(record.alternativePackages.map { PackageNames.brandSegment(it) })
        }.filter { it.isNotBlank() }

        val englishHit = englishNames.firstOrNull { name ->
            val n = TextNormalizer.normalize(name)
            n.length >= 3 && (n == label || n == labelStripped)
        }
        if (englishHit != null) {
            c.signals += DetectionSignal(
                type = SignalType.NAME_EN,
                points = 26,
                explanation = "Application name \"${app.label}\" matches the English name \"${record.nameEn}\".",
            )
        }

        // Persian names, including aliases and the noise-stripped variants.
        val persianNames = buildList {
            if (record.nameFa.isNotBlank()) add(record.nameFa)
            addAll(record.nameAliases.filter(TextNormalizer::containsPersianLetters))
        }
        val persianHit = persianNames.firstOrNull { name ->
            val n = TextNormalizer.normalize(name)
            val s = TextNormalizer.stripNoiseWords(name)
            n.length >= 2 && (n == label || n == labelStripped || (s.isNotEmpty() && (s == label || s == labelStripped)))
        }
        if (persianHit != null) {
            c.signals += DetectionSignal(
                type = SignalType.NAME_FA,
                points = 28,
                explanation = "Application name \"${app.label}\" matches the Persian name «$persianHit».",
            )
        } else {
            // Fuzzy: tolerate small spelling/transliteration differences only.
            val fuzzyEn = englishNames.maxOfOrNull { TextNormalizer.similarity(it, label) } ?: 0.0
            val fuzzyFa = persianNames.maxOfOrNull { TextNormalizer.similarity(it, label) } ?: 0.0
            val best = maxOf(fuzzyEn, fuzzyFa)
            val containmentEn = englishNames.maxOfOrNull { TextNormalizer.tokenContainment(it, label) } ?: 0.0
            if (best >= 0.86 || containmentEn >= 0.9) {
                c.signals += DetectionSignal(
                    type = SignalType.NAME_FUZZY,
                    points = 16,
                    explanation = "Application name \"${app.label}\" is a close variant of \"${record.nameEn}\" " +
                        "(similarity ${(maxOf(best, containmentEn) * 100).toInt()}%).",
                )
            } else if (best in 0.72..0.859) {
                c.signals += DetectionSignal(
                    type = SignalType.NAME_FUZZY,
                    points = 9,
                    explanation = "Application name \"${app.label}\" loosely resembles \"${record.nameEn}\" " +
                        "(similarity ${(best * 100).toInt()}%).",
                )
            }
        }
    }

    private fun addDeveloperSignals(app: InstalledApp, c: Candidate, index: DetectionIndex) {
        val record = c.record
        val ns = PackageNames.namespace(app.packageName)
        val recordNamespaces = record.allPackages.map(PackageNames::namespace).filter { it.isNotBlank() }
        if (recordNamespaces.any { it.equals(ns, ignoreCase = true) }) {
            c.signals += DetectionSignal(
                type = SignalType.PACKAGE_NAMESPACE,
                points = 18,
                explanation = "Package namespace $ns is owned by ${record.developer} (${record.nameEn}).",
            )
        }

        // Developer identity from known Iranian companies, matched against the label,
        // package and any installer hint available.
        val haystack = buildString {
            append(app.label).append(' ')
            append(app.packageName).append(' ')
            app.installerPackage?.let { append(it).append(' ') }
        }
        val company = IranianCompanies.lookup(haystack)
        if (company != null) {
            val alreadyCovered = record.developer.equals(company.name, ignoreCase = true)
            c.signals += DetectionSignal(
                type = SignalType.DEVELOPER,
                points = if (alreadyCovered) 20 else 14,
                explanation = "Application metadata references ${company.name}, a known Iranian company.",
            )
        }

        // Domain signal: the installer package or namespaces sometimes embed the
        // developer domain (e.g. `com.digikala`), which we can corroborate.
        index.byDomain.entries.firstOrNull { (domain, owner) ->
            owner !== record && app.packageName.contains(domain.substringBefore('.')) &&
                domain.length >= 6
        }?.let { (domain, owner) ->
            c.signals += DetectionSignal(
                type = SignalType.DOMAIN,
                points = 12,
                explanation = "Package id contains the developer domain $domain registered by ${owner.developer}.",
            )
        }
    }

    private fun addInstallerSignals(app: InstalledApp, c: Candidate) {
        val installer = app.installerPackage
        if (IranianStores.isIranianStore(installer)) {
            val storeName = IranianStores.displayName(installer) ?: installer!!
            c.signals += DetectionSignal(
                type = SignalType.INSTALLER,
                points = 30,
                explanation = "Installed from $storeName, an Iranian application marketplace.",
            )
        } else if (installer.isNullOrBlank()) {
            // Unknown installer is *absence* of data, not evidence. Sideloading is
            // common in Iran but also common everywhere else: never scored.
            return
        }
    }

    private fun addDomainSignals(app: InstalledApp, c: Candidate) {
        val record = c.record
        if (record.domains.isEmpty()) return
        val pkg = app.packageName.lowercase()
        val hit = record.domains.firstOrNull { domain ->
            val brand = domain.substringBefore('.').lowercase()
            brand.length >= 4 && pkg.contains(brand)
        }
        if (hit != null && c.signals.none { it.type == SignalType.DOMAIN }) {
            c.signals += DetectionSignal(
                type = SignalType.DOMAIN,
                points = 10,
                explanation = "Package id contains \"${hit.substringBefore('.')}\", the domain of ${record.developer}.",
            )
        }
    }

    /**
     * Icon fingerprint signal. Only fires when BOTH sides have a hash, which in the
     * shipped configuration never happens (the database carries no verified icon
     * hashes) — so it can never fabricate evidence on its own.
     */
    private fun addIconSignal(installedHash: String?, c: Candidate) {
        val recordHash = c.record.iconHash ?: return
        if (installedHash.isNullOrBlank()) return
        val distance = IconHash.distance(installedHash, recordHash)
        if (distance > IconHash.DISTANCE_MAX) return
        val strong = distance <= IconHash.DISTANCE_STRONG
        c.signals += DetectionSignal(
            type = SignalType.ICON,
            points = if (strong) 24 else 10,
            explanation = if (strong) {
                "Launcher icon matches the reference icon of ${c.record.nameEn} " +
                    "(${IconHash.BITS - distance}/${IconHash.BITS} bits)."
            } else {
                "Launcher icon is visually close to the reference icon of ${c.record.nameEn} " +
                    "(${IconHash.BITS - distance}/${IconHash.BITS} bits)."
            },
        )
    }

    // ----------------------------------------------------------- calibration

    /**
     * Confidence calibration:
     *  1. Duplicate/overlapping evidence is damped so that ten weak name-ish
     *     signals cannot outrank one certificate match.
     *  2. A certificate WITHOUT a package match is capped (a shared key is not
     *     proof for this specific app).
     *  3. A lone signal is capped so it stays "uncertain".
     */
    private fun applyCorroboration(c: Candidate): Assessment {
        var score = c.signals.sumOf { it.points }

        val hasPackage = c.packageExact || c.packageAlternative
        val types = c.signals.map { it.type }.toSet()

        // Damping: once two independent strong signals agree, extra weak signals
        // add less. Prevents 100% scores built purely from name variants.
        if (hasPackage && c.certificate) {
            score -= 12 // both anchors agree; avoid saturating at 100 instantly
        }
        if (hasPackage) {
            // Name signals become supporting evidence, not separate proof.
            val namePoints = c.signals.filter {
                it.type in setOf(
                    SignalType.NAME_EN,
                    SignalType.NAME_FA,
                    SignalType.NAME_FUZZY,
                    SignalType.ICON,
                )
            }.sumOf { it.points }
            if (namePoints > 0) score -= (namePoints * 0.35).toInt()
        }
        if (c.certificate && !hasPackage) {
            score = minOf(score, 72)
        }
        if (types.size == 1 && !hasPackage && !c.certificate) {
            // A single non-anchor signal can never exceed "uncertain", with one
            // exception: an Iranian marketplace install is direct provenance
            // evidence, so it may reach the *bottom* of the uncertain band (30)
            // but never "probable" (55).
            score = if (types.single() == SignalType.INSTALLER) minOf(score, 34) else minOf(score, 29)
        }

        val capped = score.coerceIn(0, 100)
        return Assessment(
            score = capped,
            signals = c.signals.toList(),
            matchedRecord = c.record,
            recordVerification = c.record.verification,
        )
    }

    private fun String.substringBefore(delimiter: Char): String = substringBefore(delimiter.toString(), this)
}

/**
 * Pre-computed lookup tables. Building this once per scan keeps detection at
 * roughly O(1) for the common case (package id present in the database) instead
 * of comparing every installed package against every record.
 */
class DetectionIndex private constructor(
    val byPackage: Map<String, KnownApp>,
    val byCertificate: Map<String, KnownApp>,
    val byDomain: Map<String, KnownApp>,
    val records: List<KnownApp>,
) {
    companion object {
        fun build(records: List<KnownApp>): DetectionIndex {
            val byPackage = HashMap<String, KnownApp>(records.size * 2)
            val byCert = HashMap<String, KnownApp>()
            val byDomain = HashMap<String, KnownApp>()
            for (record in records) {
                for (pkg in record.allPackages) {
                    byPackage.putIfAbsent(pkg.lowercase(), record)
                }
                for (cert in record.certificateSha256) {
                    byCert.putIfAbsent(cert.uppercase(), record)
                }
                for (domain in record.domains) {
                    byDomain.putIfAbsent(domain.lowercase(), record)
                }
            }
            return DetectionIndex(byPackage, byCert, byDomain, records)
        }
    }
}
