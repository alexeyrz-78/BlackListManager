package com.example.huaweiblocker
import com.example.huaweiblocker.R
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DeviceAdapter(
    private var devices: List<Device>,
    private val onBlockClick: (Device) -> Unit // Вот здесь мы исправили тип клика!
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvDeviceName)
        val tvMac: TextView = view.findViewById(R.id.tvDeviceMac)
        val btnAction: Button = view.findViewById(R.id.btnBlockAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.tvName.text = device.name
        holder.tvMac.text = device.mac

        // Стилизуем кнопку под текущий статус
        if (device.isBlocked) {
            holder.btnAction.text = "Allow"
            holder.btnAction.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#E57373")) // Красный
        } else {
            holder.btnAction.text = "Block"
            holder.btnAction.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#81C784")) // Зеленый
        }

        // Обработка нажатия на кнопку
        holder.btnAction.setOnClickListener {
            onBlockClick(device) // Передаем конкретный девайс, по которому кликнули
        }
    }

    override fun getItemCount(): Int = devices.size

    // Метод для обновления списка устройств на экране
    fun updateList(newDevices: List<Device>) {
        devices = newDevices
        notifyDataSetChanged()
    }
}
