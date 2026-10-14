package `in`.c1ph3rj.scanly.domain.text

import `in`.c1ph3rj.scanly.domain.model.DocumentKind
import `in`.c1ph3rj.scanly.domain.search.TextFolding

/**
 * Guesses what a page is from its text, entirely on-device, using weighted cue phrases.
 * Deliberately conservative: it returns null unless the evidence clearly points one way,
 * because a wrong label is worse than none.
 */
object DocumentKindClassifier {
    private const val MIN_SCORE = 4
    private const val MIN_LEAD = 2
    /** Titles and key fields sit near the top; this also bounds the work per page. */
    private const val MAX_CHARS = 4_000

    private data class Cue(val phrase: String, val weight: Int)

    private val cues: Map<DocumentKind, List<Cue>> = mapOf(
        DocumentKind.INVOICE to listOf(
            Cue("tax invoice", 5), Cue("invoice", 4), Cue("invoice no", 2), Cue("bill to", 2),
            Cue("amount due", 2), Cue("due date", 1), Cue("gstin", 2), Cue("subtotal", 1),
            Cue("hsn", 1), Cue("payment terms", 1),
        ),
        DocumentKind.RECEIPT to listOf(
            Cue("receipt", 4), Cue("thank you for shopping", 3), Cue("cash", 1), Cue("change due", 2),
            Cue("card ending", 2), Cue("total paid", 2), Cue("qty", 1), Cue("cashier", 2),
            Cue("transaction id", 1),
        ),
        DocumentKind.CONTRACT to listOf(
            Cue("agreement", 4), Cue("contract", 3), Cue("hereinafter", 3), Cue("hereby", 1),
            Cue("the parties", 2), Cue("terms and conditions", 1), Cue("in witness whereof", 3),
            Cue("landlord", 2), Cue("tenant", 2), Cue("shall", 1), Cue("lessee", 2), Cue("lessor", 2),
        ),
        DocumentKind.STATEMENT to listOf(
            Cue("statement of account", 5), Cue("account statement", 5), Cue("bank statement", 5),
            Cue("opening balance", 3), Cue("closing balance", 3), Cue("account number", 1),
            Cue("ifsc", 1), Cue("withdrawal", 1), Cue("deposit", 1),
        ),
        DocumentKind.LETTER to listOf(
            Cue("dear", 3), Cue("yours sincerely", 3), Cue("yours faithfully", 3), Cue("sincerely", 2),
            Cue("kind regards", 2), Cue("to whom it may concern", 4), Cue("subject", 1),
        ),
        DocumentKind.CERTIFICATE to listOf(
            Cue("certificate", 4), Cue("this is to certify", 5), Cue("certified that", 3),
            Cue("awarded", 2), Cue("has successfully completed", 3),
        ),
        DocumentKind.ID_DOCUMENT to listOf(
            Cue("date of birth", 3), Cue("passport", 4), Cue("driving licence", 5), Cue("driving license", 5),
            Cue("nationality", 2), Cue("aadhaar", 5), Cue("identity card", 4), Cue("permanent account number", 5),
            Cue("dob", 2), Cue("valid until", 1),
            // Indian IDs: these phrases survive OCR better than the card's own title.
            Cue("government of india", 3), Cue("unique identification authority", 5),
            Cue("income tax department", 3), Cue("election commission", 4), Cue("father", 1),
        ),
        DocumentKind.FORM to listOf(
            Cue("application form", 5), Cue("signature of applicant", 4), Cue("please fill", 3),
            Cue("form no", 2), Cue("for office use only", 3), Cue("tick", 1),
        ),
        DocumentKind.RESUME to listOf(
            Cue("curriculum vitae", 5), Cue("resume", 3), Cue("work experience", 3), Cue("education", 1),
            Cue("skills", 1), Cue("professional summary", 3), Cue("references available", 2),
        ),
    )

    fun classify(text: String): DocumentKind? {
        if (text.isBlank()) return null
        val haystack = TextFolding.fold(text.take(MAX_CHARS))
        val scores = cues.mapValues { (_, kindCues) ->
            kindCues.sumOf { cue -> if (containsPhrase(haystack, cue.phrase)) cue.weight else 0 }
        }
        val ranked = scores.entries.sortedByDescending { it.value }
        val best = ranked.first()
        val runnerUp = ranked.getOrNull(1)?.value ?: 0
        return best.key.takeIf { best.value >= MIN_SCORE && best.value - runnerUp >= MIN_LEAD }
    }

    /** Whole-word containment, so "dob" doesn't fire inside "adobe". */
    internal fun containsPhrase(haystack: String, phrase: String): Boolean {
        var from = 0
        while (true) {
            val index = haystack.indexOf(phrase, from)
            if (index < 0) return false
            val end = index + phrase.length
            val startsWord = index == 0 || !haystack[index - 1].isLetterOrDigit()
            val endsWord = end == haystack.length || !haystack[end].isLetterOrDigit()
            if (startsWord && endsWord) return true
            from = index + 1
        }
    }
}
