package net.kenevans.heartnotes;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
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
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
    private File mDataDir;
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
        setContentView(R.layout.list_view);
        mListView = findViewById(R.id.mainListView);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                onListItemClick(mListView, view, position, id);
            }
        });


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
            save();
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
        } else if (id == R.id.setopenweatherkey) {
            setOpenWeatherKey();
            return true;
        } else if (id == R.id.setdatadirectory) {
            setDataDirectory();
            return true;
        } else if (id == R.id.help) {
            showHelp();
            return true;
        }
        return false;
    }

    protected void onListItemClick(ListView lv, View view, int position, long
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
                                    Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == REQ_GET_TREE) {
            Uri treeUri;
            if (resultCode == Activity.RESULT_OK) {
                // Get Uri from Storage Access Framework.
                treeUri = resultData.getData();
                // Keep them from accumulating
                releaseAllPermissions();
                SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE)
                        .edit();
                editor.putString(PREF_TREE_URI, treeUri.toString());
                editor.apply();

                // Persist access permissions.
                final int takeFlags = resultData.getFlags()
                        & (Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                this.getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
            }
        }
    }

    /**
     * Sets the current data directory
     */
    private void setDataDirectory() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION & Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
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
        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String value = input.getText().toString();
                SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE)
                        .edit();
                editor.putString(PREF_OPENWEATHER_KEY, value);
                editor.apply();
            }
        });

        alert.setNegativeButton("Cancel",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int
                            whichButton) {
                        // Do nothing
                    }
                });

        alert.show();
    }

    private void createData() {
        Intent i = new Intent(this,
                net.kenevans.heartnotes.DataEditActivity.class);
        // Use -1 for the COL_ID to indicate it is new
        i.putExtra(COL_ID, -1L);
        startActivityForResult(i, REQ_CREATE);
        // Date date = new Date();
        // Date dateMod = date;
        // int count = (int) Math.round(Math.random() * 60);
        // int total = 60;
        // String comment = "This is a test";
        // mDbAdapter.createData(date.getTime(), dateMod.getTime(), count,
        // total,
        // false, comment);
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
        mListView.post(new Runnable() {
            public void run() {
                int pos = toEnd ? mListView.getCount() - 1 : 0;
                mListView.setSelection(pos);
            }
        });
    }

    /**
     * Saves the info to the SD card.
     */
    private void save() {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String treeUriStr = prefs.getString(PREF_TREE_URI, null);
        if (treeUriStr == null) {
            Utils.errMsg(this, "There is no data directory set");
            return;
        }
        FileWriter writer = null;
        BufferedWriter out = null;
        Cursor cursor = null;
        try {
            String format = "yyyy-MM-dd-HHmmss";
            SimpleDateFormat df = new SimpleDateFormat(format, Locale.US);
            Date now = new Date();
            String fileName = String.format(saveFileTemplate,
                    df.format(now));
            Uri treeUri = Uri.parse(treeUriStr);
            String treeDocumentId =
                    DocumentsContract.getTreeDocumentId(treeUri);
            Uri docTreeUri =
                    DocumentsContract.buildDocumentUriUsingTree(treeUri,
                            treeDocumentId);
            ContentResolver resolver = this.getContentResolver();
            Uri docUri = DocumentsContract.createDocument(resolver, docTreeUri,
                    "text/plain", fileName);
            Log.d(TAG, "save: docUri=" + docUri);

            ParcelFileDescriptor pfd = getContentResolver().
                    openFileDescriptor(docUri, "w");
            writer = new FileWriter(pfd.getFileDescriptor());
            out = new BufferedWriter(writer);
            cursor = mDbAdapter.fetchAllData(filters[mFilter].selection,
                    mSortOrder);
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
        } catch (Exception ex) {
            String msg = "Error saving to SD card";
            Utils.excMsg(this, msg, ex);
            Log.e(TAG, msg, ex);
        } finally {
            try {
                if (cursor != null) cursor.close();
                if (out != null) out.close();
                if (writer != null) writer.close();
            } catch (Exception ex) {
                // Do nothing
            }
        }
    }

    private void saveDatabase() {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String treeUriStr = prefs.getString(PREF_TREE_URI, null);
        if (treeUriStr == null) {
            Utils.errMsg(this, "There is no data directory set");
            return;
        }
        FileInputStream inputStream = null;
        OutputStream outputStream = null;
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
            Log.d(TAG, "saveDatabase: docUri=" + docUri);
            try {
                // Close the database
                if (mDbAdapter != null) {
                    mDbAdapter.close();
                }
                File file = new File(getExternalFilesDir(null), DB_NAME);
                inputStream = new FileInputStream(file);
                ParcelFileDescriptor pfd = getContentResolver().
                        openFileDescriptor(docUri, "w");
                outputStream =
                        new FileOutputStream(pfd.getFileDescriptor());
                byte[] buff = new byte[1024];
                int read;
                while ((read = inputStream.read(buff, 0, buff.length)) > 0)
                    outputStream.write(buff, 0, read);
            } catch (Exception ex) {
                String msg =
                        "Failed to save database from " + docUri.getLastPathSegment();
                Utils.excMsg(this, msg, ex);
                Log.e(TAG, msg, ex);
            } finally {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
                if (mDbAdapter != null) {
                    mDbAdapter.open();
                }
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
        String treeDocumentId =
                DocumentsContract.getTreeDocumentId(treeUri);
        Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri,
                treeDocumentId);
        final List<UriData> children = getChildren(treeUri, ".txt");
        final int len = children.size();
        if (len == 0) {
            Utils.errMsg(this, "There are no .txt files in the data directory");
            return;
        }
        // Sort them by date with newest first
        Collections.sort(children, new Comparator<UriData>() {
            public int compare(UriData data1, UriData data2) {
                return Long.compare(data2.modifiedTime, data1.modifiedTime);
            }
        });

        // Prompt for the file to use
        final CharSequence[] items = new CharSequence[children.size()];
        String displayName;
        UriData uriData;
        Uri child;
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
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, final int
                            item) {
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
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(
                                                    DialogInterface dialog,
                                                    int which) {
                                                dialog.dismiss();
                                                restoreData(children.get(item).uri);
                                            }

                                        })
                                .setNegativeButton(R.string.cancel, null)
                                .show();
                    }
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
        BufferedReader in = null;
        InputStreamReader inputStreamReader = null;
        int lineNum = 0;
        try {
//            if (!uri.exists()) {
//                Utils.errMsg(this, "Cannot find:\n" + uri.getPath());
//                return;
//            }

            // Delete all the data and recreate the table
            mDbAdapter.recreateTable();

            // Read the file and get the data to restore
            long dateMod = new Date().getTime();
            String[] tokens;
            String line;
            int count, total;
            String comment;
            Date date;
            int slash;
            inputStreamReader = new InputStreamReader(
                    getContentResolver().openInputStream(uri));
            in = new BufferedReader(inputStreamReader);
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
        } finally {
            try {
                if (in != null) in.close();
                if (inputStreamReader != null) inputStreamReader.close();
            } catch (Exception ex) {
                // Do nothing
            }
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
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
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
                    }
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
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
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
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    private List<UriData> getChildren(Uri uri, String ext) {
        ContentResolver contentResolver = this.getContentResolver();
        Uri childrenUri =
                DocumentsContract.buildChildDocumentsUriUsingTree(uri,
                        DocumentsContract.getTreeDocumentId(uri));
        List<UriData> children = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = contentResolver.query(childrenUri,
                    new String[]{
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    },
                    null,
                    null,
                    null);
            String documentId;
            Uri documentUri;
            long modifiedTime;
            String displayName;
            while (cursor.moveToNext()) {
                documentId = cursor.getString(0);
                documentUri = DocumentsContract.buildDocumentUriUsingTree(uri,
                        documentId);
                modifiedTime = cursor.getLong(1);
                displayName = cursor.getString(2);
                if (documentUri.getLastPathSegment().toLowerCase().endsWith(ext)) {
                    children.add(new UriData(documentUri, modifiedTime,
                            displayName));
                }
            }
        } finally {
            try {
                if (cursor != null) cursor.close();
            } catch (Exception ex) {
                // Do nothing
            }
        }
        return children;
    }

    public String getNameDisplayName(Uri uri) {
        Cursor cursor = null;
        String displayName = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            cursor.moveToFirst();
            displayName =
                    cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
        } finally {
            try {
                if (cursor != null) cursor.close();
            } catch (Exception ex) {
                // Do nothing
            }
        }
        return displayName;
    }

    /**
     * Releases all permissions.
     */
    private void releaseAllPermissions() {
        ContentResolver resolver = this.getContentResolver();
        final List<UriPermission> permissionList =
                resolver.getPersistedUriPermissions();
        int nPermissions = permissionList.size();
        if (nPermissions == 0) {
//            Utils.warnMsg(this, "There are no persisted permissions");
            return;
        }
        Uri uri;
        for (UriPermission permission : permissionList) {
            uri = permission.getUri();
            resolver.releasePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION |
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }
//        // Set the preference to null
//        SharedPreferences.Editor editor =
//                getPreferences(MODE_PRIVATE).edit();
//        editor.putString(PREF_TREE_URI, null);
//        editor.apply();
    }

    /**
     * Gets a new cursor and starts managing it.
     */
    private void refresh() {
        // Initialize the list view mAapter
        mListAdapter = new CustomListAdapter();
        mListView.setAdapter(mListAdapter);
        positionListView(mListViewToEnd);
    }

    /**
     * Class to manage a Filter.
     */
    private static class Filter {
        private CharSequence name;
        private String selection;

        private Filter(CharSequence menuName, String selection) {
            this.name = menuName;
            this.selection = selection;
        }
    }

    /**
     * Class to manage the data needed for an item in the ListView.
     */
    private static class Data {
        private long id;
        private String comment;
        private long dateNum;
        private int count;
        private int total;

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
        private ArrayList<Data> mData;
        private LayoutInflater mInflator;
        private int indexId;
        private int indexDate;
        private int indexCount;
        private int indexTotal;
        // private int indexDateMod;
        // private int indexEdited;
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
                    indexId = cursor.getColumnIndex(COL_ID);
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
            // // DEBUG
            // Log.d(TAG, "getView: " + i);
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
     * Convience class for managing views for a ListView row.
     */
    private static class ViewHolder {
        TextView title;
        TextView subTitle;
    }

    /**
     * Convience class for managing views for a ListView row.
     */
    private static class UriData {
        final public Uri uri;
        final public long modifiedTime;
        final public String displayName;

        UriData(Uri uri, long modifiedTime, String displayName) {
            this.uri = uri;
            this.modifiedTime = modifiedTime;
            this.displayName = displayName;
        }
    }
}