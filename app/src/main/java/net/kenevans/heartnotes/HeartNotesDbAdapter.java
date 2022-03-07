package net.kenevans.heartnotes;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.File;

/**
 * Simple database access helper class. Defines the basic CRUD operations for
 * Notes, and gives the ability to list all notes as well as retrieve or modify
 * a specific entry.
 * <p/>
 * This has been improved from the first version of this tutorial through the
 * addition of better error handling and also using returning a Cursor instead
 * of using a collection of inner classes (which is less scalable and not
 * recommended).
 */
public class HeartNotesDbAdapter implements IConstants {
    private DatabaseHelper mDbHelper;
    private SQLiteDatabase mDb;
    private final Context mCtx;

    /**
     * Database creation SQL statement
     */
    private static final String DB_CREATE = "create table " + DB_DATA_TABLE
            + " (_id integer primary key autoincrement, " + COL_DATE
            + " integer not null, " + COL_DATEMOD + " integer not null, "
            + COL_COUNT + " integer not null, " + COL_TOTAL
            + " integer not null, " + COL_EDITED + " integer not null,"
            + COL_COMMENT + " text not null);";

    /**
     * Constructor - takes the context to allow the database to be
     * opened/created
     *
     * @param ctx The context.
     */
    public HeartNotesDbAdapter(Context ctx) {
        mCtx = ctx;
    }

    /**
     * Open the database. If it cannot be opened, try to create a new instance
     * of the database. If it cannot be created, throw an exception to signal
     * the failure
     *
     * @return this (self reference, allowing this to be chained in an
     * initialization call)
     * @throws SQLException if the database could be neither opened or created
     */
    public HeartNotesDbAdapter open() throws SQLException {
        // Make sure the directory exists and is available
        File dataDir = mCtx.getExternalFilesDir(null);
        try {
            if (!dataDir.exists()) {
                boolean res = dataDir.mkdirs();
                if (!res) {
                    Utils.errMsg(mCtx,
                            "Creating directory failed\n" + dataDir);
                    return null;
                }
                // Try again
                if (!dataDir.exists()) {
                    Utils.errMsg(mCtx,
                            "Unable to create database directory at "
                                    + dataDir);
                    return null;
                }
            }
            mDbHelper = new DatabaseHelper(mCtx, dataDir.getPath()
                    + File.separator + DB_NAME);
            mDb = mDbHelper.getWritableDatabase();
        } catch (Exception ex) {
            Utils.excMsg(mCtx, "Error opening database at " + dataDir, ex);
        }
        return this;
    }

    public void close() {
        mDbHelper.close();
    }

    /**
     * Create new data using the parameters provided. If the data is
     * successfully created return the new rowId for that entry, otherwise
     * return a -1 to indicate failure.
     *
     * @param date    The date.
     * @param dateMod The modification date.
     * @param count   The count.
     * @param total   The total tries.
     * @param edited  If edited.
     * @param comment The comment.
     * @return rowId or -1 on failure.
     */
    public long createData(long date, long dateMod, long count, long total,
                           boolean edited, String comment) {
        if (mDb == null) {
            Utils.errMsg(mCtx, "Failed to create data. Database is null.");
            return -1;
        }
        ContentValues values = new ContentValues();
        values.put(COL_DATE, date);
        values.put(COL_DATEMOD, dateMod);
        values.put(COL_COUNT, count);
        values.put(COL_TOTAL, total);
        values.put(COL_EDITED, edited);
        values.put(COL_COMMENT, comment);
        values.put(COL_DATE, date);

        return mDb.insert(DB_DATA_TABLE, null, values);
    }

    /**
     * Delete the data with the given rowId
     *
     * @param rowId id of data to delete
     * @return true if deleted, false otherwise
     */
    public boolean deleteData(long rowId) {
        return mDb.delete(DB_DATA_TABLE, COL_ID + "=" + rowId, null) > 0;
    }

    /**
     * Delete all the data and recreate the table.
     */
    public void recreateDataTable() {
        mDb.execSQL("DROP TABLE IF EXISTS " + DB_DATA_TABLE);
        mDb.execSQL(DB_CREATE);
    }

    /**
     * Return a Cursor over the list of all notes in the database
     *
     * @return Cursor over all notes
     */
    public Cursor fetchAllData(String filter, String sortOrder) {
        if (mDb == null) {
            return null;
        }
        return mDb.query(DB_DATA_TABLE, new String[]{COL_ID, COL_DATE,
                        COL_DATEMOD, COL_COUNT, COL_TOTAL, COL_EDITED,
                        COL_COMMENT},
                filter, null, null, null, sortOrder);
    }

    /**
     * Return a Cursor positioned at the data that matches the given rowId
     *
     * @param rowId id of entry to retrieve
     * @return Cursor positioned to matching entry, if found
     * @throws SQLException if entry could not be found/retrieved
     */
    public Cursor fetchData(long rowId) throws SQLException {
        Cursor mCursor = mDb.query(true, DB_DATA_TABLE, new String[]{COL_ID,
                        COL_DATE, COL_DATEMOD, COL_COUNT, COL_TOTAL, COL_EDITED,
                        COL_COMMENT}, COL_ID + "=" + rowId, null, null, null,
                null,
                null);
        if (mCursor != null) {
            mCursor.moveToFirst();
        }
        return mCursor;
    }

    /**
     * Update the data using the details provided. The data to be updated is
     * specified using the rowId, and it is altered to use the values passed in
     *
     * @param rowId   The row ID.
     * @param date    The date.
     * @param dateMod The modification date.
     * @param edited  If edited.
     * @param comment The comment.
     * @return true if the entry was successfully updated, false otherwise
     */
    public boolean updateData(long rowId, long date, long dateMod, long count,
                              long total, boolean edited, String comment) {
        ContentValues values = new ContentValues();
        values.put(COL_DATE, date);
        values.put(COL_DATEMOD, dateMod);
        values.put(COL_COUNT, count);
        values.put(COL_TOTAL, total);
        values.put(COL_EDITED, edited);
        values.put(COL_COMMENT, comment);

        return mDb.update(DB_DATA_TABLE, values, COL_ID + "=" + rowId, null) > 0;
    }

    /**
     * Clears the working database, attaches the new one, copies all data,
     * detaches the old one.
     *
     * @param newFileName Path to the new database.
     * @param alias       Name for the new database or null to use "SourceDb"
     */
    public void replaceDatabase(String newFileName, String alias) {
        if (alias == null) alias = "TEMP_DB";
        // Clear the working database
        recreateDataTable();
        // Attach the new database
        mDb.execSQL("ATTACH DATABASE '" + newFileName
                + "' AS " + alias);
        // Copy the data
        mDb.execSQL("INSERT INTO " + DB_DATA_TABLE + " SELECT * FROM "
                + alias + "." + DB_DATA_TABLE);
        // Detach the new database
        mDb.execSQL("DETACH DATABASE " + alias);
    }

    /**
     * A SQLiteOpenHelper helper to help manage database creation and version
     * management. Extends a custom version that writes to the SD Card instead
     * of using the Context.
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {
        public DatabaseHelper(Context context, String dir) {
            super(context, dir, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(DB_CREATE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int
                newVersion) {
            // TODO Re-do this so nothing is lost if there is a need to change
            // the version
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS " + DB_DATA_TABLE);
            onCreate(db);
        }
    }

}
