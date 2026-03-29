package com.example.nfccheckin

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var pendingIntent: PendingIntent
    private lateinit var intentFilters: Array<IntentFilter>

    private lateinit var tvStatus: TextView
    private lateinit var tvLastId: TextView
    private lateinit var tvLastName: TextView
    private lateinit var cardResult: CardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus   = findViewById(R.id.tv_status)
        tvLastId   = findViewById(R.id.tv_last_id)
        tvLastName = findViewById(R.id.tv_last_name)
        cardResult = findViewById(R.id.card_result)

        // Setup NFC
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            tvStatus.text = "❌ Thiết bị không hỗ trợ NFC"
            return
        }

        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)

        intentFilters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        )

        findViewById<View>(R.id.btn_manage).setOnClickListener {
            startActivity(Intent(this, GuestManagerActivity::class.java))
        }

        tvStatus.text = "✅ Sẵn sàng — đưa thẻ NFC vào đọc"
    }

    // ── NFC Foreground Dispatch ──────────────────────────────

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFilters, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    // ── Nhận NFC intent ─────────────────────────────────────

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val action = intent.action
        if (action == NfcAdapter.ACTION_TAG_DISCOVERED ||
            action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
            action == NfcAdapter.ACTION_TECH_DISCOVERED) {

            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            tag?.let { handleNfcId(bytesToHex(it.id)) }
        }
    }

    // ── Xử lý NFC ID ────────────────────────────────────────

    private fun handleNfcId(nfcId: String) {
        tvStatus.text = "⏳ Đang xử lý: $nfcId"
        tvLastId.text = "ID: $nfcId"
        cardResult.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val result = ApiClient.checkin(nfcId)
                if (result.getBoolean("success")) {
                    val guest = result.getJSONObject("guest")
                    val name = guest.getString("ho_ten")
                    tvLastName.text = "✅ $name"
                    tvStatus.text = "✅ Check-in: $name"
                } else {
                    tvLastName.text = "❓ Thẻ chưa đăng ký"
                    tvStatus.text = "❓ Không tìm thấy khách"
                }
            } catch (e: Exception) {
                tvLastName.text = "❌ Lỗi kết nối server"
                tvStatus.text = "❌ ${e.message}"
                Toast.makeText(this@MainActivity, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }
}
