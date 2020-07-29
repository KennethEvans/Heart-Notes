package net.kenevans.heartnotes;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
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
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
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
    private static final String sdCardFileNameTemplate = "HeartNotes.%s.txt";

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

        // Open the database
        mDataDir = getDataDirectory();
        if (mDataDir == null) {
            return;
        }
        mDbAdapter = new HeartNotesDbAdapter(this, mDataDir);
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
        switch (id) {
            case R.id.refresh:
                refresh();
                return true;
            case R.id.newdata:
                createData();
                return true;
            case R.id.savetext:
                save();
                return true;
            case R.id.toend:
                mListViewToEnd = true;
                positionListView(mListViewToEnd);
                return true;
            case R.id.tostart:
                mListViewToEnd = false;
                positionListView(mListViewToEnd);
                return true;
            case R.id.filter:
                setFilter();
                return true;
            case R.id.sortOrder:
                setSortOrder();
                return true;
            case R.id.restore:
                checkRestore();
                return true;
            case R.id.setopenweatherkey:
                setOpenWeatherKey();
                return true;
            case R.id.setdatadirectory:
                setDataDirectory();
                return true;
            case R.id.help:
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
        Intent i = new Intent(this, DataEditActivity.class);
        i.putExtra(COL_ID, data.getId());
        i.putExtra(PREF_DATA_DIRECTORY, mDataDir.getPath());
        startActivityForResult(i, ACTIVITY_EDIT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        // refresh should not be necessary. onResume will be called next
        // and on Resume does not require refresh
        // refresh();
    }

    // @Override
    // public File getDatabasePath(String name) {
    // File file = null;
    // File sdCardRoot = Environment.getExternalStorageDirectory();
    // if (sdCardRoot.canWrite()) {
    // File dir = new File(sdCardRoot, SD_CARD_DB_DIRECTORY);
    // file = new File(dir, name);
    // }
    // return file;
    // }

    /**
     * Gets the current data directory
     *
     * @return The data directory.
     */
    public File getDataDirectory() {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String dataDirName = prefs.getString(PREF_DATA_DIRECTORY, null);
        File dataDir = null;
        if (dataDirName != null) {
            dataDir = new File(dataDirName);
        } else {
            File sdCardRoot = Environment.getExternalStorageDirectory();
            if (sdCardRoot != null) {
                dataDir = new File(sdCardRoot, SD_CARD_DB_DIRECTORY);
                // Change the stored value (even if it is null)
                SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE)
                        .edit();
                editor.putString(PREF_DATA_DIRECTORY, dataDir.getPath());
                editor.apply();
            }
        }
        if (dataDir == null) {
            Utils.errMsg(this, "Data directory is null");
        } else if (!dataDir.exists()) {
            Utils.errMsg(this, "Cannot find directory: " + dataDir);
            return null;
        }
        return dataDir;
    }

    /**
     * Sets the current data directory
     */
    private void setDataDirectory() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("Set Data Directory");
        alert.setMessage("Data Directory (Leave blank for default):");

        // Set an EditText view to get user input
        final EditText input = new EditText(this);
        // Set it with the current value
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String imageDirName = prefs.getString(PREF_DATA_DIRECTORY, null);
        if (imageDirName != null) {
            input.setText(imageDirName);
        }
        alert.setView(input);

        // DEBUG
        // File file = this.getExternalFilesDir(null);
        // Utils.infoMsg(this, "getExternalFilesDirectory:\n" + file.getPath());
        // Debug 4.4.2
        // File[] files = this.getExternalFilesDirs(null);
        // String info = "";
        // if (files == null) {
        // info += "getExternalFilesDirs returned null\n\n";
        // } else {
        // info += "Number of getExternalFilesDirs=" + files.length + "\n\n";
        // for (File file : files) {
        // info += file.getPath() + "\n";
        // }
        // }
        // info += "Current data directory:\n" + imageDirName + "\n";
        // String path1 = files[1].getPath();
        // if (imageDirName.equals(path1)) {
        // info += "Same";
        // } else {
        // info += "Different";
        // }
        // Utils.infoMsg(this, info);

        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String value = input.getText().toString();
                File dataDir = null;
                if (value.length() == 0) {
                    File sdCardRoot = Environment.getExternalStorageDirectory();
                    if (sdCardRoot != null) {
                        dataDir = new File(sdCardRoot, SD_CARD_DB_DIRECTORY);
                    }
                } else {
                    dataDir = new File(value);
                }
                if (dataDir == null) {
                    Utils.errMsg(HeartNotesActivity.this,
                            "Directory is null\n");
                    return;
                }
                mDataDir = dataDir;
                SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE)
                        .edit();
                editor.putString(PREF_DATA_DIRECTORY, mDataDir.getPath());
                editor.apply();
                if (!dataDir.exists()) {
                    Utils.errMsg(HeartNotesActivity.this,
                            "Directory does not exist:\n" + dataDir.getPath());
                    return;
                }
                if (mDbAdapter != null) {
                    mDbAdapter.close();
                }
                try {
                    mDbAdapter = new HeartNotesDbAdapter(
                            HeartNotesActivity.this, mDataDir);
                    mDbAdapter.open();
                } catch (Exception ex) {
                    Utils.excMsg(HeartNotesActivity.this,
                            "Error opening database at " + mDataDir, ex);
                }
                refresh();
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
        Intent i = new Intent(this, DataEditActivity.class);
        i.putExtra(PREF_DATA_DIRECTORY, mDataDir.getPath());
        // Use -1 for the COL_ID to indicate it is new
        i.putExtra(COL_ID, -1L);
        startActivityForResult(i, ACTIVITY_CREATE);
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
        BufferedWriter out = null;
        Cursor cursor = null;
        try {
            if (mDataDir == null) {
                Utils.errMsg(this, "Error saving to SD card");
                return;
            }
            String format = "yyyy-MM-dd-HHmmss";
            SimpleDateFormat df = new SimpleDateFormat(format, Locale.US);
            Date now = new Date();
            String fileName = String.format(sdCardFileNameTemplate,
                    df.format(now));
            File file = new File(mDataDir, fileName);
            FileWriter writer = new FileWriter(file);
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
                    comment = cursor.getString(indexComment);
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
            Utils.infoMsg(this, "Wrote " + file.getPath());
        } catch (Exception ex) {
            Utils.excMsg(this, "Error saving to SD card", ex);
        } finally {
            try {
                if (cursor != null) cursor.close();
            } catch (Exception ex) {
                // Do nothing
            }
            try {
                if (out != null) out.close();
            } catch (Exception ex) {
                // Do nothing
            }
        }
    }

    /**
     * Does the preliminary checking for restoring data, prompts if it is OK to
     * delete the current data, and call restoreData to actually do the delete
     * and restore.
     */
    private void checkRestore() {
        if (mDataDir == null) {
            Utils.errMsg(this, "Cannot find Heart Notes Data Directory");
            return;
        }

        // Find the .txt files in the data directory
        final File[] files = mDataDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                if (file.isDirectory()) {
                    return false;
                } else {
                    String[] extensions = {".txt"};
                    String path = file.getAbsolutePath().toLowerCase(Locale.US);
                    for (String extension : extensions) {
                        if (path.endsWith(extension)) {
                            return true;
                        }
                    }
                }
                return false;
            }
        });
        if (files == null || files.length == 0) {
            Utils.errMsg(this, "There are no .txt files in the data directory");
            return;
        }

        // Sort them by date with newest first
        Arrays.sort(files, new Comparator<File>() {
            public int compare(File f1, File f2) {
                return Long.valueOf(f2.lastModified()).compareTo(
                        f1.lastModified());
            }
        });

        // Prompt for the file to use
        final CharSequence[] items = new CharSequence[files.length];
        for (int i = 0; i < files.length; i++) {
            items[i] = files[i].getName();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getText(R.string.select_restore_file));
        builder.setSingleChoiceItems(items, 0,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, final int
                            item) {
                        dialog.dismiss();
                        if (item < 0 || item >= files.length) {
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
                                                restoreData(files[item]);
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
     */
    private void restoreData(File file) {
        BufferedReader in = null;
        int lineNum = 0;
        try {
            if (!file.exists()) {
                Utils.errMsg(this, "Cannot find:\n" + file.getPath());
                return;
            }

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
            in = new BufferedReader(new FileReader(file));
            while ((line = in.readLine()) != null) {
                lineNum++;
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
                comment = tokens[2];
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
                    "Restored " + lineNum + " lines from " + file.getPath());
        } catch (Exception ex) {
            Utils.excMsg(this, "Error restoring at line " + lineNum, ex);
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
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

}