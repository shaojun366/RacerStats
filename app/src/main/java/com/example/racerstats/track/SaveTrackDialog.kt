package com.example.racerstats.track

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import com.example.racerstats.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SaveTrackDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_save_track, null)
        
        val trackNameInput = view.findViewById<EditText>(R.id.etTrackName)

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.new_track)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val trackName = trackNameInput.text.toString()
                (parentFragment as? TrackFragment)?.onSaveTrack(trackName)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }
}