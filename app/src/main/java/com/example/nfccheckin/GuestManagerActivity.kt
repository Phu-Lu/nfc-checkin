package com.example.nfccheckin

import android.app.AlertDialog
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

    private lateinit var guestAdapter: GuestAdapter
    private lateinit var tvEmpty: TextView
    private val guestList = mutableListOf<Guest>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guest_manager)

        tvEmpty = findViewById(R.id.tv_empty)

        guestAdapter = GuestAdapter(
            guestList,
            onEdit = { showGuestDialog(it) },
            onDelete = { confirmDelete(it) }
        )

        findViewById<RecyclerView>(R.id.recycler_view).apply {
            layoutManager = LinearLayoutManager(this@GuestManagerActivity)
            adapter = this@GuestManagerActivity.guestAdapter
        }

        findViewById<FloatingActionButton>(R.id.fab_add).setOnClickListener {
            showGuestDialog(null)
        }

        loadGuests()
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
                guestAdapter.setGuests(list)
                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                Toast.makeText(this@GuestManagerActivity, "Lỗi tải danh sách: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Dialog thêm/sửa ─────────────────────────────────────

    private fun showGuestDialog(existing: Guest?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_guest, null)

        val etId       = view.findViewById<EditText>(R.id.et_nfc_id)
        val etName     = view.findViewById<EditText>(R.id.et_ho_ten)
        val etGreeting = view.findViewById<EditText>(R.id.et_loi_chao)
        val etNote     = view.findViewById<EditText>(R.id.et_ghi_chu)

        if (existing != null) {
            etId.setText(existing.nfc_id)
            etId.isEnabled = false
            etName.setText(existing.ho_ten)
            etGreeting.setText(existing.loi_chao)
            etNote.setText(existing.ghi_chu)
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing != null) "✏️ Sửa khách" else "➕ Thêm khách")
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
                        if (existing != null) {
                            ApiClient.updateGuest(guest)
                            Toast.makeText(this@GuestManagerActivity, "✅ Đã cập nhật", Toast.LENGTH_SHORT).show()
                        } else {
                            ApiClient.addGuest(guest)
                            Toast.makeText(this@GuestManagerActivity, "✅ Đã thêm khách", Toast.LENGTH_SHORT).show()
                        }
                        loadGuests()
                    } catch (e: Exception) {
                        Toast.makeText(this@GuestManagerActivity, "❌ ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Huỷ", null)
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
                        Toast.makeText(this@GuestManagerActivity, "🗑️ Đã xoá", Toast.LENGTH_SHORT).show()
                        loadGuests()
                    } catch (e: Exception) {
                        Toast.makeText(this@GuestManagerActivity, "❌ ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }
}
