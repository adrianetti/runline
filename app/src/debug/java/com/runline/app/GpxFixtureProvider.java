package com.runline.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Synthetic fixtures only, in the instrumented process to avoid emulator provider-start deadlines. */
public class GpxFixtureProvider extends ContentProvider {
    public static final String GPX = "<gpx><trk><trkseg><trkpt lat='0' lon='0'/><trkpt lat='0.01' lon='0'/><trkpt lat='0' lon='0'/></trkseg></trk></gpx>";
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/gpx+xml"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        MatrixCursor cursor = new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME});
        cursor.addRow(new Object[] {uri.getLastPathSegment()});
        return cursor;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        String text = "invalid.gpx".equals(uri.getLastPathSegment()) ? "<not-gpx/>" : GPX;
        File file = new File(getContext().getCacheDir(), "fixture-" + uri.getLastPathSegment().replaceAll("[^a-zA-Z0-9.]", "_"));
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) { throw new FileNotFoundException("Fixture unavailable"); }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
