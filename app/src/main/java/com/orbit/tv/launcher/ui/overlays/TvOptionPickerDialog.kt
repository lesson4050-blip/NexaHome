package com.orbit.tv.launcher.ui.overlays

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.DialogOptionPickerBinding
import com.orbit.tv.launcher.databinding.ItemOptionPickerBinding

class TvOptionPickerDialog : DialogFragment() {

    data class OptionItem(
        val id: String,
        val title: String,
        val subtitle: String? = null,
        val isSelected: Boolean = false
    )

    private var _binding: DialogOptionPickerBinding? = null
    private val binding get() = _binding!!

    private var titleText: String = ""
    private var options: List<OptionItem> = emptyList()
    private var onSelectListener: ((OptionItem) -> Unit)? = null

    companion object {
        const val TAG = "TvOptionPickerDialog"

        fun show(
            fragmentManager: FragmentManager,
            title: String,
            options: List<OptionItem>,
            onSelect: (OptionItem) -> Unit
        ) {
            val dialog = TvOptionPickerDialog().apply {
                this.titleText = title
                this.options = options
                this.onSelectListener = onSelect
            }
            dialog.show(fragmentManager, TAG)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogOptionPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvDialogTitle.text = titleText

        val adapter = OptionAdapter(options) { selected ->
            onSelectListener?.invoke(selected)
            dismiss()
        }
        binding.rvDialogOptions.adapter = adapter

        val selectedIndex = options.indexOfFirst { it.isSelected }
        if (selectedIndex >= 0) {
            binding.rvDialogOptions.post {
                binding.rvDialogOptions.scrollToPosition(selectedIndex)
                val viewHolder = binding.rvDialogOptions.findViewHolderForAdapterPosition(selectedIndex)
                viewHolder?.itemView?.requestFocus()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class OptionAdapter(
        private val items: List<OptionItem>,
        private val onClick: (OptionItem) -> Unit
    ) : RecyclerView.Adapter<OptionAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemOptionPickerBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemOptionPickerBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            with(holder.binding) {
                tvOptionTitle.text = item.title
                if (item.subtitle.isNullOrBlank()) {
                    tvOptionSubtitle.visibility = View.GONE
                } else {
                    tvOptionSubtitle.text = item.subtitle
                    tvOptionSubtitle.visibility = View.VISIBLE
                }

                ivOptionCheck.visibility = if (item.isSelected) View.VISIBLE else View.GONE
                containerOptionItem.isSelected = item.isSelected

                TvFocusHelper.bindFocusScale(root, 1.02f)

                root.setOnClickListener {
                    onClick(item)
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
