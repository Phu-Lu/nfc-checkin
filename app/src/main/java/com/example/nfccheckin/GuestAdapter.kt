package com.example.nfccheckin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class GuestAdapter(
    private var guests: MutableList<Guest>,
    private val onEdit: (Guest) -> Unit,
    private val onDelete: (Guest) -> Unit
) : RecyclerView.Adapter<GuestAdapter.VH>() {

    fun setGuests(list: List<Guest>) {
        guests.clear()
        guests.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_guest, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val g = guests[pos]
        h.tvName.text = g.ho_ten
        h.tvId.text = "ID: ${g.nfc_id}"
        h.tvGreeting.text = g.loi_chao
        h.tvNote.text = g.ghi_chu
        h.tvNote.visibility = if (g.ghi_chu.isNotEmpty()) View.VISIBLE else View.GONE
        h.btnEdit.setOnClickListener { onEdit(g) }
        h.btnDelete.setOnClickListener { onDelete(g) }
    }

    override fun getItemCount() = guests.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tv_name)
        val tvId: TextView = v.findViewById(R.id.tv_id)
        val tvGreeting: TextView = v.findViewById(R.id.tv_greeting)
        val tvNote: TextView = v.findViewById(R.id.tv_note)
        val btnEdit: ImageButton = v.findViewById(R.id.btn_edit)
        val btnDelete: ImageButton = v.findViewById(R.id.btn_delete)
    }
}
