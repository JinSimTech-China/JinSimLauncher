package com.jinsim.account.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jinsim.account.AccountType
import com.jinsim.account.BoundAccount
import com.tungsten.fcl.R

/** 附属账号列表：左侧类型徽标 + 名称/副标题 + 默认标记 + 右侧更多 */
class AccountAdapter(
    private val onClick: (BoundAccount) -> Unit,
    private val onMore: (BoundAccount, View) -> Unit
) : RecyclerView.Adapter<AccountAdapter.Holder>() {

    private val items = mutableListOf<BoundAccount>()

    fun submit(list: List<BoundAccount>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_jinsim_account, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val badge: TextView = itemView.findViewById(R.id.badge)
        private val name: TextView = itemView.findViewById(R.id.name)
        private val sub: TextView = itemView.findViewById(R.id.sub)
        private val defaultTag: TextView = itemView.findViewById(R.id.default_tag)
        private val more: TextView = itemView.findViewById(R.id.more)

        fun bind(account: BoundAccount) {
            badge.text = when (account.type) {
                AccountType.JINSIM -> "JS"
                AccountType.MICROSOFT -> "MS"
                AccountType.OFFLINE -> "OF"
            }
            name.text = account.displayName.ifBlank { account.playerName }
            sub.text = when (account.type) {
                AccountType.OFFLINE -> "离线 · ${account.uuid.take(8)}"
                AccountType.MICROSOFT -> "微软 · ${account.uuid.ifBlank { "待同步" }.take(8)}"
                AccountType.JINSIM -> "Jinsim · ${account.playerName}"
            }
            defaultTag.visibility = if (account.isDefault) View.VISIBLE else View.GONE
            itemView.setOnClickListener { onClick(account) }
            more.setOnClickListener { v -> onMore(account, v) }
        }
    }
}
