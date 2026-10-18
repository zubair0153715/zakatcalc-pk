package com.zakatcalc.pk

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {

    companion object {
        const val PREFS = "zakatcalc_prefs"
        const val KEY_LANG = "lang"
        const val KEY_GOLD = "gold_per_gram"
        const val KEY_SILVER = "silver_per_gram"
        const val KEY_UPDATED = "rates_updated"
        const val KEY_MANUAL = "rates_manual"
        // Bundled fallback rates (PKR/gram), verified Oct 2026 — user-editable in app
        const val GOLD_FALLBACK = 37100.0
        const val SILVER_FALLBACK = 510.0
        const val GOLD_NISAB_GRAMS = 87.48
        const val SILVER_NISAB_GRAMS = 612.36
        const val OZ_TO_GRAMS = 31.1034768
    }

    private lateinit var prefs: SharedPreferences
    private val fmt: NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
        maximumFractionDigits = 0
    }

    override fun attachBaseContext(newBase: Context) {
        val p = newBase.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lang = p.getString(KEY_LANG, "en") ?: "en"
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        findViewById<Button>(R.id.btnLangEn).setOnClickListener { switchLang("en") }
        findViewById<Button>(R.id.btnLangUr).setOnClickListener { switchLang("ur") }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { refreshRates() }
        findViewById<Button>(R.id.btnEditRates).setOnClickListener { showEditRatesDialog() }
        findViewById<Button>(R.id.btnCalculate).setOnClickListener { calculate() }

        displayRates()
    }

    private fun switchLang(lang: String) {
        if ((prefs.getString(KEY_LANG, "en") ?: "en") == lang) return
        prefs.edit().putString(KEY_LANG, lang).apply()
        recreate()
    }

    // ---------- Rates ----------

    private fun goldRate() = prefs.getFloat(KEY_GOLD, GOLD_FALLBACK.toFloat()).toDouble()
    private fun silverRate() = prefs.getFloat(KEY_SILVER, SILVER_FALLBACK.toFloat()).toDouble()

    private fun displayRates() {
        val g = goldRate()
        val s = silverRate()
        findViewById<TextView>(R.id.tvGoldRate).text =
            "${getString(R.string.gold_per_gram)}: Rs ${fmt.format(g)}"
        findViewById<TextView>(R.id.tvSilverRate).text =
            "${getString(R.string.silver_per_gram)}: Rs ${fmt.format(s)}"
        findViewById<TextView>(R.id.tvNisabGold).text =
            "${getString(R.string.nisab_gold)}: Rs ${fmt.format(g * GOLD_NISAB_GRAMS)}"
        findViewById<TextView>(R.id.tvNisabSilver).text =
            "${getString(R.string.nisab_silver)}: Rs ${fmt.format(s * SILVER_NISAB_GRAMS)}"
        val ts = prefs.getLong(KEY_UPDATED, 0)
        val manual = prefs.getBoolean(KEY_MANUAL, false)
        findViewById<TextView>(R.id.tvRatesUpdated).text = if (ts > 0 && !manual) {
            val d = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ts))
            getString(R.string.rates_updated, d)
        } else {
            getString(R.string.rates_fallback)
        }
    }

    private fun refreshRates() {
        Toast.makeText(this, "…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                // gold-api.com: free, no key. XAU/XAG in USD per troy oz.
                val xau = JSONObject(get("https://api.gold-api.com/price/XAU")).getDouble("price")
                val xag = JSONObject(get("https://api.gold-api.com/price/XAG")).getDouble("price")
                // open.er-api.com: free USD base rates, no key
                val usdPkr = JSONObject(get("https://open.er-api.com/v6/latest/USD"))
                    .getJSONObject("rates").getDouble("PKR")
                val goldPerGram = xau / OZ_TO_GRAMS * usdPkr
                val silverPerGram = xag / OZ_TO_GRAMS * usdPkr
                prefs.edit()
                    .putFloat(KEY_GOLD, goldPerGram.toFloat())
                    .putFloat(KEY_SILVER, silverPerGram.toFloat())
                    .putLong(KEY_UPDATED, System.currentTimeMillis())
                    .putBoolean(KEY_MANUAL, false)
                    .apply()
                runOnUiThread {
                    displayRates()
                    Toast.makeText(this, R.string.toast_rates_ok, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, R.string.toast_rates_fail, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun get(urlStr: String): String {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("User-Agent", "ZakatCalcPK/1.0")
        }
        conn.inputStream.bufferedReader().use { return it.readText() }
    }

    private fun showEditRatesDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_rates, null)
        val etGold = view.findViewById<EditText>(R.id.etDlgGold)
        val etSilver = view.findViewById<EditText>(R.id.etDlgSilver)
        etGold.setText(goldRate().toLong().toString())
        etSilver.setText(silverRate().toLong().toString())
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_rates_title)
            .setView(view)
            .setPositiveButton(R.string.dialog_save) { _, _ ->
                val g = etGold.text.toString().toDoubleOrNull()
                val s = etSilver.text.toString().toDoubleOrNull()
                if (g != null && g > 0 && s != null && s > 0) {
                    prefs.edit()
                        .putFloat(KEY_GOLD, g.toFloat())
                        .putFloat(KEY_SILVER, s.toFloat())
                        .putLong(KEY_UPDATED, System.currentTimeMillis())
                        .putBoolean(KEY_MANUAL, true)
                        .apply()
                    displayRates()
                } else {
                    Toast.makeText(this, R.string.err_number, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    // ---------- Calculation ----------

    private fun num(id: Int): Double {
        val t = findViewById<EditText>(id).text.toString().trim()
        if (t.isEmpty()) return 0.0
        return t.toDoubleOrNull()
            ?: throw IllegalArgumentException(getString(R.string.err_number))
    }

    private fun calculate() {
        try {
            val cash = num(R.id.etCash)
            val goldG = num(R.id.etGold)
            val silverG = num(R.id.etSilver)
            val business = num(R.id.etBusiness)
            val liabilities = num(R.id.etLiabilities)
            val harvest = num(R.id.etHarvest)

            val g = goldRate()
            val s = silverRate()
            val wealth = cash + goldG * g + silverG * s + business - liabilities
            val nisab = minOf(g * GOLD_NISAB_GRAMS, s * SILVER_NISAB_GRAMS)

            val natural = findViewById<RadioButton>(R.id.rbNatural).isChecked
            val ushr = harvest * if (natural) 0.10 else 0.05

            findViewById<View>(R.id.cardResult).visibility = View.VISIBLE
            findViewById<TextView>(R.id.tvWealth).text =
                getString(R.string.result_wealth, fmt.format(maxOf(wealth, 0.0)))
            findViewById<TextView>(R.id.tvNisab).text =
                getString(R.string.result_nisab, fmt.format(nisab))
            val tvBelow = findViewById<TextView>(R.id.tvBelow)
            if (wealth >= nisab && wealth > 0) {
                tvBelow.visibility = View.GONE
                findViewById<TextView>(R.id.tvZakat).text =
                    getString(R.string.result_zakat, fmt.format(wealth * 0.025))
            } else {
                tvBelow.visibility = View.VISIBLE
                findViewById<TextView>(R.id.tvZakat).text =
                    getString(R.string.result_zakat, fmt.format(0.0))
            }
            findViewById<TextView>(R.id.tvUshr).text =
                getString(R.string.result_ushr, fmt.format(ushr))
        } catch (e: IllegalArgumentException) {
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
    }
}
