package net.kenevans.heartnotes;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Manages a database with entries for the number of Premature Ventricular
 * Contractions (PVCs) at a given time. The database implementation is similar
 * to the Notes example, but the database is on the SD card.
 */
public class HeartNotesActivity extends AppCompatActivity implements IConstants {
    /**
     * Template for the name of the file written to the SD card
     */
    private static final String saveFileTemplate = "HeartNotes.%s.txt";
    private static final String saveDatabaseTemplate = "HeartNotes.%s.db";

    private HeartNotesDbAdapter mDbAdapter;
    private CustomListAdapter mListAdapter;
    private ListView mListView;
    private String mSortOrder = SORT_DESCENDING;
    private boolean mListViewToEnd = false;

    /**
     * Array of hard-coded filters
     */
    protected Filter[] filters;
    /**
     * The current mFilter.
     */
    private int mFilter = 0;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Capture global exceptions
        Thread.setDefaultUncaughtExceptionHandler((paramThread,
                                                   paramThrowable) -> {
            Log.e(TAG, "Unexpected exception :", paramThrowable);
            // Any non-zero exit code
            System.exit(2);
        });

        setContentView(R.layout.list_view);
        mListView = findViewById(R.id.mainListView);
        mListView.setOnItemClickListener((parent, view, position, id) -> onListItemClick(position, id));


        // Create filters here so getText is available
        filters = new Filter[]{
                new Filter(getText(R.string.filter_none), null),
                new Filter(getText(R.string.filter_nonzero), COL_COUNT
                        + " <> 0"),
                new Filter(getText(R.string.filter_counttotal), COL_COUNT
                        + " = " + COL_TOTAL),};

        // Get the preferences here before refresh()
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        mFilter = prefs.getInt(PREF_FILTER, 0);
        if (mFilter < 0 || mFilter >= filters.length) {
            mFilter = 0;
        }
        mSortOrder = prefs.getString(PREF_SORT_ORDER, SORT_DESCENDING);

        mDbAdapter = new HeartNotesDbAdapter(this);
        mDbAdapter.open();

        refresh();

        // Position it to the end
        positionListView(mListViewToEnd);
    }

    @Override
    protected void onResume() {
        Log.d(TAG, this.getClass().getSimpleName() + ": onResume");
        super.onResume();
        refresh();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, this.getClass().getSimpleName() + ": onPause");
        super.onPause();
        if (mListAdapter != null) {
            mListAdapter.clear();
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, this.getClass().getSimpleName() + ": onDestroy");
        super.onDestroy();
        if (mDbAdapter != null) {
            mDbAdapter.close();
            mDbAdapter = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainmenu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.refresh) {
            refresh();
            return true;
        } else if (id == R.id.newdata) {
            createData();
            return true;
        } else if (id == R.id.savetext) {
            saveData();
            return true;
        } else if (id == R.id.savedb) {
            saveDatabase();
            return true;
        } else if (id == R.id.toend) {
            mListViewToEnd = true;
            positionListView(mListViewToEnd);
            return true;
        } else if (id == R.id.tostart) {
            mListViewToEnd = false;
            positionListView(mListViewToEnd);
            return true;
        } else if (id == R.id.filter) {
            setFilter();
            return true;
        } else if (id == R.id.sortOrder) {
            setSortOrder();
            return true;
        } else if (id == R.id.restore) {
            checkRestore();
            return true;
        } else if (id == R.id.replace_database) {
            checkReplaceDatabase();
            return true;
        } else if (id == R.id.set_openweather_key) {
            setOpenWeatherKey();
            return true;
        } else if (id == R.id.choose_data_directory) {
            chooseDataDirectory();
            return true;
        } else if (id == R.id.help) {
            showHelp();
            return true;
        }
        return false;
    }

    protected void onListItemClick(int position, long
            id) {
//        super.onListItemClick(lv, view, position, id);
        Log.d(TAG, this.getClass().getSimpleName() + ": onListItemClick: " +
                "position="
                + position + " id=" + id);
        final Data data = mListAdapter.getData(position);
        if (data == null) return;
        Log.d(TAG, "data: id=" + data.getId() + " " + data.getComment());
        Intent i = new Intent(this,
                net.kenevans.heartnotes.DataEditActivity.class);
        i.putExtra(COL_ID, data.getId());
        startActivityForResult(i, REQ_EDIT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == REQ_GET_TREE && resultCode == RESULT_OK) {
            Uri treeUri;
            // Get Uri from Storage Access Framework.
            treeUri = intent.getData();
            // Keep them from accumulating
            UriUtils.releaseAllPermissions(this);
            SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE)
                    .edit();
            if (treeUri != null) {
                editor.putString(PREF_TREE_URI, treeUri.toString());
            } else {
                editor.putString(PREF_TREE_URI, null);
            }
            editor.apply();

            // Persist access permissions.
            if (treeUri != null) {
                this.getContentResolver().takePersistableUriPermission(treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } else {
                Utils.errMsg(this, "Failed to get persistent access " +
                        "permissions");
            }
        }
    }

    /**
     * Sets the current data directory
     */
    private void chooseDataDirectory() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION &
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
//        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uriToLoad);
        startActivityForResult(intent, REQ_GET_TREE);
    }

    /**
     * Sets the OpenWeather key.
     */
    private void setOpenWeatherKey() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("Set OpenWeather Key");

        // Set an EditText view to get user input
        final EditText input = new EditText(this);
        // Set it with the current value
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String keyName = prefs.getString(PREF_OPENWEATHER_KEY, null);
        if (keyName != null) {
            input.setText(keyName);
        }
        alert.setView(input);
        alert.setPositiveButton("Ok", (dialog, whichButton) -> {
            String value = input.getText().toString();
            SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE)
                    .edit();
            editor.putString(PREF_OPENWEATHER_KEY, value);
            editor.apply();
        });

        alert.setNegativeButton("Cancel",
                (dialog, whichButton) -> {
                    // Do nothing
                });

        alert.show();
    }

    private void createData() {
        Intent i = new Intent(this,
                net.kenevans.heartnotes.DataEditActivity.class);
        // Use -1 for the COL_ID to indicate it is new
        i.putExtra(COL_ID, -1L);
        startActivityForResult(i, REQ_CREATE);
    }

    /**
     * Show the help.
     */
    private void showHelp() {
        try {
            // Start theInfoActivity
            Intent intent = new Intent();
            intent.setClass(this, InfoActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra(INFO_URL, "file:///android_asset/heartnotes.html");
            startActivity(intent);
        } catch (Exception ex) {
            Utils.excMsg(this, getString(R.string.help_show_error), ex);
        }
    }

    /**
     * Format the date using the static format.
     *
     * @param dateNum The date number.
     * @return The formatted date.
     */
    public static String formatDate(Long dateNum) {
        return formatDate(HeartNotesActivity.longFormatter, dateNum);
    }

    /**
     * Format the date using the given format.
     *
     * @param formatter The formatter.
     * @param dateNum   The date.
     * @return The formatted date.
     */
    public static String formatDate(SimpleDateFormat formatter, Long dateNum) {
        // Consider using Date.toString() as it might be more locale
        // independent.
        if (dateNum == null) {
            return "<Unknown>";
        }
        if (dateNum == -1) {
            // Means the column was not found in the database
            return "<Date NA>";
        }
        // Consider using Date.toString()
        // It might be more locale independent.
        // return new Date(dateNum).toString();

        // Include the dateNum
        // return dateNum + " " + formatter.format(dateNum);

        return formatter.format(dateNum);
    }

    /**
     * Positions the list view.
     *
     * @param toEnd True to go to the end, false to go to the beginning.
     */
    private void positionListView(final boolean toEnd) {
        mListView.post(() -> {
            int pos = toEnd ? mListView.getCount() - 1 : 0;
            mListView.setSelection(pos);
        });
    }

    /**
     * Saves the info to the SD card.
     */
    private void saveData() {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String treeUriStr = prefs.getString(PREF_TREE_URI, null);
        if (treeUriStr == null) {
            Utils.errMsg(this, "There is no data directory set");
            return;
        }
        String format = "yyyy-MM-dd-HHmmss";
        SimpleDateFormat df = new SimpleDateFormat(format, Locale.US);
        Date now = new Date();
        String fileName = String.format(saveFileTemplate,
                df.format(now));
        try {
            Uri treeUri = Uri.parse(treeUriStr);
            String treeDocumentId =
                    DocumentsContract.getTreeDocumentId(treeUri);
            Uri docTreeUri =
                    DocumentsContract.buildDocumentUriUsingTree(treeUri,
                            treeDocumentId);
            ContentResolver resolver = this.getContentResolver();
            ParcelFileDescriptor pfd;
            Uri docUri = DocumentsContract.createDocument(resolver, docTreeUri,
                    "text/plain", fileName);
            pfd = getContentResolver().
                    openFileDescriptor(docUri, "w");
            try (FileWriter writer = new FileWriter(pfd.getFileDescriptor());
                 BufferedWriter out = new BufferedWriter(writer);
                 Cursor cursor =
                         mDbAdapter.fetchAllData(filters[mFilter].selection,
                                 mSortOrder)) {
                // int indexId = cursor.getColumnIndex(COL_ID);
                int indexDate = cursor.getColumnIndex(COL_DATE);
                // int indexDateMod = cursor.getColumnIndex(COL_DATEMOD);
                int indexCount = cursor.getColumnIndex(COL_COUNT);
                int indexTotal = cursor.getColumnIndex(COL_TOTAL);
                // indexEdited = cursor.getColumnIndex(COL_EDITED);
                int indexComment = cursor.getColumnIndex(COL_COMMENT);
                // Loop over items
                cursor.moveToFirst();
                String comment, info, date;
                long count, total, dateNum;
                while (!cursor.isAfterLast()) {
                    comment = "<None>";
                    if (indexComment > -1) {
                        // Convert tabs and newlines to text for restore
                        comment = cursor.getString(indexComment)
                                .replaceAll("\\n", "<br>")
                                .replaceAll("\\t", "<tab>");
                    }
                    date = "<Unknown>";
                    if (indexDate > -1) {
                        dateNum = cursor.getLong(indexDate);
                        date = formatDate(dateNum);
                    }
                    count = -1;
                    if (indexCount > -1) {
                        count = cursor.getInt(indexCount);
                    }
                    total = -1;
                    if (indexTotal > -1) {
                        total = cursor.getInt(indexTotal);
                    }
                    info = String.format(Locale.US, "%2d/%d \t%s \t%s\n", count,
                            total, date, comment);
                    out.write(info);
                    cursor.moveToNext();
                }
                Utils.infoMsg(this, "Wrote " + docUri.getLastPathSegment());
            }
        } catch (Exception ex) {
            String msg = "Error saving to SD card";
            Utils.excMsg(this, msg, ex);
            Log.e(TAG, msg, ex);
        }
    }

    private void saveDatabase() {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String treeUriStr = prefs.getString(PREF_TREE_URI, null);
        if (treeUriStr == null) {
            Utils.errMsg(this, "There is no data directory set");
            return;
        }
        try {
            String format = "yyyy-MM-dd-HHmmss";
            SimpleDateFormat df = new SimpleDateFormat(format, Locale.US);
            Date now = new Date();
            String fileName = String.format(saveDatabaseTemplate,
                    df.format(now));
            Uri treeUri = Uri.parse(treeUriStr);
            String treeDocumentId =
                    DocumentsContract.getTreeDocumentId(treeUri);
            Uri docTreeUri =
                    DocumentsContract.buildDocumentUriUsingTree(treeUri,
                            treeDocumentId);
            ContentResolver resolver = this.getContentResolver();
            Uri docUri = DocumentsContract.createDocument(resolver, docTreeUri,
                    "application/vnd.sqlite3", fileName);
            if (docUri == null) {
                Utils.errMsg(this, "Could not create document Uri");
                return;
            }
            ParcelFileDescriptor pfd = getContentResolver().
                    openFileDescriptor(docUri, "rw");
            File src = new File(getExternalFilesDir(null), DB_NAME);
            Log.d(TAG, "saveDatabase: docUri=" + docUri);
            try (FileChannel in =
                         new FileInputStream(src).getChannel();
                 FileChannel out =
                         new FileOutputStream(pfd.getFileDescriptor()).getChannel()) {
                out.transferFrom(in, 0, in.size());
            } catch (Exception ex) {
                String msg = "Error copying source database from "
                        + docUri.getLastPathSegment() + " to "
                        + src.getPath();
                Log.e(TAG, msg, ex);
                Utils.excMsg(this, msg, ex);
            }
            Utils.infoMsg(this, "Wrote " + docUri.getLastPathSegment());
        } catch (Exception ex) {
            String msg = "Error saving to SD card";
            Utils.excMsg(this, msg, ex);
            Log.e(TAG, msg, ex);
        }
    }

    /**
     * Does the preliminary checking for restoring data, prompts if it is OK to
     * delete the current data, and call restoreData to actually do the delete
     * and restore.
     */
    private void checkRestore() {
        // Find the .txt files in the data directory
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String treeUriStr = prefs.getString(PREF_TREE_URI, null);
        if (treeUriStr == null) {
            Utils.errMsg(this, "There is no tree Uri set");
            return;
        }
        Uri treeUri = Uri.parse(treeUriStr);
        final List<UriUtils.UriData> children =
                UriUtils.getChildren(this, treeUri, ".txt");
        final int len = children.size();
        if (len == 0) {
            Utils.errMsg(this, "There are no .txt files in the data directory");
            return;
        }
        // Sort them by date with newest first
        Collections.sort(children,
                (data1, data2) -> Long.compare(data2.modifiedTime,
                        data1.modifiedTime));

        // Prompt for the file to use
        final CharSequence[] items = new CharSequence[children.size()];
        String displayName;
        UriUtils.UriData uriData;
        for (int i = 0; i < len; i++) {
            uriData = children.get(i);
            displayName = uriData.displayName;
            if (displayName == null) {
                displayName = uriData.uri.getLastPathSegment();
            }
            items[i] = displayName;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getText(R.string.select_restore_file));
        builder.setSingleChoiceItems(items, 0,
                (dialog, item) -> {
                    dialog.dismiss();
                    if (item < 0 || item >= len) {
                        Utils.errMsg(HeartNotesActivity.this,
                                "Invalid item");
                        return;
                    }
                    // Confirm the user wants to delete all the current data
                    new AlertDialog.Builder(HeartNotesActivity.this)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setTitle(R.string.confirm)
                            .setMessage(R.string.delete_prompt)
                            .setPositiveButton(R.string.ok,
                                    (dialog1, which) -> {
                                        dialog1.dismiss();
                                        restoreData(children.get(item).uri);
                                    })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * Does the preliminary checking for restoring the database, prompts if
     * it is OK to delete the current one, and call restoreDatabase to actually
     * do the replace.
     */
    private void checkReplaceDatabase() {
        Log.d(TAG, "checkReplaceDatabase");
        // Find the .db files in the data directory
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String treeUriStr = prefs.getString(PREF_TREE_URI, null);
        if (treeUriStr == null) {
            Utils.errMsg(this, "There is no tree Uri set");
            return;
        }
        Uri treeUri = Uri.parse(treeUriStr);
        final List<UriUtils.UriData> children =
                UriUtils.getChildren(this, treeUri, ".db");
        final int len = children.size();
        if (len == 0) {
            Utils.errMsg(this, "There are no .db files in the data directory");
            return;
        }
        // Sort them by date with newest first
        Collections.sort(children,
                (data1, data2) -> Long.compare(data2.modifiedTime,
                        data1.modifiedTime));

        // Prompt for the file to use
        final CharSequence[] items = new CharSequence[children.size()];
        String displayName;
        UriUtils.UriData uriData;
        for (int i = 0; i < len; i++) {
            uriData = children.get(i);
            displayName = uriData.displayName;
            if (displayName == null) {
                displayName = uriData.uri.getLastPathSegment();
            }
            items[i] = displayName;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getText(R.string.select_replace_database));
        builder.setSingleChoiceItems(items, 0,
                (dialog, item) -> {
                    dialog.dismiss();
                    if (item < 0 || item >= len) {
                        Utils.errMsg(HeartNotesActivity.this,
                                "Invalid item");
                        return;
                    }
                    // Confirm the user wants to delete all the current data
                    new AlertDialog.Builder(HeartNotesActivity.this)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setTitle(R.string.confirm)
                            .setMessage(R.string.delete_prompt)
                            .setPositiveButton(R.string.ok,
                                    (dialog1, which) -> {
                                        dialog1.dismiss();
                                        Log.d(TAG, "Calling replaceDatabase: " +
                                                "uri="
                                                + children.get(item).uri);
                                        replaceDatabase(children.get(item).uri);
                                    })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * Deletes the existing data without prompting and restores the new data.
     *
     * @param uri The Uri.
     */
    private void restoreData(Uri uri) {
        Log.d(TAG, "restoreData: uri=" + uri);
        int lineNum = 0;
        try (InputStreamReader inputStreamReader = new InputStreamReader(
                getContentResolver().openInputStream(uri));
             BufferedReader in = new BufferedReader(inputStreamReader)) {

//            if (!uri.exists()) {
//                Utils.errMsg(this, "Cannot find:\n" + uri.getPath());
//                return;
//            }

            // Delete all the data and recreate the table
            mDbAdapter.recreateDataTable();

            // Read the file and get the data to restore
            long dateMod = new Date().getTime();
            String[] tokens;
            String line;
            int count, total;
            String comment;
            Date date;
            int slash;
            while ((line = in.readLine()) != null) {
                lineNum++;
                Log.d(TAG, lineNum + " " + line);
                tokens = line.trim().split("\t");
                // Skip blank lines
                if (tokens.length == 0) {
                    continue;
                }
                // Skip lines starting with #
                if (tokens[0].trim().startsWith("#")) {
                    continue;
                }
                if (tokens.length != 3) {
                    Utils.errMsg(this, "Found " + tokens.length
                            + " tokens for line " + lineNum
                            + "\nShould be 3 tokens");
                    in.close();
                    return;
                }
                slash = tokens[0].indexOf("/");
                if (slash < 0 || slash == tokens[0].length() - 1) {
                    Utils.errMsg(this, "count/total field is invalid |"
                            + tokens[0] + "|");
                    in.close();
                    return;
                }
                count = Integer.parseInt(tokens[0].substring(0, slash));
                total = Integer.parseInt(tokens[0].substring(slash + 1).trim());
                date = HeartNotesActivity.longFormatter.parse(tokens[1]
                        .trim());
                // Convert newline and tabs back
                comment = tokens[2].replaceAll("<br>", "\n").replaceAll("<tab" +
                        ">", "\t");
                long id = -1;
                if (date != null) {
                    id = mDbAdapter.createData(date.getTime(), dateMod, count,
                            total, true, comment);
                }
                if (id < 0) {
                    Utils.errMsg(this, "Failed to create the entry for line "
                            + lineNum);
                    in.close();
                    return;
                }
            }
            refresh();
            Utils.infoMsg(this,
                    "Restored " + lineNum + " lines from " + uri.getPath());
        } catch (Exception ex) {
            String msg = "Error restoring at line " + lineNum;
            Utils.excMsg(this, msg, ex);
            Log.e(TAG, msg, ex);
        }
    }

    /**
     * Replaces the database without prompting.
     *
     * @param uri The Uri.
     */
    private void replaceDatabase(Uri uri) {
        Log.d(TAG, "replaceDatabase: uri=" + uri.getLastPathSegment());
        if (uri == null) {
            Log.d(TAG, this.getClass().getSimpleName()
                    + "replaceDatabase: Source database is null");
            Utils.errMsg(this, "Source database is null");
            return;
        }
        String lastSeg = uri.getLastPathSegment();
        if (!UriUtils.exists(this, uri)) {
            String msg = "Source database does not exist " + lastSeg;
            Log.d(TAG, this.getClass().getSimpleName()
                    + "replaceDatabase: " + msg);
            Utils.errMsg(this, msg);
            return;
        }
        // Copy the data base to app storage
        File dest = null;
        try {
            String destFileName = UriUtils.getFileNameFromUri(uri);
            dest = new File(getExternalFilesDir(null), destFileName);
            dest.createNewFile();
            ParcelFileDescriptor pfd = getContentResolver().
                    openFileDescriptor(uri, "rw");
            try (FileChannel in =
                         new FileInputStream(pfd.getFileDescriptor()).getChannel();
                 FileChannel out =
                         new FileOutputStream(dest).getChannel()) {
                out.transferFrom(in, 0, in.size());
            } catch (Exception ex) {
                String msg = "Error copying source database from "
                        + uri.getLastPathSegment() + " to "
                        + dest.getPath();
                Log.e(TAG, msg, ex);
                Utils.excMsg(this, msg, ex);
            }
        } catch (Exception ex) {
            String msg = "Error getting source database" + uri;
            Log.e(TAG, msg, ex);
            Utils.excMsg(this, msg, ex);
        }
        try {
            // Replace (Use null for default alias)
            mDbAdapter.replaceDatabase(dest.getPath(), null);
            refresh();
            Utils.infoMsg(this,
                    "Restored database from " + uri.getLastPathSegment());
        } catch (Exception ex) {
            String msg = "Error replacing data from " + dest.getPath();
            Log.e(TAG, msg, ex);
            Utils.excMsg(this, msg, ex);
        }
    }

    /**
     * Bring up a dialog to change the mFilter order.
     */
    private void setFilter() {
        final CharSequence[] items = new CharSequence[filters.length];
        for (int i = 0; i < filters.length; i++) {
            items[i] = filters[i].name;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getText(R.string.filter_title));
        builder.setSingleChoiceItems(items, mFilter,
                (dialog, item) -> {
                    dialog.dismiss();
                    if (item < 0 || item >= filters.length) {
                        Utils.errMsg(HeartNotesActivity.this,
                                "Invalid mFilter");
                        mFilter = 0;
                    } else {
                        mFilter = item;
                    }
                    SharedPreferences.Editor editor =
                            getPreferences(MODE_PRIVATE).edit();
                    editor.putInt(PREF_FILTER, mFilter);
                    editor.apply();
                    refresh();
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * Bring up a dialog to change the sort order.
     */
    private void setSortOrder() {
        final CharSequence[] items = new CharSequence[2];
        items[0] = "Ascending";
        items[1] = "Descending";
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getText(R.string.sort_order_item));
        builder.setSingleChoiceItems(items,
                mSortOrder.equals(SORT_ASCENDING) ? 0 : 1,
                (dialog, item) -> {
                    dialog.dismiss();
                    if (item == 0) {
                        mSortOrder = SORT_ASCENDING;
                    } else {
                        mSortOrder = SORT_DESCENDING;
                    }
                    SharedPreferences.Editor editor =
                            getPreferences(MODE_PRIVATE).edit();
                    editor.putString(PREF_SORT_ORDER, mSortOrder);
                    editor.apply();
                    refresh();
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * Gets a new cursor and starts managing it.
     */
    private void refresh() {
        // Initialize the mListAdapter
        mListAdapter = new CustomListAdapter();
        mListView.setAdapter(mListAdapter);
        positionListView(mListViewToEnd);
    }

    /**
     * Class to manage a Filter.
     */
    private static class Filter {
        private final CharSequence name;
        private final String selection;

        private Filter(CharSequence menuName, String selection) {
            this.name = menuName;
            this.selection = selection;
        }
    }

    /**
     * Class to manage the data needed for an item in the ListView.
     */
    private static class Data {
        private final long id;
        private final String comment;
        private final long dateNum;
        private final int count;
        private final int total;

        public Data(long id, String comment, long dateNum, int count, int
                total) {
            this.id = id;
            this.comment = comment;
            this.dateNum = dateNum;
            this.count = count;
            this.total = total;
        }

        private long getId() {
            return id;
        }

        private String getComment() {
            return comment;
        }

        private long getDateNum() {
            return dateNum;
        }

        private int getCount() {
            return count;
        }

        private int getTotal() {
            return total;
        }
    }

    /**
     * ListView adapter class for this activity.
     */
    private class CustomListAdapter extends BaseAdapter {
        private final ArrayList<Data> mData;
        private final LayoutInflater mInflator;
        private int indexDate;
        private int indexCount;
        private int indexTotal;
        private int indexComment;

        private CustomListAdapter() {
            super();
            mData = new ArrayList<>();
            mInflator = HeartNotesActivity.this.getLayoutInflater();
            Cursor cursor = null;
            int nItems = 0;
            try {
                if (mDbAdapter != null) {
                    cursor = mDbAdapter.fetchAllData(filters[mFilter]
                            .selection, mSortOrder);
                    int indexId = cursor.getColumnIndex(COL_ID);
                    indexDate = cursor.getColumnIndex(COL_DATE);
                    // indexDateMod = cursor.getColumnIndex(COL_DATEMOD);
                    indexCount = cursor.getColumnIndex(COL_COUNT);
                    indexTotal = cursor.getColumnIndex(COL_TOTAL);
                    // indexEdited = cursor.getColumnIndex(COL_EDITED);
                    indexComment = cursor.getColumnIndex(COL_COMMENT);

                    // Loop over items
                    cursor.moveToFirst();
                    while (!cursor.isAfterLast()) {
                        nItems++;
                        long id = cursor.getLong(indexId);
                        String comment = "<Comment NA>";
                        if (indexComment > -1) {
                            comment = cursor.getString(indexComment);
                        }
                        long dateNum = -1L;
                        if (indexDate > -1) {
                            dateNum = cursor.getLong(indexDate);
                        }
                        int count = -1;
                        if (indexCount > -1) {
                            count = cursor.getInt(indexCount);
                        }
                        int total = -1;
                        if (indexTotal > -1) {
                            total = cursor.getInt(indexTotal);
                        }
                        addData(new Data(id, comment, dateNum, count, total));
                        cursor.moveToNext();
                    }
                }
                if (cursor != null) cursor.close();
            } catch (Exception ex) {
                Utils.excMsg(HeartNotesActivity.this,
                        "Error getting data", ex);
            } finally {
                try {
                    if (cursor != null) cursor.close();
                } catch (Exception ex) {
                    // Do nothing
                }
            }
            Log.d(TAG, "Data list created with " + nItems + " items");
        }

        private void addData(Data data) {
            if (!mData.contains(data)) {
                mData.add(data);
            }
        }

        private Data getData(int position) {
            return mData.get(position);
        }

        private void clear() {
            mData.clear();
        }

        @Override
        public int getCount() {
            return mData.size();
        }

        @Override
        public Object getItem(int i) {
            return mData.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.list_row, viewGroup,
                        false);
                viewHolder = new ViewHolder();
                viewHolder.title = view.findViewById(R.id.title);
                viewHolder.subTitle = view.findViewById(R.id
                        .subtitle);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            Data data = mData.get(i);
            viewHolder.title.setText(String.format(Locale.US, "%d", data
                    .getId()) + ": " + data.getCount() +
                    "/" + data.getTotal() + " at "
                    + formatDate(data.getDateNum()));
            viewHolder.subTitle.setText(data.getComment());
            return view;
        }
    }

    /**
     * Convenience class for managing views for a ListView row.
     */
    private static class ViewHolder {
        TextView title;
        TextView subTitle;
    }
}