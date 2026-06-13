package com.example.nfccheckin

import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import org.json.JSONArray

class GuestManagerActivity : AppCompatActivity() {

    private lateinit var adapter: GuestAdapter
    private lateinit var tvEmpty: TextView
    private val guestList = mutableListOf<Guest>()

    // NFC khi dialog đang mở
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var pendingIntent: PendingIntent
    private lateinit var intentFilters: Array<IntentFilter>

    // Giữ reference đến EditText ID của dialog đang mở
    private var activeNfcIdField: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guest_manager)

        tvEmpty = findViewById(R.id.tv_empty)

        adapter = GuestAdapter(
            guestList,
            onEdit = { showGuestDialog(it) },
            onDelete = { confirmDelete(it) }
        )

        findViewById<RecyclerView>(R.id.recycler_view).apply {
            layoutManager = LinearLayoutManager(this@GuestManagerActivity)
            adapter = this@GuestManagerActivity.adapter
        }

        findViewById<FloatingActionButton>(R.id.fab_add).setOnClickListener {
            showGuestDialog(null)
        }

        // Setup NFC foreground dispatch cho activity này
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        val nfcIntent = Intent(this, GuestManagerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        pendingIntent = PendingIntent.getActivity(this, 0, nfcIntent, PendingIntent.FLAG_MUTABLE)
        intentFilters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        )

        // Nếu được mở từ MainActivity với NFC ID mới → mở dialog luôn
        intent.getStringExtra("new_nfc_id")?.let { nfcId ->
            showGuestDialog(null, prefillId = nfcId)
        }

        loadGuests()
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFilters, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    // Khi dialog đang mở và chạm thẻ → điền ID vào ô
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val action = intent.action
        if (action == NfcAdapter.ACTION_TAG_DISCOVERED ||
            action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
            action == NfcAdapter.ACTION_TECH_DISCOVERED) {

            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            tag?.let {
                val nfcId = bytesToHex(it.id)
                if (activeNfcIdField != null) {
                    // Dialog đang mở → điền vào ô ID
                    activeNfcIdField!!.setText(nfcId)
                    Toast.makeText(this, "✅ Đã đọc ID: $nfcId", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ── Load danh sách ───────────────────────────────────────

    private fun loadGuests() {
        lifecycleScope.launch {
            try {
                val arr: JSONArray = ApiClient.getGuests()
                val list = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Guest(
                        nfc_id   = o.getString("nfc_id"),
                        ho_ten   = o.getString("ho_ten"),
                        loi_chao = o.getString("loi_chao"),
                        ghi_chu  = o.optString("ghi_chu", "")
                    )
                }
                adapter.setGuests(list)
                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                Toast.makeText(this@GuestManagerActivity,
                    "Lỗi tải danh sách: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Dialog thêm/sửa ─────────────────────────────────────

    private fun showGuestDialog(existing: Guest?, prefillId: String? = null) {
        val isEdit = existing != null
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_guest, null)

        val etId       = view.findViewById<EditText>(R.id.et_nfc_id)
        val etName     = view.findViewById<EditText>(R.id.et_ho_ten)
        val etGreeting = view.findViewById<EditText>(R.id.et_loi_chao)
        val etNote     = view.findViewById<EditText>(R.id.et_ghi_chu)

        if (isEdit && existing != null) {
            etId.setText(existing.nfc_id)
            etId.isEnabled = false
            etName.setText(existing.ho_ten)
            etGreeting.setText(existing.loi_chao)
            etNote.setText(existing.ghi_chu)
        } else if (prefillId != null) {
            // ID điền sẵn từ NFC quét
            etId.setText(prefillId)
            etId.isEnabled = false // Đã có ID rồi, không cần sửa
        }

        // Gán field này để onNewIntent biết điền vào đâu
        activeNfcIdField = if (!isEdit && prefillId == null) etId else null

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (isEdit) "✏️ Sửa khách" else "➕ Thêm khách")
            .setView(view)
            .setPositiveButton("Lưu") { _, _ ->
                val id       = etId.text.toString().trim()
                val name     = etName.text.toString().trim()
                val greeting = etGreeting.text.toString().trim()
                val note     = etNote.text.toString().trim()

                if (id.isEmpty() || name.isEmpty() || greeting.isEmpty()) {
                    Toast.makeText(this, "Vui lòng điền đủ thông tin", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val guest = Guest(id, name, greeting, note)
                lifecycleScope.launch {
                    try {
                        if (isEdit) {
                            ApiClient.updateGuest(guest)
                            Toast.makeText(this@GuestManagerActivity,
                                "✅ Đã cập nhật", Toast.LENGTH_SHORT).show()
                        } else {
                            ApiClient.addGuest(guest)
                            Toast.makeText(this@GuestManagerActivity,
                                "✅ Đã thêm khách", Toast.LENGTH_SHORT).show()
                        }
                        loadGuests()
                    } catch (e: Exception) {
                        Toast.makeText(this@GuestManagerActivity,
                            "❌ ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Huỷ", null)
            .setOnDismissListener {
                // Dialog đóng → clear NFC field reference
                activeNfcIdField = null
            }
            .show()
    }

    // ── Xác nhận xoá ────────────────────────────────────────

    private fun confirmDelete(g: Guest) {
        AlertDialog.Builder(this)
            .setTitle("Xoá khách")
            .setMessage("Xoá \"${g.ho_ten}\" khỏi danh sách?")
            .setPositiveButton("Xoá") { _, _ ->
                lifecycleScope.launch {
                    try {
                        ApiClient.deleteGuest(g.nfc_id)
                        Toast.makeText(this@GuestManagerActivity,
                            "🗑️ Đã xoá", Toast.LENGTH_SHORT).show()
                        loadGuests()
                    } catch (e: Exception) {
                        Toast.makeText(this@GuestManagerActivity,
                            "❌ ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    // ── Helpers ──────────────────────────────────────────────

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }
}
