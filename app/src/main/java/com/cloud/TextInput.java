/*package com.cloud;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.widget.EditText;
import android.widget.FrameLayout;

public class TextInput {
    public static void show(final Activity activity,
                            final String title,
                            final String current,
                            final boolean isPassword) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final EditText editText = new EditText(activity);
                if (isPassword) {
                    editText.setInputType(
                        InputType.TYPE_CLASS_TEXT |
                        InputType.TYPE_TEXT_VARIATION_PASSWORD);
                } else {
                    editText.setInputType(InputType.TYPE_CLASS_TEXT);
                }
                if (current != null && !current.isEmpty()) {
                    editText.setText(current);
                    editText.setSelection(editText.getText().length());
                }

                // Wrap in a FrameLayout so we can add horizontal padding.
                FrameLayout container = new FrameLayout(activity);
                DisplayMetrics dm = activity.getResources().getDisplayMetrics();
                int pad = (int)(20 * dm.density);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
                params.setMargins(pad, 0, pad, 0);
                container.addView(editText, params);

                new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setView(container)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            nativeOnTextResult(editText.getText().toString());
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            nativeOnTextResult(null);
                        }
                    })
                    .setCancelable(false)
                    .show();
            }
        });
    }

    // Implemented in android_keyboard.c via JNI name mangling.
    private static native void nativeOnTextResult(String text);
}
*/