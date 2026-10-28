package com.example.racerstats.track

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.racerstats.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SaveTrackDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_save_track, null)
        val input = view.findViewById<EditText>(R.id.etTrackName)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.new_track).setView(view)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isBlank()) input.error = getString(R.string.error_track_name_empty)
                else {
                    (parentFragment as? TrackFragment)?.onSaveTrack(name)
                    dismiss()
                }
            }
        }
        return dialog
    }
}
