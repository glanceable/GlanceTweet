package org.glanceable.tweet;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

public class AboutDialogFragment extends DialogFragment {
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
//        final SpannableString s = new SpannableString(getActivity().getText(R.string.dialog_about));
//        Linkify.addLinks(s, Linkify.WEB_URLS);
        builder.setMessage(R.string.dialog_about)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }
                });

        // Create the AlertDialog object and return it
        Dialog dialog =  builder.create();
        return dialog;
    }
}