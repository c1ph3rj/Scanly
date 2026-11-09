package `in`.c1ph3rj.scanly.core.common

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import `in`.c1ph3rj.scanly.R
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Resolves string resources outside Compose: ViewModels, repositories, use cases, event lambdas and
 * enum labels. Inside a composable, use `stringResource` so text follows configuration changes.
 *
 * Reads through the application context with the in-app language ([AppLocales]) applied. Call sites must
 * resolve text when it is shown, not cache it in a long-lived object such as an enum or `object`.
 *
 * JVM unit tests have no Android resources, so [get] falls back to reading
 * `src/main/res/values/strings.xml` and keeps English assertions working.
 */
object AppStrings {
    @Volatile
    private var context: Context? = null

    /** [context] with the current in-app language applied, refreshed on configuration changes. */
    @Volatile
    private var localized: Context? = null

    fun init(context: Context) {
        this.context = context.applicationContext
        localized = null
    }

    /** Drops the cached localized context after the app language changes. */
    fun onLocaleChanged() {
        localized = null
    }

    private fun resources(): Context? {
        localized?.let { return it }
        val base = context ?: return null
        return AppLocales.wrap(base).also { localized = it }
    }

    fun plural(@PluralsRes id: Int, quantity: Int, vararg args: Any?): String {
        val ctx = resources() ?: return JvmFallback.plural(id, quantity, args)
        return ctx.resources.getQuantityString(id, quantity, *args)
    }

    fun get(@StringRes id: Int, vararg args: Any?): String {
        val ctx = resources() ?: return JvmFallback.get(id, args)
        return if (args.isEmpty()) ctx.getString(id) else ctx.getString(id, *args)
    }

    /** strings.xml lookup for JVM tests, by resource name via the generated `R.string` fields. */
    private object JvmFallback {
        private val names: Map<Int, String> by lazy {
            R.string::class.java.fields.associate { it.getInt(null) to it.name }
        }

        private val pluralNames: Map<Int, String> by lazy {
            R.plurals::class.java.fields.associate { it.getInt(null) to it.name }
        }

        private val pluralValues: Map<String, Map<String, String>> by lazy { loadPlurals() }

        /** English rules only: "one" for 1, "other" otherwise. */
        fun plural(id: Int, quantity: Int, args: Array<out Any?>): String {
            val name = pluralNames[id] ?: return "#$id"
            val forms = pluralValues[name] ?: return name
            val text = forms[if (quantity == 1) "one" else "other"] ?: forms["other"] ?: return name
            return if (args.isEmpty()) text else String.format(Locale.ROOT, text, *args)
        }

        private fun stringsFile(): File? = listOf(
            "src/main/res/values/strings.xml",
            "app/src/main/res/values/strings.xml",
        ).map(::File).firstOrNull(File::exists)

        private fun loadPlurals(): Map<String, Map<String, String>> {
            val file = stringsFile() ?: return emptyMap()
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val nodes = doc.getElementsByTagName("plurals")
            return buildMap {
                for (i in 0 until nodes.length) {
                    val node = nodes.item(i)
                    val name = node.attributes.getNamedItem("name")?.nodeValue ?: continue
                    val items = node.childNodes
                    val forms = buildMap {
                        for (j in 0 until items.length) {
                            val item = items.item(j)
                            if (item.nodeName != "item") continue
                            val quantity = item.attributes.getNamedItem("quantity")?.nodeValue ?: continue
                            put(quantity, unescape(item.textContent))
                        }
                    }
                    put(name, forms)
                }
            }
        }

        private val values: Map<String, Pair<String, Boolean>> by lazy { load() }

        fun get(id: Int, args: Array<out Any?>): String {
            val name = names[id] ?: return "#$id"
            val (text, formatted) = values[name] ?: return name
            return if (args.isEmpty() || !formatted) text else String.format(Locale.ROOT, text, *args)
        }

        private fun load(): Map<String, Pair<String, Boolean>> {
            val file = stringsFile() ?: return emptyMap()
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val nodes = doc.getElementsByTagName("string")
            return buildMap {
                for (i in 0 until nodes.length) {
                    val node = nodes.item(i)
                    val attrs = node.attributes
                    val name = attrs.getNamedItem("name")?.nodeValue ?: continue
                    val formatted = attrs.getNamedItem("formatted")?.nodeValue != "false"
                    put(name, unescape(node.textContent) to formatted)
                }
            }
        }

        private fun unescape(raw: String): String {
            var s = raw
            if (s.length >= 2 && s.startsWith('"') && s.endsWith('"')) s = s.substring(1, s.length - 1)
            val out = StringBuilder()
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    when (val d = s[i + 1]) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        else -> out.append(d)
                    }
                    i += 2
                } else {
                    out.append(c)
                    i++
                }
            }
            return out.toString()
        }
    }
}

/** Shorthand for [AppStrings.plural]. */
fun appPluralString(@PluralsRes id: Int, quantity: Int, vararg args: Any?): String =
    AppStrings.plural(id, quantity, *args)

/** Shorthand for [AppStrings.get]. */
fun appString(@StringRes id: Int, vararg args: Any?): String = AppStrings.get(id, *args)
