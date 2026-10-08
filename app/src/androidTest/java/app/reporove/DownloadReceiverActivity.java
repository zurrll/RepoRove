package app.reporove;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Test-only foreign UID receiver. Uses platform Java, independent of the target app's runtime. */
public final class DownloadReceiverActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String action = getIntent().getStringExtra("replyAction");
        if (action == null) { finish(); return; }
        Intent result = new Intent(action).setPackage("app.reporove")
            .putExtra("mime", getIntent().getType())
            .putExtra("receiverUid", android.os.Process.myUid());
        try (InputStream input = getContentResolver().openInputStream(getIntent().getData());
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (output.size() + count > 32768) throw new IllegalArgumentException("Fixture too large");
                output.write(buffer, 0, count);
            }
            result.putExtra("content", new String(output.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception error) { result.putExtra("failure", error.getClass().getSimpleName()); }
        sendBroadcast(result);
        finish();
    }
}
