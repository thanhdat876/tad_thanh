package com.example

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppListAdapter(
    private val appList: List<AppInfo>,
    private val onItemClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgIcon: ImageView = view.findViewById(R.id.img_app_icon)
        val tvName: TextView = view.findViewById(R.id.tv_app_name)
        val tvPackage: TextView = view.findViewById(R.id.tv_package_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val appInfo = appList[position]
        holder.tvName.text = appInfo.appName
        holder.tvPackage.text = appInfo.packageName
        
        if (appInfo.appIcon != null) {
            holder.imgIcon.setImageDrawable(appInfo.appIcon)
        } else {
            holder.imgIcon.setImageResource(android.R.drawable.sym_def_app_icon)
        }

        holder.itemView.setOnClickListener {
            onItemClick(appInfo)
        }
    }

    override fun getItemCount(): Int = appList.size
}
