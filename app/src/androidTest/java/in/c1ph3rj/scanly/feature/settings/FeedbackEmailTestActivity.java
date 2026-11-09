package in.c1ph3rj.scanly.feature.settings;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

/** Separate-UID fixture uses only platform classes; the test APK has no standalone Kotlin runtime. */
public class FeedbackEmailTestActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean success = false;
        try {
            ArrayList<Uri> images = getIntent().getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            success = images != null && images.size() == 2;
            if (success) {
                for (Uri uri : images) {
                    try (InputStream input = getContentResolver().openInputStream(uri)) {
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        byte[] buffer = new byte[32];
                        int count;
                        while (input != null && (count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                        success &= Arrays.equals(new byte[] {1, 2, 3, 4}, bytes.toByteArray());
                    }
                }
                success &= "info@c1ph3rj.in".equals(getIntent().getStringArrayExtra(Intent.EXTRA_EMAIL)[0]);
                success &= "Scanly attachment test".equals(getIntent().getStringExtra(Intent.EXTRA_SUBJECT));
            }
        } catch (Exception failure) {
            success = false;
        }
        sendBroadcast(new Intent("in.c1ph3rj.scanly.FEEDBACK_IMAGE_READ_TEST")
            .setPackage("in.c1ph3rj.scanly")
            .putExtra("token", getIntent().getStringExtra("token")).putExtra("success", success));
        finish();
    }
}
